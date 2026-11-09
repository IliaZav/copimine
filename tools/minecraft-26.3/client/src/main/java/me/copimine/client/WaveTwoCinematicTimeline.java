package me.copimine.client;

/** Pure presentation clock. The signed wave age, never a group timer, owns this sequence. */
final class WaveTwoCinematicTimeline {
    static final long INTRO_MILLIS = 10_000L;
    static final long SCREAM_MILLIS = 1_500L;
    static final long FIELD_START_MILLIS = 3_500L;
    static final long FIELD_FULL_MILLIS = 8_500L;

    private WaveTwoCinematicTimeline() { }

    static boolean firstObserverVisible(long age) { return age >= 0 && age < FIELD_START_MILLIS; }
    static boolean screaming(long age) { return age >= SCREAM_MILLIS && age < FIELD_START_MILLIS; }
    static boolean combatReady(long age) { return age >= INTRO_MILLIS; }
    static int visibleCount(long age, int population) {
        if (age < FIELD_START_MILLIS || population <= 0) return 0;
        if (age >= FIELD_FULL_MILLIS) return population;
        return Math.min(population, 1 + (int) ((age - FIELD_START_MILLIS) * population
                / (FIELD_FULL_MILLIS - FIELD_START_MILLIS)));
    }
    static long appearanceMillis(int index, int population) {
        return FIELD_START_MILLIS + Math.max(0, index) * (FIELD_FULL_MILLIS - FIELD_START_MILLIS)
                / Math.max(1, population);
    }
    // Pinned vanilla minecraft:invert uses InverseAmount=.8, not full color inversion.
    static float shaderStrength(long age) { return 0.8F * smooth((age - SCREAM_MILLIS) / 1_000.0F); }
    static float darknessStrength(long age) {
        return 0.65F * smooth((age - 2_000L) / 700.0F) * (1.0F - smooth((age - 3_300L) / 1_200.0F));
    }
    /** Skip stale queued appearances after a slow frame instead of bursting dozens of sounds. */
    static int firstAudibleAppearance(int previousCount, int currentCount) {
        return Math.max(previousCount, currentCount - 2);
    }
    static boolean screamCue(long previousAge, long age) {
        return previousAge < SCREAM_MILLIS && age >= SCREAM_MILLIS && age < FIELD_START_MILLIS;
    }
    static int parseIntroAge(String value) {
        if (value == null || value.isEmpty()) return 0;
        if (!value.matches("intro-age=[0-9]{1,5}")) return -1;
        int age = Integer.parseInt(value.substring(10));
        return age <= INTRO_MILLIS ? age : -1;
    }
    private static float smooth(float value) {
        float bounded = Math.max(0.0F, Math.min(1.0F, value));
        return bounded * bounded * (3.0F - 2.0F * bounded);
    }
}
