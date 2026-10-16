package me.copimine.client;

import java.util.UUID;
import me.copimine.protocol.EncounterGenerationPolicy;

/** Main-thread client mirror of the server-owned W6 prisoner HUD state. */
public final class PrisonerHudController {
    static final long MAX_GENERATION = EncounterGenerationPolicy.MAX_SUPPORTED_GENERATION;

    private String eventId = "";
    private long generation;
    private boolean terminal;
    private UUID prisoner;
    private int casterDeaths;
    private final long[] cooldownUntilMillis = new long[4];
    private PrisonerTargetEligibility targetEligibility = PrisonerTargetEligibility.empty();

    public synchronized boolean applyState(String incomingEventId,
                                           long incomingGeneration,
                                           UUID incomingPrisoner,
                                           int incomingCasterDeaths,
                                           long[] remainingCooldownsMillis,
                                           PrisonerTargetEligibility incomingTargetEligibility,
                                           long receivedAtMillis) {
        return applyState(incomingEventId, incomingGeneration, incomingPrisoner, incomingCasterDeaths,
                remainingCooldownsMillis, incomingTargetEligibility, receivedAtMillis, receivedAtMillis);
    }

    public synchronized boolean applyState(String incomingEventId, long incomingGeneration,
                                           UUID incomingPrisoner, int incomingCasterDeaths,
                                           long[] remainingCooldownsMillis,
                                           PrisonerTargetEligibility incomingTargetEligibility,
                                           long receivedAtMillis, long serverTimestamp) {
        if (incomingEventId == null || incomingEventId.isBlank() || incomingEventId.length() > 128
                || !EncounterGenerationPolicy.isActiveGeneration(incomingGeneration)
                || incomingPrisoner == null
                || incomingCasterDeaths < 0 || incomingCasterDeaths > 5
                || remainingCooldownsMillis == null || remainingCooldownsMillis.length != 4
                || incomingTargetEligibility == null
                || receivedAtMillis < 0L || serverTimestamp < 0L) {
            return false;
        }
        for (long remaining : remainingCooldownsMillis) {
            if (remaining < 0L || remaining > 60_000L) {
                return false;
            }
        }
        if (!eventId.isBlank() && incomingGeneration < generation) {
            return false;
        }
        if (!eventId.isBlank() && incomingGeneration == generation
                && !eventId.equals(incomingEventId)) {
            return false;
        }
        if (terminal && incomingGeneration == generation) {
            return false;
        }
        // End-Event packets are applied in connection order. Do not use wall-clock time
        // as an ordering fence: the client clock can move backward or repeat a millisecond.
        eventId = incomingEventId;
        generation = incomingGeneration;
        terminal = false;
        prisoner = incomingPrisoner;
        casterDeaths = incomingCasterDeaths;
        targetEligibility = incomingTargetEligibility;
        for (int index = 0; index < cooldownUntilMillis.length; index++) {
            cooldownUntilMillis[index] = receivedAtMillis + remainingCooldownsMillis[index];
        }
        return true;
    }

    public synchronized boolean clear(String incomingEventId, long incomingGeneration) {
        if (!EncounterGenerationPolicy.isActiveGeneration(incomingGeneration) || eventId.isBlank()
                || !eventId.equals(incomingEventId) || incomingGeneration != generation) {
            return false;
        }
        // Retain the terminated identity until a new generation or connection.
        // Late semantic state from this prison must not restore input or HUD.
        terminal = true;
        clearPresentation();
        return true;
    }

    /** Only a new server-owned prisoner session can reopen this terminated identity. */
    public synchronized boolean resumeSession(String expectedEventId, long expectedGeneration) {
        if (!terminal || !EncounterGenerationPolicy.isActiveGeneration(expectedGeneration)
                || !eventId.equals(expectedEventId) || expectedGeneration != generation) return false;
        terminal = false;
        return true;
    }

    public synchronized boolean activeFor(UUID playerId) {
        return playerId != null && prisoner != null && prisoner.equals(playerId)
                && !eventId.isBlank() && generation > 0L;
    }

    public synchronized AbilityState state(Ability ability,
                                           UUID playerId,
                                           long nowMillis,
                                           UUID targetId) {
        if (ability == null || !activeFor(playerId) || nowMillis < 0L) {
            return new AbilityState(HudState.LOCKED, 0L);
        }
        if (casterDeaths < ability.unlockDeathCount()) {
            return new AbilityState(HudState.LOCKED, 0L);
        }
        long remaining = Math.max(0L, cooldownUntilMillis[ability.ordinal()] - nowMillis);
        if (remaining > 0L) {
            return new AbilityState(HudState.COOLDOWN, remaining);
        }
        return new AbilityState(isTargetAllowed(ability, targetId)
                ? HudState.READY : HudState.NO_TARGET, 0L);
    }

    public synchronized String eventId() {
        return eventId;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized UUID prisoner() {
        return prisoner;
    }

    public synchronized int casterDeaths() {
        return casterDeaths;
    }

    public synchronized long cooldownUntilMillis(Ability ability) {
        return ability == null ? 0L : cooldownUntilMillis[ability.ordinal()];
    }

    public synchronized boolean isTargetAllowed(Ability ability, UUID targetId) {
        return targetEligibility.allows(ability, targetId);
    }

    public synchronized void clear() {
        // Death/world change ends this prison within the same connection.
        terminal = true;
        clearPresentation();
    }

    public synchronized void reset() {
        eventId = "";
        generation = 0L;
        terminal = false;
        clearPresentation();
    }

    private void clearPresentation() {
        prisoner = null;
        casterDeaths = 0;
        targetEligibility = PrisonerTargetEligibility.empty();
        java.util.Arrays.fill(cooldownUntilMillis, 0L);
    }

    public enum Ability {
        HEAL(1, "Q"),
        BATTLE_SURGE(2, "W"),
        GUARDIAN_LINK(3, "E"),
        TURNCOAT(4, "R");

        private final int unlockDeathCount;
        private final String keyLabel;

        Ability(int unlockDeathCount, String keyLabel) {
            this.unlockDeathCount = unlockDeathCount;
            this.keyLabel = keyLabel;
        }

        public int unlockDeathCount() {
            return unlockDeathCount;
        }

        public String keyLabel() {
            return keyLabel;
        }
    }

    public enum HudState {
        LOCKED,
        READY,
        COOLDOWN,
        NO_TARGET
    }

    public record AbilityState(HudState state, long remainingMillis) {
        public AbilityState {
            if (state == null || remainingMillis < 0L) {
                throw new IllegalArgumentException("HUD state and nonnegative cooldown are required");
            }
        }
    }
}
