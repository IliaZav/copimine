package me.copimine.endevent.domain;

/**
 * The official End Rift V2 objective map.  The older objective policy remains
 * readable for snapshot migration and disposable probes, but the live V2
 * controller must use this policy so Wave 4/5 cannot silently fall back to
 * the removed Tower Defense/Rift Storm flow.
 */
public final class V2WaveObjectivePolicy {
    public static final int MAX_WAVE = 6;
    public static final int REQUIRED_CARRIER_CHARGES = 3;
    public static final int REQUIRED_HUNT_CYCLES = 3;
    public static final int REQUIRED_PORTALS = 3;
    public static final int REQUIRED_FOG_CYCLES = 3;
    public static final int REQUIRED_COLLAPSE_RINGS = 3;
    public static final int PORTAL_COUNT = 3;
    public static final int RING_COUNT = 3;
    public static final int SAFE_ZONE_PHASES = 3;
    public static final int SAFE_ZONE_HOLD_SECONDS = 4;
    public static final int FOG_SECONDS = 3;
    public static final int PRE_BOSS_SECONDS = 20;

    private V2WaveObjectivePolicy() {
    }

    public static Objective objective(int wave) {
        return switch (wave) {
            case 1 -> Objective.CARRIER;
            case 2 -> Objective.HUNT_MARK;
            case 3 -> Objective.PORTALS;
            case 4 -> Objective.BLACK_FOG;
            case 5 -> Objective.COLLAPSE_RINGS;
            case 6 -> Objective.CHAMBERS;
            default -> throw new IllegalArgumentException("unknown V2 wave: " + wave);
        };
    }

    public static String title(int wave) {
        return objective(wave).title();
    }

    public static int portalCount() {
        return PORTAL_COUNT;
    }

    /**
     * The three safe-zone sets are intentionally derived from the live roster
     * only once per phase.  The number never exceeds five and never creates a
     * zone with no possible occupant.
     */
    public static int safeZoneCount(int livingPlayers, int phaseIndex) {
        int players = Math.max(0, livingPlayers);
        if (players == 0) {
            return 0;
        }
        int phase = Math.max(0, Math.min(SAFE_ZONE_PHASES - 1, phaseIndex));
        int first = Math.min(5, Math.max(1, (players + 1) / 2));
        return Math.max(1, first - phase);
    }

    public static int safeZoneSide(int phaseIndex) {
        return switch (Math.max(0, Math.min(SAFE_ZONE_PHASES - 1, phaseIndex))) {
            case 0 -> 3;
            case 1 -> 2;
            default -> 1;
        };
    }

    public static int chamberCount(int players) {
        int safePlayers = Math.max(0, Math.min(20, players));
        if (safePlayers <= 0) {
            return 0;
        }
        return Math.min(4, Math.max(2, safePlayers));
    }

    /**
     * A chamber wave is complete only after the final scheduled group has
     * spawned and every event-owned chamber mob is gone.  Checking only the
     * current live count lets a staggered wave open its passage between
     * groups, which breaks room isolation and leaves the controller waiting on
     * mobs that arrive after the objective was announced complete.
     */
    public static boolean chambersComplete(boolean allGroupsSpawned, int liveMobs) {
        return allGroupsSpawned && liveMobs <= 0;
    }

    public static int ringCount() {
        return RING_COUNT;
    }

    public static boolean isComplete(int wave, Progress progress) {
        if (progress == null) {
            return false;
        }
        return switch (objective(wave)) {
            case CARRIER -> progress.carrierCharges() >= REQUIRED_CARRIER_CHARGES;
            case HUNT_MARK -> progress.huntCycles() >= REQUIRED_HUNT_CYCLES;
            case PORTALS -> progress.capturedPortals() >= REQUIRED_PORTALS;
            case BLACK_FOG -> progress.fogCycles() >= REQUIRED_FOG_CYCLES;
            case COLLAPSE_RINGS -> progress.collapsedRings() >= REQUIRED_COLLAPSE_RINGS;
            case CHAMBERS -> progress.allChambersCleared();
        };
    }

    public enum Objective {
        CARRIER("Носители Разлома"),
        HUNT_MARK("Охота и Метка Разлома"),
        PORTALS("Врата Разлома"),
        BLACK_FOG("Чёрный Туман"),
        COLLAPSE_RINGS("Кольца Коллапса"),
        CHAMBERS("Раскол Реальности");

        private final String title;

        Objective(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    public record Progress(int carrierCharges, int huntCycles, int capturedPortals,
                           int fogCycles, int collapsedRings,
                           boolean allChambersCleared) {
        public Progress {
            if (carrierCharges < 0 || huntCycles < 0 || capturedPortals < 0
                    || fogCycles < 0 || collapsedRings < 0) {
                throw new IllegalArgumentException("V2 wave progress cannot be negative");
            }
        }
    }
}
