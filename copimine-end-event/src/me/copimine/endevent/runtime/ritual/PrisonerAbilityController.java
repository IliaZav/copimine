package me.copimine.endevent.runtime.ritual;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative, generation-fenced cooldown and unlock state for the W6 prisoner. */
public final class PrisonerAbilityController {
    public static final long HEAL_COOLDOWN_MILLIS = 20_000L;
    public static final long BATTLE_SURGE_COOLDOWN_MILLIS = 30_000L;
    public static final long GUARDIAN_LINK_COOLDOWN_MILLIS = 30_000L;
    public static final long TURNCOAT_COOLDOWN_MILLIS = 55_000L;
    public static final long BATTLE_SURGE_DURATION_MILLIS = 7_500L;
    public static final long GUARDIAN_LINK_DURATION_MILLIS = 6_000L;
    public static final long TURNCOAT_DURATION_MILLIS = 22_500L;

    private long generation;
    private UUID prisoner;
    private boolean active;
    private final Map<Ability, Long> cooldowns = new EnumMap<>(Ability.class);

    public synchronized void start(long eventGeneration, UUID prisonerId) {
        if (eventGeneration <= 0L || prisonerId == null) {
            throw new IllegalArgumentException("active generation and prisoner are required");
        }
        if (generation != eventGeneration || !prisonerId.equals(prisoner)) {
            cooldowns.clear();
        }
        generation = eventGeneration;
        prisoner = prisonerId;
        active = true;
    }

    public synchronized void end(long eventGeneration) {
        if (eventGeneration != generation) {
            return;
        }
        // Keep this identity's deadlines in memory across disconnect/reconnect.
        // A different prisoner or generation starts a fresh session in start().
        active = false;
    }

    public synchronized Decision request(long eventGeneration,
                                         UUID sender,
                                         Ability ability,
                                         UUID target,
                                         int casterDeaths,
                                         long nowMillis,
                                         boolean targetAllowed) {
        Rejection rejection = validate(eventGeneration, sender, ability, target,
                casterDeaths, nowMillis, targetAllowed);
        if (rejection != Rejection.NONE) {
            return new Decision(false, rejection, ability, target,
                    ability == null ? 0L : cooldownUntil(ability));
        }
        long until = nowMillis + ability.cooldownMillis();
        cooldowns.put(ability, until);
        return new Decision(true, Rejection.NONE, ability, target, until);
    }

    public synchronized HudState state(Ability ability,
                                       int casterDeaths,
                                       long nowMillis,
                                       boolean hasValidTarget) {
        if (!active || ability == null || casterDeaths < 0 || casterDeaths > 5 || nowMillis < 0L) {
            return HudState.LOCKED;
        }
        if (!isUnlocked(casterDeaths, ability)) {
            return HudState.LOCKED;
        }
        if (cooldownUntil(ability) > nowMillis) {
            return HudState.COOLDOWN;
        }
        return hasValidTarget ? HudState.READY : HudState.NO_TARGET;
    }

    public synchronized long cooldownUntil(Ability ability) {
        return !active || ability == null ? 0L : cooldowns.getOrDefault(ability, 0L);
    }

    public synchronized Snapshot snapshot(int casterDeaths) {
        return new Snapshot(active ? generation : 0L, active ? prisoner : null, boundedDeaths(casterDeaths),
                cooldownUntil(Ability.HEAL), cooldownUntil(Ability.BATTLE_SURGE),
                cooldownUntil(Ability.GUARDIAN_LINK), cooldownUntil(Ability.TURNCOAT));
    }

    private Rejection validate(long eventGeneration,
                               UUID sender,
                               Ability ability,
                               UUID target,
                               int casterDeaths,
                               long nowMillis,
                               boolean targetAllowed) {
        if (!active || generation <= 0L || prisoner == null) {
            return Rejection.INACTIVE_SESSION;
        }
        if (eventGeneration != generation) {
            return Rejection.STALE_GENERATION;
        }
        if (sender == null || !prisoner.equals(sender)) {
            return Rejection.NOT_PRISONER;
        }
        if (ability == null || casterDeaths < 0 || casterDeaths > 5) {
            return Rejection.INVALID_REQUEST;
        }
        if (!isUnlocked(casterDeaths, ability)) {
            return Rejection.LOCKED;
        }
        if (nowMillis < 0L || cooldownUntil(ability) > nowMillis) {
            return Rejection.COOLDOWN;
        }
        if (target == null || !targetAllowed) {
            return Rejection.NO_TARGET;
        }
        return Rejection.NONE;
    }

    private static boolean isUnlocked(int casterDeaths, Ability ability) {
        return casterDeaths >= ability.unlockDeathCount();
    }

    private static int boundedDeaths(int value) {
        return Math.max(0, Math.min(5, value));
    }

    public enum Ability {
        HEAL(1, HEAL_COOLDOWN_MILLIS),
        BATTLE_SURGE(2, BATTLE_SURGE_COOLDOWN_MILLIS),
        GUARDIAN_LINK(3, GUARDIAN_LINK_COOLDOWN_MILLIS),
        TURNCOAT(4, TURNCOAT_COOLDOWN_MILLIS);

        private final int unlockDeathCount;
        private final long cooldownMillis;

        Ability(int unlockDeathCount, long cooldownMillis) {
            this.unlockDeathCount = unlockDeathCount;
            this.cooldownMillis = cooldownMillis;
        }

        public int unlockDeathCount() {
            return unlockDeathCount;
        }

        public long cooldownMillis() {
            return cooldownMillis;
        }
    }

    public enum HudState {
        LOCKED,
        READY,
        COOLDOWN,
        NO_TARGET
    }

    public enum Rejection {
        NONE,
        INACTIVE_SESSION,
        STALE_GENERATION,
        NOT_PRISONER,
        INVALID_REQUEST,
        LOCKED,
        COOLDOWN,
        NO_TARGET
    }

    public record Decision(boolean accepted,
                           Rejection rejection,
                           Ability ability,
                           UUID target,
                           long cooldownUntilMillis) {
    }

    public record Snapshot(long generation,
                           UUID prisoner,
                           int casterDeaths,
                           long healCooldownUntilMillis,
                           long battleSurgeCooldownUntilMillis,
                           long guardianLinkCooldownUntilMillis,
                           long turncoatCooldownUntilMillis) {
        public long cooldownUntil(Ability ability) {
            if (ability == null) {
                return 0L;
            }
            return switch (ability) {
                case HEAL -> healCooldownUntilMillis;
                case BATTLE_SURGE -> battleSurgeCooldownUntilMillis;
                case GUARDIAN_LINK -> guardianLinkCooldownUntilMillis;
                case TURNCOAT -> turncoatCooldownUntilMillis;
            };
        }
    }
}
