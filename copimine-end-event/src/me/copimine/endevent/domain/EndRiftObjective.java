package me.copimine.endevent.domain;

/**
 * Canonical End Rift wave map.  The integer passed to this class is the
 * official wave number shown to players; the objective id is also the
 * persistence key for the current encounter.
 */
public final class EndRiftObjective {
    public static final int MAX_WAVE = 7;
    public static final int PRE_BOSS_SECONDS = 20;
    public static final int REQUIRED_CARRIER_DELIVERIES = 3;
    public static final int REQUIRED_HUNT_CYCLES = 3;
    public static final int REQUIRED_GATES = 3;
    public static final int REQUIRED_FOG_CYCLES = 3;
    public static final int REQUIRED_COLLAPSE_RINGS = 3;
    public static final int GATE_COUNT = 3;
    public static final int RING_COUNT = 3;
    public static final int FOG_ZONE_PHASES = 3;

    private EndRiftObjective() {
    }

    public static Objective objective(int wave) {
        return switch (wave) {
            case 1 -> Objective.RIFT_CARRIERS;
            case 2 -> Objective.RIFT_HUNT;
            case 3 -> Objective.RIFT_GATES;
            case 4 -> Objective.OBELISK_ASSAULT;
            case 5 -> Objective.BLACK_FOG;
            case 6 -> Objective.COLLAPSE_RINGS;
            case 7 -> Objective.REALITY_SPLIT;
            default -> throw new IllegalArgumentException("unknown wave: " + wave);
        };
    }

    public static String title(int wave) {
        return objective(wave).title();
    }

    public static boolean isNumberedWave(int wave) {
        return wave >= 1 && wave <= MAX_WAVE;
    }

    public static boolean hasTransitionRunesAfter(int completedWave) {
        return completedWave >= 1 && completedWave <= 6;
    }

    public static int nextWave(int completedWave) {
        return isNumberedWave(completedWave) && completedWave < MAX_WAVE
                ? completedWave + 1 : 0;
    }

    public static int fogZoneCount(int livingPlayers, int phaseIndex) {
        int players = Math.max(0, Math.min(20, livingPlayers));
        if (players == 0) {
            return 0;
        }
        int phase = Math.max(0, Math.min(FOG_ZONE_PHASES - 1, phaseIndex));
        int first = Math.min(5, Math.max(1, (players + 1) / 2));
        return Math.max(1, first - phase);
    }

    public static int fogZoneSide(int phaseIndex) {
        return switch (Math.max(0, Math.min(FOG_ZONE_PHASES - 1, phaseIndex))) {
            case 0 -> 3;
            case 1 -> 2;
            default -> 1;
        };
    }

    public static boolean realitySplitComplete(boolean allGroupsSpawned, int liveMobs) {
        return allGroupsSpawned && liveMobs <= 0;
    }

    /** uses chambers only for Wave 7: 2 -> 2, 3 -> 3, 4+ -> 4. */
    public static int chamberCount(int livingPlayers) {
        if (livingPlayers < 2) {
            return 0;
        }
        return Math.min(4, livingPlayers);
    }

    public enum Objective {
        RIFT_CARRIERS("Носители Разлома"),
        RIFT_HUNT("Охота и Метка Разлома"),
        RIFT_GATES("Врата Разлома"),
        OBELISK_ASSAULT("Штурм Обелисков"),
        BLACK_FOG("Чёрный Туман"),
        COLLAPSE_RINGS("Кольца Коллапса"),
        REALITY_SPLIT("Раскол Реальности");

        private final String title;

        Objective(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }
}
