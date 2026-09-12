package me.copimine.endevent.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-authoritative paired-guard controller for COLLAPSE_RINGS.
 *
 * <p>The first death opens a 200-tick coordination window. Killing the
 * partner on the deadline is still valid; after the deadline the first guard
 * is rebuilt at a bounded fraction of its health and the pair starts again.
 * The timer is anchored to the actual first death, not server uptime or an
 * earlier visual transition.</p>
 */
public final class CollapseRingEncounterPolicy {
    public static final int MAX_ROOMS = 4;
    public static final long PAIR_TIMER_TICKS = 200L;
    public static final double REVIVE_HEALTH_FRACTION = 0.35D;

    private CollapseRingEncounterPolicy() {
    }

    public static State initial(long generation,
                                int roomId,
                                UUID guardA,
                                UUID guardB,
                                long rotationStartedTick) {
        requireGeneration(generation);
        requireRoom(roomId);
        requirePair(guardA, guardB);
        return new State(generation, roomId, guardA, guardB, null, -1L, -1L,
                REVIVE_HEALTH_FRACTION, Phase.BOTH_ALIVE, safeTick(rotationStartedTick));
    }

    /** Apply one idempotent guard-death callback. */
    public static State guardDown(State state, long generation, UUID guardId, long nowTick) {
        if (!valid(state, generation) || guardId == null || !isGuard(state, guardId)
                || state.phase() == Phase.PAIR_DEFEATED) {
            return state;
        }
        long now = safeTick(nowTick);
        if (state.phase() == Phase.BOTH_ALIVE) {
            return state.firstDown(guardId, now, now + PAIR_TIMER_TICKS);
        }
        if (state.phase() != Phase.FIRST_DOWN
                || Objects.equals(state.firstDownGuardId(), guardId)) {
            return state;
        }
        // Inclusive boundary: the partner may die exactly on the deadline.
        if (now <= state.killWindowDeadlineTick()) {
            return state.withPhase(Phase.PAIR_DEFEATED, state.firstDownGuardId(),
                    state.firstDownTick(), state.killWindowDeadlineTick());
        }
        // A late callback cannot turn a timed-out attempt into a success.
        return timeout(state, generation);
    }

    /** Rebuild the first guard after the inclusive kill window expires. */
    public static State tick(State state, long generation, long nowTick) {
        if (!valid(state, generation) || state.phase() != Phase.FIRST_DOWN) {
            return state;
        }
        return safeTick(nowTick) > state.killWindowDeadlineTick()
                ? timeout(state, generation) : state;
    }

    public static boolean timerActive(State state, long generation, long nowTick) {
        return valid(state, generation) && state.phase() == Phase.FIRST_DOWN
                && safeTick(nowTick) <= state.killWindowDeadlineTick();
    }

    public static boolean pairDefeated(State state, long generation) {
        return valid(state, generation) && state.phase() == Phase.PAIR_DEFEATED;
    }

    public static boolean isComplete(State state) {
        return state != null && state.phase() == Phase.PAIR_DEFEATED;
    }

    /**
     * Stable duo rotation based on elapsed time since this room began. A
     * player entering later does not inherit an arbitrary server-uptime phase.
     */
    public static int attackingGuardIndex(int playerCount,
                                          long rotationStartedTick,
                                          long nowTick) {
        if (playerCount < 1) {
            return -1;
        }
        long elapsed = Math.max(0L, safeTick(nowTick) - safeTick(rotationStartedTick));
        return (int) Math.floorMod(elapsed / PAIR_TIMER_TICKS, 2L);
    }

    public static boolean guardMayAttack(State state,
                                         int playerCount,
                                         UUID guardId,
                                         long nowTick) {
        if (state == null || !valid(state, state.generation()) || guardId == null
                || !isGuard(state, guardId) || state.phase() == Phase.PAIR_DEFEATED) {
            return false;
        }
        if (playerCount > 2) {
            return true;
        }
        int slot = Objects.equals(state.guardA(), guardId) ? 0 : 1;
        return slot == attackingGuardIndex(playerCount, state.rotationStartedTick(), nowTick);
    }

    private static State timeout(State state, long generation) {
        return new State(generation, state.roomId(), state.guardA(), state.guardB(), null,
                -1L, -1L, state.reviveHealthFraction(), Phase.BOTH_ALIVE,
                state.rotationStartedTick());
    }

    private static boolean valid(State state, long generation) {
        return state != null && generation > 0L && state.generation() == generation;
    }

    private static boolean isGuard(State state, UUID guardId) {
        return Objects.equals(state.guardA(), guardId) || Objects.equals(state.guardB(), guardId);
    }

    private static long safeTick(long tick) {
        return Math.max(0L, tick);
    }

    private static void requireGeneration(long generation) {
        if (generation <= 0L) {
            throw new IllegalArgumentException("generation must be positive");
        }
    }

    private static void requireRoom(int roomId) {
        if (roomId < 0 || roomId >= MAX_ROOMS) {
            throw new IllegalArgumentException("room id is outside the encounter");
        }
    }

    private static void requirePair(UUID guardA, UUID guardB) {
        if (guardA == null || guardB == null || guardA.equals(guardB)) {
            throw new IllegalArgumentException("two distinct guard ids are required");
        }
    }

    public enum Phase {
        BOTH_ALIVE,
        FIRST_DOWN,
        PAIR_DEFEATED
    }

    public record State(long generation,
                        int roomId,
                        UUID guardA,
                        UUID guardB,
                        UUID firstDownGuardId,
                        long firstDownTick,
                        long killWindowDeadlineTick,
                        double reviveHealthFraction,
                        Phase phase,
                        long rotationStartedTick) {
        public State {
            requireGeneration(generation);
            requireRoom(roomId);
            requirePair(guardA, guardB);
            if (firstDownGuardId != null && !firstDownGuardId.equals(guardA)
                    && !firstDownGuardId.equals(guardB)) {
                throw new IllegalArgumentException("first down guard is not part of the pair");
            }
            if (firstDownTick < -1L || killWindowDeadlineTick < -1L
                    || rotationStartedTick < 0L) {
                throw new IllegalArgumentException("encounter ticks are invalid");
            }
            if (!Double.isFinite(reviveHealthFraction)
                    || reviveHealthFraction <= 0.0D || reviveHealthFraction > 1.0D) {
                throw new IllegalArgumentException("revive health fraction is invalid");
            }
            phase = phase == null ? Phase.BOTH_ALIVE : phase;
            if (phase == Phase.FIRST_DOWN
                    && (firstDownGuardId == null || firstDownTick < 0L
                    || killWindowDeadlineTick < firstDownTick)) {
                throw new IllegalArgumentException("FIRST_DOWN requires a live timer");
            }
            if (phase == Phase.BOTH_ALIVE
                    && (firstDownGuardId != null || firstDownTick != -1L
                    || killWindowDeadlineTick != -1L)) {
                throw new IllegalArgumentException("BOTH_ALIVE cannot retain a down timer");
            }
        }

        private State firstDown(UUID guardId, long downTick, long deadline) {
            return new State(generation, roomId, guardA, guardB, guardId, downTick,
                    deadline, reviveHealthFraction, Phase.FIRST_DOWN, rotationStartedTick);
        }

        private State withPhase(Phase next, UUID firstDownId, long downTick, long deadline) {
            return new State(generation, roomId, guardA, guardB, firstDownId, downTick,
                    deadline, reviveHealthFraction, next, rotationStartedTick);
        }
    }
}
