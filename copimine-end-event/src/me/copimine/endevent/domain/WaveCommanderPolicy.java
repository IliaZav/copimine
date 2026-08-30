package me.copimine.endevent.domain;

/** Small, bounded commander policy shared by the wave controller and tests. */
public final class WaveCommanderPolicy {
    public static final int FIRST_DIFFICULT_WAVE = 3;
    public static final int LAST_DIFFICULT_WAVE = 6;
    public static final double AURA_RADIUS_BLOCKS = 10.0D;
    public static final int AURA_DURATION_TICKS = 40;
    public static final int AURA_AMPLIFIER = 0; // Strength I.
    public static final int MAX_LIVE_MOBS = 56;

    private WaveCommanderPolicy() {
    }

    public static boolean isDifficultWave(int wave) {
        return wave >= FIRST_DIFFICULT_WAVE && wave <= LAST_DIFFICULT_WAVE;
    }

    public static boolean shouldAssign(int wave, boolean elite, boolean alreadyAssigned) {
        return isDifficultWave(wave) && elite && !alreadyAssigned;
    }

    public static String displayName(String baseName) {
        String safe = baseName == null || baseName.isBlank() ? "Страж Разлома" : baseName.trim();
        return "Командир волны · " + safe;
    }
}
