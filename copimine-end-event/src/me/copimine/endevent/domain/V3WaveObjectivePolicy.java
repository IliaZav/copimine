package me.copimine.endevent.domain;

/**
 * Canonical End Rift V3 wave map.  The integer passed to this class is the
 * official wave number shown to players; the legacy objective slot is exposed
 * separately so a migration adapter cannot silently change the public flow.
 */
public final class V3WaveObjectivePolicy {
    public static final int MAX_WAVE = 7;
    public static final int PRE_BOSS_SECONDS = 20;

    private V3WaveObjectivePolicy() {
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
            default -> throw new IllegalArgumentException("unknown V3 wave: " + wave);
        };
    }

    public static String title(int wave) {
        return objective(wave).title();
    }

    /**
     * Maps only the still-compatible objectives to their old implementation
     * slot.  Wave 4 is intentionally -1: its real-block obelisk controller
     * must not fall through to the old fog implementation.
     */
    public static int legacyObjectiveSlot(int wave) {
        return switch (wave) {
            case 1, 2, 3 -> wave;
            case 4 -> -1;
            case 5 -> 4;
            case 6 -> 5;
            case 7 -> 6;
            default -> 0;
        };
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

    /** V3 uses chambers only for Wave 7: 2 -> 2, 3 -> 3, 4+ -> 4. */
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
