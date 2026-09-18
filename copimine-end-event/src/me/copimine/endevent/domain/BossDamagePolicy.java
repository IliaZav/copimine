package me.copimine.endevent.domain;

/**
 * Damage gate for the current real-health boss.
 *
 * <p>Normal abilities never make the boss immune. Only an explicit lifecycle
 * boundary may reject damage, and the reason is carried in the decision so a
 * generic cast flag cannot become an invulnerability switch.</p>
 */
public final class BossDamagePolicy {
    private BossDamagePolicy() {
    }

    public static Decision evaluate(BossPhase phase,
                                    BossPhasePolicy.DamageImmunityReason reason) {
        BossPhase safePhase = phase == null ? BossPhase.AWAKENING : phase;
        BossPhasePolicy.DamageImmunityReason safeReason = reason == null
                ? BossPhasePolicy.DamageImmunityReason.NONE : reason;
        boolean allowed = safeReason == BossPhasePolicy.DamageImmunityReason.NONE;
        return new Decision(safePhase, allowed, safeReason, 1.0D);
    }

    public static boolean damageAllowed(BossPhase phase,
                                        BossPhasePolicy.DamageImmunityReason reason) {
        return evaluate(phase, reason).allowed();
    }

    /** Normal current-phase damage is never multiplied by a cast state. */
    public static double applyIncomingDamage(double finalDamage) {
        if (!Double.isFinite(finalDamage) || finalDamage <= 0.0D) return 0.0D;
        return finalDamage;
    }

    public record Decision(BossPhase phase, boolean allowed,
                           BossPhasePolicy.DamageImmunityReason reason,
                           double multiplier) {
        public Decision {
            phase = phase == null ? BossPhase.AWAKENING : phase;
            reason = reason == null ? BossPhasePolicy.DamageImmunityReason.NONE : reason;
            multiplier = Double.isFinite(multiplier) && multiplier > 0.0D
                    ? multiplier : 1.0D;
        }
    }
}
