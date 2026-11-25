package me.copimine.dungeon.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure core of the bounded room wave scheduler (spec OV4.10
 * {@code DungeonRoomController}; verification R01/R04).
 *
 * <p>One authoritative room generation owns the actor schedule. Scaling is
 * deterministic and bounded independently of the room's simultaneous live cap, so any number of eligible
 * players may participate without ever spawning an unbounded wave, and a
 * simultaneous or duplicated join recomputes exactly the same plan instead of
 * standing up a second controller generation. End Rift's own seven-wave owner
 * stays separate and is not modelled here.</p>
 */
public final class RoomWaveSchedulePolicy {

    /** Absolute ceiling on simultaneously live enemies for one room generation. */
    public static final int MAX_LIVE_CAP = 128;

    private RoomWaveSchedulePolicy() {
    }

    /** One scheduled wave: when it starts and how many real enemies it fields. */
    public record WavePlan(int index, int delayTicks, int startTick, int enemyCount, boolean clamped) {
    }

    /** The whole bounded, deterministic room schedule. */
    public record Plan(List<WavePlan> waves, int totalEnemies, int liveCap) {

        public Plan {
            waves = List.copyOf(waves);
        }

        public int waveCount() {
            return waves.size();
        }

        public boolean isEmpty() {
            return waves.isEmpty();
        }

        /** True when any wave's configured count had to be clamped to the cap. */
        public boolean truncated() {
            for (WavePlan wave : waves) {
                if (wave.clamped()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * The bounded live cap for a room. A configured value of {@code <= 0} is
     * treated as "use the ceiling" (an unset field is never an unbounded spawn).
     */
    public static int liveCap(int configured) {
        if (configured <= 0) {
            return MAX_LIVE_CAP;
        }
        return Math.min(configured, MAX_LIVE_CAP);
    }

    /**
     * Plans the room's waves for an eligible roster. Scaling never drops a
     * legitimate player and bounds total work per wave, and the result is
     * a pure function of its inputs so a replayed activation is idempotent.
     */
    public static Plan plan(DungeonRoomDefinition room, int eligiblePlayers) {
        Objects.requireNonNull(room, "room");
        int players = Math.max(1, eligiblePlayers);
        int cap = liveCap(room.maxConcurrentEnemies());
        List<WavePlan> waves = new ArrayList<>(room.waves().size());
        int startTick = 0;
        int total = 0;
        for (int index = 0; index < room.waves().size(); index++) {
            WaveSpec spec = room.waves().get(index);
            long scaled = scale(spec.totalCount(), room.scalingMode(), players);
            int bounded = (int) Math.min(scaled, MAX_LIVE_CAP);
            waves.add(new WavePlan(index, spec.delayTicks(), startTick, bounded, bounded != scaled));
            startTick += spec.delayTicks();
            total += bounded;
        }
        return new Plan(waves, total, cap);
    }

    private static long scale(int configured, ScalingMode mode, int players) {
        return switch (mode) {
            case FIXED -> configured;
            case PER_PLAYER, ADAPTIVE -> (long) configured * players;
        };
    }
}
