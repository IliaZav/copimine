package me.copimine.endevent.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure bounded schedule and landing-position lock for the Wave 6 Anvil spell. */
public final class FallingAnvilVolleyPolicy {
    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 20;
    public static final int SECOND_VOLLEY_THRESHOLD = 11;
    public static final int MAX_VISUALS_PER_HIGH_ROSTER_VOLLEY = 25;
    public static final int MAX_VISUALS_PER_LOWER_ROSTER_VOLLEY = 90;
    public static final long WARNING_TICKS = 60L;
    public static final long POST_IMPACT_TICKS = 20L;

    public enum Phase {
        IDLE,
        WARNING,
        IMPACT,
        INTERMISSION,
        COMPLETE
    }

    /** Locked landing position; the adapter must not recompute it from moving players. */
    public record LandingPoint(double x, double y, double z) {
        public LandingPoint {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("finite Anvil landing point required");
            }
        }
    }

    public record State(long generation,
                        Phase phase,
                        int participants,
                        int eligibleTargetCount,
                        int volleyIndex,
                        int totalVolleys,
                        List<LandingPoint> landingPositions,
                        long deadlineTick) {
        public State {
            if (phase == null || landingPositions == null) {
                throw new IllegalArgumentException("Falling Anvil phase and locked positions required");
            }
            landingPositions = List.copyOf(landingPositions);
            if (phase == Phase.IDLE) {
                if (participants != 0 || eligibleTargetCount != 0 || volleyIndex != 0
                        || totalVolleys != 0 || !landingPositions.isEmpty() || deadlineTick != 0L) {
                    throw new IllegalArgumentException("idle Anvil state cannot retain a volley");
                }
            } else {
                if (participants < MIN_PARTICIPANTS || participants > MAX_PARTICIPANTS
                        || eligibleTargetCount < 1 || eligibleTargetCount > participants
                        || volleyIndex < 1 || volleyIndex > totalVolleys
                        || totalVolleys != volleyCountForPlayers(participants)
                        || deadlineTick < 0L) {
                    throw new IllegalArgumentException("invalid bounded Falling Anvil state");
                }
                boolean visualsVisible = phase == Phase.WARNING || phase == Phase.IMPACT;
                int expectedVisuals = visualsFor(participants, eligibleTargetCount);
                if (visualsVisible && landingPositions.size() != expectedVisuals
                        || !visualsVisible && !landingPositions.isEmpty()) {
                    throw new IllegalArgumentException("Anvil visuals must match the locked roster matrix");
                }
                if (landingPositions.size() > maxVisualsForParticipants(participants)
                        || hasDuplicatePositions(landingPositions)
                        || participants >= SECOND_VOLLEY_THRESHOLD
                        && visualsVisible && !isGrid(landingPositions, 5)) {
                    throw new IllegalArgumentException("Anvil visual grid exceeds or violates its bounded layout");
                }
                if (participants >= 4 && participants < SECOND_VOLLEY_THRESHOLD
                        && visualsVisible && !isGroupedGrid(landingPositions, 3)) {
                    throw new IllegalArgumentException("lower-roster Anvils must form 3x3 groups");
                }
            }
        }

        public int activeVisuals() {
            return landingPositions.size();
        }
    }

    public record StartResult(boolean accepted, State state) { }

    private FallingAnvilVolleyPolicy() { }

    public static State idle(long generation) {
        return new State(generation, Phase.IDLE, 0, 0, 0, 0, List.of(), 0L);
    }

    /** The 10/11 boundary opens volley two; all roster counts are total participants. */
    public static int volleyCountForPlayers(int participants) {
        requireParticipants(participants);
        return participants < SECOND_VOLLEY_THRESHOLD ? 1 : 2;
    }

    /** Master-task matrix: one per target, nine per target, then a single 5x5 cluster. */
    public static int visualsFor(int participants, int eligibleTargetCount) {
        requireParticipants(participants);
        if (eligibleTargetCount < 1 || eligibleTargetCount > participants) {
            throw new IllegalArgumentException("eligible Wave 6 targets outside roster");
        }
        if (participants <= 3) return eligibleTargetCount;
        if (participants < SECOND_VOLLEY_THRESHOLD) return eligibleTargetCount * 9;
        return MAX_VISUALS_PER_HIGH_ROSTER_VOLLEY;
    }

    public static int maxVisualsForParticipants(int participants) {
        requireParticipants(participants);
        if (participants <= 3) return participants;
        if (participants < SECOND_VOLLEY_THRESHOLD) return participants * 9;
        return MAX_VISUALS_PER_HIGH_ROSTER_VOLLEY;
    }

    public static StartResult start(State current,
                                    long eventGeneration,
                                    int participants,
                                    int eligibleTargetCount,
                                    List<LandingPoint> lockedPositions,
                                    long nowTick,
                                    boolean waveActive,
                                    boolean sharedSpellSlotFree) {
        State state = current == null ? idle(eventGeneration) : current;
        if (state.generation() != eventGeneration
                || state.phase() != Phase.IDLE && state.phase() != Phase.COMPLETE
                || !validRosterAndGrid(participants, eligibleTargetCount, lockedPositions)
                || nowTick < 0L
                || !waveActive
                || !sharedSpellSlotFree) {
            return new StartResult(false, state);
        }
        long deadline;
        try {
            deadline = Math.addExact(nowTick, WARNING_TICKS);
        } catch (ArithmeticException overflow) {
            return new StartResult(false, state);
        }
        return new StartResult(true, new State(eventGeneration, Phase.WARNING,
                participants, eligibleTargetCount, 1, volleyCountForPlayers(participants),
                lockedPositions, deadline));
    }

    /** Warning expiry requests one impact; the adapter confirms the actual impact separately. */
    public static State tick(State state, long eventGeneration, long nowTick) {
        if (state == null || state.generation() != eventGeneration || nowTick < 0L
                || nowTick < state.deadlineTick()) {
            return state;
        }
        if (state.phase() == Phase.WARNING) {
            return new State(eventGeneration, Phase.IMPACT, state.participants(),
                    state.eligibleTargetCount(), state.volleyIndex(), state.totalVolleys(),
                    state.landingPositions(), nowTick);
        }
        return state;
    }

    /**
     * Lock a newly selected cluster and begin volley two exactly when the post-impact wait ends.
     * Selection and geometry remain caller-owned; this policy validates and freezes the points.
     */
    public static StartResult beginNextVolley(State state,
                                              long eventGeneration,
                                              int eligibleTargetCount,
                                              List<LandingPoint> lockedPositions,
                                              long nowTick) {
        if (state == null || state.generation() != eventGeneration
                || state.phase() != Phase.INTERMISSION
                || state.volleyIndex() >= state.totalVolleys()
                || nowTick < state.deadlineTick()
                || !validRosterAndGrid(state.participants(), eligibleTargetCount, lockedPositions)
                || nowTick < 0L) {
            return new StartResult(false, state);
        }
        long warningDeadline;
        try {
            warningDeadline = Math.addExact(nowTick, WARNING_TICKS);
        } catch (ArithmeticException overflow) {
            return new StartResult(false, state);
        }
        return new StartResult(true, new State(eventGeneration, Phase.WARNING,
                state.participants(), eligibleTargetCount, state.volleyIndex() + 1,
                state.totalVolleys(), lockedPositions, warningDeadline));
    }

    /** Begin the one-second wait only after the adapter confirms the impact occurred. */
    public static State impactResolved(State state, long eventGeneration, long impactTick) {
        if (state == null || state.generation() != eventGeneration
                || state.phase() != Phase.IMPACT || impactTick < state.deadlineTick()) {
            return state;
        }
        if (state.volleyIndex() >= state.totalVolleys()) {
            return new State(eventGeneration, Phase.COMPLETE, state.participants(),
                    state.eligibleTargetCount(), state.volleyIndex(), state.totalVolleys(),
                    List.of(), impactTick);
        }
        long intermissionDeadline;
        try {
            intermissionDeadline = Math.addExact(impactTick, POST_IMPACT_TICKS);
        } catch (ArithmeticException overflow) {
            return state;
        }
        return new State(eventGeneration, Phase.INTERMISSION, state.participants(),
                state.eligibleTargetCount(), state.volleyIndex(), state.totalVolleys(),
                List.of(), intermissionDeadline);
    }

    public static State clear(State state, long eventGeneration) {
        if (state == null || state.generation() != eventGeneration) {
            return state;
        }
        return idle(eventGeneration);
    }

    private static boolean validRosterAndGrid(int participants, int eligibleTargetCount,
                                               List<LandingPoint> positions) {
        try {
            int expectedCount = visualsFor(participants, eligibleTargetCount);
            if (positions == null || positions.size() != expectedCount
                    || positions.size() > maxVisualsForParticipants(participants)
                    || hasDuplicatePositions(positions)) {
                return false;
            }
            if (participants >= SECOND_VOLLEY_THRESHOLD) {
                return isGrid(positions, 5);
            }
            if (participants >= 4) {
                return isGroupedGrid(positions, 3);
            }
            return positions.stream().allMatch(java.util.Objects::nonNull);
        } catch (IllegalArgumentException | NullPointerException invalidInput) {
            return false;
        }
    }

    private static boolean isGroupedGrid(List<LandingPoint> positions, int side) {
        if (positions == null || positions.size() % (side * side) != 0) return false;
        for (int start = 0; start < positions.size(); start += side * side) {
            if (!isGrid(positions.subList(start, start + side * side), side)) return false;
        }
        return true;
    }

    private static boolean isGrid(List<LandingPoint> positions, int side) {
        if (positions == null || positions.size() != side * side) return false;
        Set<Double> xs = new HashSet<>();
        Set<Double> zs = new HashSet<>();
        Set<String> cells = new HashSet<>();
        for (LandingPoint point : positions) {
            if (point == null) return false;
            xs.add(point.x());
            zs.add(point.z());
            cells.add(Double.doubleToLongBits(point.x()) + ":" + Double.doubleToLongBits(point.z()));
        }
        return xs.size() == side && zs.size() == side && cells.size() == side * side;
    }

    private static boolean hasDuplicatePositions(List<LandingPoint> positions) {
        if (positions == null) return true;
        Set<LandingPoint> distinct = new HashSet<>();
        for (LandingPoint position : positions) {
            if (position == null || !distinct.add(position)) return true;
        }
        return false;
    }

    private static void requireParticipants(int participants) {
        if (participants < MIN_PARTICIPANTS || participants > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("Wave 6 participants outside 2–20: " + participants);
        }
    }
}
