package me.copimine.dungeon.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure core of the dungeon room controller's spawn plan (spec OV4.10
 * {@code DungeonRoomController}; verification R01/R04).
 *
 * <p>{@link RoomWaveSchedulePolicy} answers "how many enemies and when"; this
 * class turns that bounded schedule into concrete, deterministic spawn
 * assignments against the room's validated anchors. The same room, anchors and
 * eligible roster always produce exactly the same plan, so a simultaneous or
 * replayed activation can never stand up a second, differently-shuffled wave.
 * Only anchors that belong to the room and its world are eligible; a room with
 * no usable anchor is not viable and must not be started (fail closed), so the
 * runtime never invents a spawn position.</p>
 *
 * <p>This class owns no Bukkit types and no live world state; the Paper adapter
 * only materialises the returned assignments.</p>
 */
public final class DungeonEncounterPlanPolicy {

    /** One concrete enemy to spawn for one wave. */
    public record Assignment(int waveIndex, int ordinalInWave, MobTemplate template,
                             String anchorId, int x, int y, int z) {

        public Assignment {
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(anchorId, "anchorId");
        }
    }

    /**
     * A whole deterministic room plan. {@code viable == false} means the room
     * cannot be started at all (no waves, no usable anchor) and {@code reason}
     * says why in an operator-readable form.
     */
    public record Plan(int eligibleParticipants, int liveCap, int waveCount, boolean viable,
                       String reason, List<Assignment> assignments, boolean truncated) {

        public Plan {
            assignments = List.copyOf(assignments);
            reason = reason == null ? "" : reason;
        }

        public int totalAssignments() {
            return assignments.size();
        }

        /** The assignments belonging to one wave, in stable spawn order. */
        public List<Assignment> wave(int waveIndex) {
            List<Assignment> selected = new ArrayList<>();
            for (Assignment assignment : assignments) {
                if (assignment.waveIndex() == waveIndex) {
                    selected.add(assignment);
                }
            }
            selected.sort(Comparator.comparingInt(Assignment::ordinalInWave));
            return selected;
        }
    }

    private DungeonEncounterPlanPolicy() {
    }

    /**
     * Builds the bounded, deterministic spawn plan for a room and roster.
     *
     * @param room               the durable room definition
     * @param anchors            every known spawn anchor (foreign rooms ignored)
     * @param eligibleParticipants the eligible live roster, clamped to >= 1
     */
    public static Plan plan(DungeonRoomDefinition room, List<SpawnAnchor> anchors,
                            int eligibleParticipants) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(anchors, "anchors");
        int players = Math.max(1, eligibleParticipants);
        int cap = RoomWaveSchedulePolicy.liveCap(room.maxConcurrentEnemies());

        List<SpawnAnchor> usable = new ArrayList<>();
        for (SpawnAnchor anchor : anchors) {
            if (anchor == null) {
                continue;
            }
            if (!anchor.roomId().equals(room.id())) {
                continue;
            }
            if (!room.world().isBlank() && !anchor.world().equalsIgnoreCase(room.world())) {
                continue;
            }
            usable.add(anchor);
        }
        // Stable order independent of catalogue insertion order.
        usable.sort(Comparator.comparing(SpawnAnchor::id));

        if (room.waves().isEmpty()) {
            return new Plan(players, cap, 0, false, "room has no waves", List.of(), false);
        }
        if (usable.isEmpty()) {
            return new Plan(players, cap, room.waves().size(), false,
                    "room has no usable spawn anchor", List.of(), false);
        }

        RoomWaveSchedulePolicy.Plan schedule = RoomWaveSchedulePolicy.plan(room, players);
        List<Assignment> assignments = new ArrayList<>(schedule.totalEnemies());
        int anchorCursor = 0;
        for (int waveIndex = 0; waveIndex < room.waves().size(); waveIndex++) {
            WaveSpec spec = room.waves().get(waveIndex);
            int target = schedule.waves().get(waveIndex).enemyCount();
            int[] counts = distribute(spec, room.scalingMode(), players, target);
            int ordinal = 0;
            for (int groupIndex = 0; groupIndex < counts.length; groupIndex++) {
                MobTemplate template = spec.group().get(groupIndex).template();
                for (int n = 0; n < counts[groupIndex]; n++) {
                    SpawnAnchor anchor = usable.get(anchorCursor % usable.size());
                    anchorCursor++;
                    assignments.add(new Assignment(waveIndex, ordinal++, template,
                            anchor.id(), anchor.x(), anchor.y(), anchor.z()));
                }
            }
        }
        return new Plan(players, cap, room.waves().size(), true, "ok",
                assignments, schedule.truncated());
    }

    /**
     * Distributes the wave's already-bounded total across its enemy groups while
     * preserving the configured proportion. When the cap forced the total down,
     * the remainder is handed to the earliest groups so the result is stable.
     */
    static int[] distribute(WaveSpec spec, ScalingMode mode, int players, int target) {
        int groups = spec.group().size();
        int[] counts = new int[groups];
        if (groups == 0 || target <= 0) {
            return counts;
        }
        int[] raw = new int[groups];
        long rawSum = 0L;
        for (int i = 0; i < groups; i++) {
            EnemyGroup group = spec.group().get(i);
            raw[i] = mode == ScalingMode.FIXED ? group.count() : group.count() * players;
            rawSum += raw[i];
        }
        if (rawSum <= 0L) {
            return counts;
        }
        if (rawSum <= target) {
            System.arraycopy(raw, 0, counts, 0, groups);
            return counts;
        }
        int assigned = 0;
        for (int i = 0; i < groups; i++) {
            counts[i] = (int) ((long) raw[i] * target / rawSum);
            assigned += counts[i];
        }
        int remainder = target - assigned;
        int cursor = 0;
        int guard = 0;
        while (remainder > 0 && guard < groups * (target + 1) + groups) {
            int index = cursor % groups;
            if (counts[index] < raw[index]) {
                counts[index]++;
                remainder--;
            }
            cursor++;
            guard++;
        }
        return counts;
    }
}
