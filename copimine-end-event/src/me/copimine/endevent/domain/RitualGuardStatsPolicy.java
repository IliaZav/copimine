package me.copimine.endevent.domain;

/** One-time bounded Wave 6 guard health tuning, separate from tentacle scaling. */
public final class RitualGuardStatsPolicy {
    public static final double HEALTH_INCREASE = 1.50D;
    public static final double MAX_TUNED_HEALTH = 80.0D;

    private RitualGuardStatsPolicy() {
    }

    public static double maximumHealth(double previousMaximum, boolean alreadyTuned) {
        if (!Double.isFinite(previousMaximum) || previousMaximum <= 0.0D) {
            throw new IllegalArgumentException("guard maximum health must be finite and positive");
        }
        if (alreadyTuned) {
            return previousMaximum;
        }
        return Math.max(previousMaximum,
                Math.min(MAX_TUNED_HEALTH, previousMaximum * HEALTH_INCREASE));
    }
}
