package me.copimine.endevent.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Server-side state contract for the Wave 5 rings/prisoner encounter.
 * Entities, displays and the ice journal stay in the Bukkit adapter; this
 * policy makes the shield, reconstruction window and prisoner drain
 * deterministic and generation-scoped.
 */
public final class Wave5EncounterPolicy {
    public static final int GUARD_COUNT = 3;
    public static final int ELITE_COUNT = 1;
    public static final long RECONSTRUCTION_WINDOW_TICKS = 200L;
    public static final long PRISONER_DRAIN_INTERVAL_TICKS = 1_000L;
    public static final double PRISONER_DRAIN_HP = 3.0D;
    public static final double PRISONER_MIN_HP = 1.0D;
    public static final double GUARD_HP_MULTIPLIER = 0.60D;
    public static final double ELITE_HP_MULTIPLIER = 0.72D;
    public static final int MAX_PRISONER_STRENGTH_STACKS = 3;

    private Wave5EncounterPolicy() {
    }

    public static State initial(long generation) {
        requireGeneration(generation);
        return new State(generation, Phase.RING_ONE_PACK, Set.of(), false,
                false, 20.0D, 0L, 0L, 0);
    }

    public static State beginRingTwo(State state, long generation, long nowTick) {
        if (!valid(state, generation) || state.phase() != Phase.RING_ONE_PACK) {
            return state;
        }
        return state.withPhase(Phase.RING_TWO_GUARDS, safeTick(nowTick));
    }

    public static boolean reconstructionComplete(State state, long generation, long nowTick) {
        return valid(state, generation)
                && state.phase() == Phase.RING_TWO_GUARDS
                && safeTick(nowTick) >= state.phaseStartedTick() + RECONSTRUCTION_WINDOW_TICKS;
    }

    public static State beginRingThree(State state, long generation, long nowTick) {
        if (!reconstructionComplete(state, generation, nowTick)) {
            return state;
        }
        return state.withPhase(Phase.RING_THREE_PRISONER, safeTick(nowTick));
    }

    public static State beginFinal(State state, long generation, long nowTick) {
        if (!valid(state, generation)
                || (state.phase() != Phase.RING_THREE_PRISONER
                && state.phase() != Phase.RING_TWO_GUARDS)) {
            return state;
        }
        return new State(state.generation(), Phase.FINAL_GUARDS, Set.of(), true,
                state.prisonerReleased(), state.prisonerHealth(), state.lastPrisonerDrainTick(),
                safeTick(nowTick), state.prisonerStrengthStacks());
    }

    public static State guardDefeated(State state, long generation, int guardIndex) {
        if (!valid(state, generation) || guardIndex < 0 || guardIndex >= GUARD_COUNT
                || state.phase() != Phase.FINAL_GUARDS
                || state.defeatedGuards().contains(guardIndex)) {
            return state;
        }
        Set<Integer> defeated = new LinkedHashSet<>(state.defeatedGuards());
        defeated.add(guardIndex);
        Phase next = defeated.size() == GUARD_COUNT
                ? Phase.ELITE_VULNERABLE : Phase.FINAL_GUARDS;
        return new State(state.generation(), next, defeated, state.prisonerLocked(),
                state.prisonerReleased(), state.prisonerHealth(), state.lastPrisonerDrainTick(),
                state.phaseStartedTick(), state.prisonerStrengthStacks());
    }

    public static State eliteDefeated(State state, long generation) {
        if (!valid(state, generation) || state.phase() != Phase.ELITE_VULNERABLE) {
            return state;
        }
        return new State(state.generation(), Phase.COMPLETE, state.defeatedGuards(), false,
                true, state.prisonerHealth(), state.lastPrisonerDrainTick(), state.phaseStartedTick(),
                state.prisonerStrengthStacks());
    }

    public static DrainResult drainPrisoner(State state, long generation, long nowTick) {
        long tick = safeTick(nowTick);
        if (!valid(state, generation)
                || state.phase() == Phase.COMPLETE
                || !state.prisonerLocked()
                || state.prisonerReleased()
                || tick < Math.max(
                state.lastPrisonerDrainTick() + PRISONER_DRAIN_INTERVAL_TICKS,
                state.phaseStartedTick() + PRISONER_DRAIN_INTERVAL_TICKS)) {
            return new DrainResult(state, false, 0.0D);
        }
        double before = state.prisonerHealth();
        double after = Math.max(PRISONER_MIN_HP, before - PRISONER_DRAIN_HP);
        if (after >= before) {
            return new DrainResult(state, false, 0.0D);
        }
        int stacks = Math.min(MAX_PRISONER_STRENGTH_STACKS,
                state.prisonerStrengthStacks() + 1);
        return new DrainResult(new State(state.generation(), state.phase(), state.defeatedGuards(),
                state.prisonerLocked(), state.prisonerReleased(), after, tick,
                state.phaseStartedTick(), stacks), true, before - after);
    }

    public static State prisonerReleased(State state, long generation) {
        if (!valid(state, generation) || state.prisonerReleased()) {
            return state;
        }
        return new State(state.generation(), state.phase(), state.defeatedGuards(), false,
                true, state.prisonerHealth(), state.lastPrisonerDrainTick(), state.phaseStartedTick(),
                state.prisonerStrengthStacks());
    }

    public static boolean guardShieldActive(State state) {
        return state != null && state.phase() == Phase.FINAL_GUARDS;
    }

    public static boolean eliteCanTakeDamage(State state) {
        return state != null && state.phase() == Phase.ELITE_VULNERABLE;
    }

    public static boolean isComplete(State state) {
        return state != null && state.phase() == Phase.COMPLETE;
    }

    /** Apply the bounded W5 guard/elite health profiles to the entity base HP. */
    public static double guardHealth(double baseHealth) {
        return scaledHealth(baseHealth, GUARD_HP_MULTIPLIER);
    }

    public static double eliteHealth(double baseHealth) {
        return scaledHealth(baseHealth, ELITE_HP_MULTIPLIER);
    }

    /** For a duo, only one guard attacks at a time and rotates every 10 sec. */
    public static int attackingGuardIndex(int players, long nowTick) {
        if (players < 1 || players > 2) {
            return -1;
        }
        return (int) Math.floorMod(safeTick(nowTick) / RECONSTRUCTION_WINDOW_TICKS, GUARD_COUNT);
    }

    private static boolean valid(State state, long generation) {
        return state != null && generation > 0L && state.generation() == generation;
    }

    private static long safeTick(long tick) {
        return Math.max(0L, tick);
    }

    private static double scaledHealth(double baseHealth, double multiplier) {
        if (!Double.isFinite(baseHealth) || baseHealth <= 0.0D
                || !Double.isFinite(multiplier) || multiplier <= 0.0D) {
            return 1.0D;
        }
        return Math.max(1.0D, baseHealth * multiplier);
    }

    private static void requireGeneration(long generation) {
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
    }

    public enum Phase {
        RING_ONE_PACK,
        RING_TWO_GUARDS,
        RING_THREE_PRISONER,
        FINAL_GUARDS,
        ELITE_VULNERABLE,
        COMPLETE
    }

    public record State(long generation, Phase phase, Set<Integer> defeatedGuards,
                        boolean prisonerLocked, boolean prisonerReleased,
                        double prisonerHealth, long lastPrisonerDrainTick,
                        long phaseStartedTick, int prisonerStrengthStacks) {
        public State {
            requireGeneration(generation);
            phase = phase == null ? Phase.RING_ONE_PACK : phase;
            defeatedGuards = Set.copyOf(defeatedGuards == null ? Set.of() : defeatedGuards);
            if (defeatedGuards.stream().anyMatch(index -> index == null
                    || index < 0 || index >= GUARD_COUNT)) {
                throw new IllegalArgumentException("invalid defeated guard index");
            }
            if (!Double.isFinite(prisonerHealth)
                    || prisonerHealth < PRISONER_MIN_HP) {
                throw new IllegalArgumentException("prisoner health is below its floor");
            }
            if (lastPrisonerDrainTick < 0L || phaseStartedTick < 0L) {
                throw new IllegalArgumentException("encounter ticks cannot be negative");
            }
            if (prisonerStrengthStacks < 0
                    || prisonerStrengthStacks > MAX_PRISONER_STRENGTH_STACKS) {
                throw new IllegalArgumentException("prisoner strength stacks are out of bounds");
            }
        }

        private State withPhase(Phase next, long startedTick) {
            return new State(generation, next, defeatedGuards, prisonerLocked,
                    prisonerReleased, prisonerHealth, lastPrisonerDrainTick, startedTick,
                    prisonerStrengthStacks);
        }
    }

    public record DrainResult(State state, boolean drained, double amount) {
        public DrainResult {
            if (state == null || !Double.isFinite(amount) || amount < 0.0D) {
                throw new IllegalArgumentException("invalid drain result");
            }
        }
    }
}
