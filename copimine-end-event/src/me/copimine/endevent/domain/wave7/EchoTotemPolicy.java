package me.copimine.endevent.domain.wave7;

/**
 * GEAR-007 / GEAR-009: a personal copy's Totem of Undying.
 *
 * <p>Two opposite mistakes are both live defects: a totem that is only an
 * inventory descriptor (the copy dies with a totem "in hand") and a totem that
 * resurrects unconditionally. The rule is narrow - it must be equipped, it
 * fires only on a genuinely lethal hit, and it is spent.
 */
public final class EchoTotemPolicy {
    /** Share of maximum health restored by activation. */
    public static final double ACTIVATION_HEALTH_FRACTION = 0.5D;

    private EchoTotemPolicy() {
    }

    /** True when this hit would take the copy to zero or below while it is still alive. */
    public static boolean wouldBeLethal(double currentHealth, double incomingDamage) {
        if (!Double.isFinite(currentHealth) || !Double.isFinite(incomingDamage)
                || currentHealth <= 0.0D || incomingDamage <= 0.0D) {
            return false;
        }
        return currentHealth - incomingDamage <= 0.0D;
    }

    /**
     * @param hasTotem       the copy holds a real totem
     * @param equipped       the totem is in a supported equipped/held state
     * @param usedInThisLife this copy already activated one since admission
     */
    public static boolean mayActivate(boolean hasTotem, boolean equipped, boolean usedInThisLife) {
        return hasTotem && equipped && !usedInThisLife;
    }

    /** Activation restores a bounded share of maximum health; see the caller for the cap. */
    public static double activationHealth(double maximumHealth) {
        if (!Double.isFinite(maximumHealth) || maximumHealth <= 0.0D) {
            return 0.0D;
        }
        return maximumHealth * ACTIVATION_HEALTH_FRACTION;
    }

    /** The totem is spent on activation, which is what makes a second use impossible. */
    public static boolean consumedOnActivation() {
        return true;
    }

    /** GEAR-009: no phantom shield survives the switch to a held totem. */
    public static boolean shieldUsableWhileHoldingTotem() {
        return false;
    }

    public static boolean switchCostsATransition() {
        return true;
    }
}