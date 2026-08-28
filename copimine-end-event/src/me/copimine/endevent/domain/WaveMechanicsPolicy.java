package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure bounds for the five initial-wave mechanics.  The Bukkit adapter owns
 * entities and blocks; this class keeps the numbers deterministic and easy to
 * regression-test without a running Paper server.
 */
public final class WaveMechanicsPolicy {
    private static final int MIN_PORTALS = 3;
    private static final int MAX_PORTALS = 6;
    private static final int STORM_FLOOR_CAP = 160;
    private static final int STORM_WEB_CAP = 12;

    private WaveMechanicsPolicy() {
    }

    /** The official portal curve: 2 -> 3, 3-5 -> 4, 6-10 -> 5, 11+ -> 6. */
    public static int portalCount(int players) {
        if (players <= 2) {
            return MIN_PORTALS;
        }
        if (players <= 5) {
            return 4;
        }
        if (players <= 10) {
            return 5;
        }
        return MAX_PORTALS;
    }

    /** Wave IV spawn cap by roster size, before the global 48-entity cap. */
    public static int towerMobCap(int players) {
        if (players <= 2) {
            return 16;
        }
        if (players <= 5) {
            return 22;
        }
        return 28;
    }

    /**
     * Apply the Wave IV cap after player scaling. Keeping this operation in
     * the pure policy makes it impossible for the Bukkit adapter to validate
     * one cap and spawn a larger composition by accident.
     */
    public static WaveCounts capTowerCounts(WaveCounts requested, int players) {
        if (requested == null) {
            return new WaveCounts(0, 0, 0, 0);
        }
        int[] counts = {
                Math.max(0, requested.endermen()),
                Math.max(0, requested.spiders()),
                Math.max(0, requested.shulkers()),
                Math.max(0, requested.eliteEndermen())};
        int total = counts[0] + counts[1] + counts[2] + counts[3];
        int cap = towerMobCap(players);
        while (total > cap) {
            int selected = 0;
            for (int index = 1; index < counts.length; index++) {
                if (counts[index] > counts[selected]) {
                    selected = index;
                }
            }
            if (counts[selected] == 0) {
                break;
            }
            counts[selected]--;
            total--;
        }
        return new WaveCounts(counts[0], counts[1], counts[2], counts[3]);
    }

    public static List<Integer> towerGroupCadenceSeconds() {
        return List.of(14, 12, 10);
    }

    public static int floorMutationCap(int availableCells) {
        return Math.max(0, Math.min(STORM_FLOOR_CAP, availableCells));
    }

    public static int webCap() {
        return STORM_WEB_CAP;
    }

    /**
     * Deterministic bounded role damage.  The id and attack sequence are part
     * of the input so retries never produce an accidental second value.
     */
    public static double roleDamage(TowerRole role, String entityId, int attackSequence) {
        if (role == null || entityId == null || entityId.isBlank() || attackSequence < 0) {
            return 0.0D;
        }
        long mixed = 0x9E3779B97F4A7C15L;
        mixed ^= entityId.hashCode() * 0xBF58476D1CE4E5B9L;
        mixed ^= (long) attackSequence * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        int span = Math.max(0, (int) Math.round(role.maxDamage() - role.minDamage()));
        int offset = span == 0 ? 0 : Math.floorMod((int) mixed, span + 1);
        return role.minDamage() + offset;
    }

    /** Select a small number of clustered web cells without touching safety cells. */
    public static Set<HazardPlanner.Point> selectWebCells(Set<HazardPlanner.Point> hazards,
                                                            Set<HazardPlanner.Point> protectedPoints,
                                                            Set<HazardPlanner.Point> occupiedPoints,
                                                            long seed) {
        Set<HazardPlanner.Point> blocked = new LinkedHashSet<>();
        if (protectedPoints != null) {
            blocked.addAll(protectedPoints);
        }
        if (occupiedPoints != null) {
            blocked.addAll(occupiedPoints);
        }
        List<HazardPlanner.Point> candidates = new ArrayList<>(hazards == null ? Set.of() : hazards);
        candidates.removeIf(point -> point == null || blocked.contains(point));
        candidates.sort(Comparator.comparingLong(point -> stableHash(point, seed)));
        Set<HazardPlanner.Point> result = new LinkedHashSet<>();
        for (HazardPlanner.Point candidate : candidates) {
            if (result.size() >= STORM_WEB_CAP) {
                break;
            }
            result.add(candidate);
            for (HazardPlanner.Point neighbour : neighbours(candidate)) {
                if (result.size() >= STORM_WEB_CAP) {
                    break;
                }
                if (candidates.contains(neighbour)) {
                    result.add(neighbour);
                }
            }
        }
        return Set.copyOf(result);
    }

    private static List<HazardPlanner.Point> neighbours(HazardPlanner.Point point) {
        return List.of(
                new HazardPlanner.Point(point.x() + 1, point.z()),
                new HazardPlanner.Point(point.x() - 1, point.z()),
                new HazardPlanner.Point(point.x(), point.z() + 1),
                new HazardPlanner.Point(point.x(), point.z() - 1));
    }

    private static long stableHash(HazardPlanner.Point point, long seed) {
        long value = seed ^ ((long) point.x() * 0x9E3779B97F4A7C15L)
                ^ ((long) point.z() * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        return value;
    }

    public enum TowerRole {
        RAIDER(12.0D, 18.0D, 1_500L),
        BREAKER(28.0D, 38.0D, 2_000L),
        ARTILLERY(15.0D, 22.0D, 2_500L);

        private final double minDamage;
        private final double maxDamage;
        private final long attackIntervalMillis;

        TowerRole(double minDamage, double maxDamage, long attackIntervalMillis) {
            this.minDamage = minDamage;
            this.maxDamage = maxDamage;
            this.attackIntervalMillis = attackIntervalMillis;
        }

        public double minDamage() {
            return minDamage;
        }

        public double maxDamage() {
            return maxDamage;
        }

        public long attackIntervalMillis() {
            return attackIntervalMillis;
        }
    }

    public record WaveCounts(int endermen, int spiders, int shulkers, int eliteEndermen) {
        public int total() {
            return Math.max(0, endermen) + Math.max(0, spiders)
                    + Math.max(0, shulkers) + Math.max(0, eliteEndermen);
        }
    }
}
