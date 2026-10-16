package me.copimine.protocol;

/**
 * Shared wire and persistence contract for End Rift encounter generations.
 *
 * <p>Zero identifies an empty, unconfigured snapshot. Active generations use
 * positive values up to {@link #MAX_SUPPORTED_GENERATION}; {@link Long#MAX_VALUE}
 * remains reserved so increment and parsing failures cannot wrap into a valid
 * encounter.</p>
 */
public final class EncounterGenerationPolicy {
    public static final long EMPTY_GENERATION = 0L;
    public static final long MAX_SUPPORTED_GENERATION = Long.MAX_VALUE - 1L;

    private EncounterGenerationPolicy() {
    }

    public static boolean isSnapshotGeneration(long generation) {
        return generation >= EMPTY_GENERATION && generation <= MAX_SUPPORTED_GENERATION;
    }

    public static boolean isActiveGeneration(long generation) {
        return generation > EMPTY_GENERATION && generation <= MAX_SUPPORTED_GENERATION;
    }

    public static boolean canAdvance(long generation) {
        return generation >= EMPTY_GENERATION && generation < MAX_SUPPORTED_GENERATION;
    }

    public static long requireActiveGeneration(long generation) {
        if (!isActiveGeneration(generation)) {
            throw new IllegalArgumentException("generation must be positive and supported");
        }
        return generation;
    }

    public static long next(long currentGeneration) {
        if (!isSnapshotGeneration(currentGeneration)) {
            throw new IllegalArgumentException("current generation is invalid");
        }
        if (!canAdvance(currentGeneration)) {
            throw new IllegalStateException("generation exhausted; administrative recovery required");
        }
        return currentGeneration + 1L;
    }
}
