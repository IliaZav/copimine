package me.copimine.endevent.domain;

/** Single bounded reduction applied to wave and mini-boss outgoing damage. */
public final class WaveDamagePolicy {
    private WaveDamagePolicy() {
    }

    public static double reduce(double configuredDamage, double reduction) {
        double damage = finite(configuredDamage) ? Math.max(0.0D, configuredDamage) : 0.0D;
        double amount = finite(reduction) ? Math.max(0.0D, reduction) : 0.0D;
        return Math.max(0.0D, damage - amount);
    }

    /**
     * Apply the wave damage curve to one attack.
     *
     * <p>The old rule subtracted a flat {@code 4.0} from every attack, then
     * clamped at {@code 1.0}. An Enderman's base attack is {@code 7.0}, so an
     * ordinary wave body hit for {@code 3.0} and a player in iron armour took
     * {@code final=0.0} on practically every swing: mobs looked like they were
     * attacking with no effect. The curve now removes a FRACTION of the raw
     * value, so a bigger attack always lands more damage, and a hit that cleared
     * all protection still cannot fall below {@link #MINIMUM_HIT_DAMAGE}.
     *
     * @param configuredDamage raw attack damage, may be non-finite
     * @param reduction       flat damage reduction configured for the role
     */
    public static double minimumCombatDamage(double configuredDamage, double reduction) {
        double raw = finite(configuredDamage) ? Math.max(0.0D, configuredDamage) : 0.0D;
        double amount = finite(reduction) ? Math.max(0.0D, reduction) : 0.0D;
        if (raw <= 0.0D) {
            return amount > 0.0D ? MINIMUM_HIT_DAMAGE : 0.0D;
        }
        double scaled = raw * (1.0D - Math.min(MAX_REDUCTION_FRACTION, amount * reductionFractionPerPoint()));
        // The wave curve removes a share of the attack, so a bigger attack always
        // lands more and a weak one keeps its own (smaller) value: the old flat
        // subtraction plus the old 1.5 floor turned the weakest attacks UP.
        // A hit that cleared protection still keeps a readable minimum, and an
        // attack that arrives as zero plus protection resolves to that minimum.
        return Math.max(MINIMUM_HIT_DAMAGE, scaled);
    }

    /** Share of the raw attack removed by the wave damage curve at its softest. */
    public static final double MAX_REDUCTION_FRACTION = 0.35D;

    /** One configured reduction point removes this share, softly nested under the cap. */
    private static double reductionFractionPerPoint() {
        return 0.05D;
    }

    /** A wave attack that reached the player always has a readable, non-zero effect. */
    public static final double MINIMUM_HIT_DAMAGE = 1.5D;

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
