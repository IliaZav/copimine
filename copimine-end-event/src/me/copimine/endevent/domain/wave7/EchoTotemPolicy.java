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

    /**
     * The supported equipped/held states for a copy's totem.
     *
     * <p>A totem loose in the pack is not "equipped": activation requires the
     * offhand slot the copy actually holds, or the hotbar slot it is using. The
     * slot numbers are the frozen {@link EchoLoadoutState} layout, not a
     * caller's convention.</p>
     */
    public static boolean isEquippedSlot(int totemSlot, int selectedSlot) {
        if (totemSlot < 0) return false;
        if (totemSlot == EchoLoadoutState.OFFHAND_SLOT) return true;
        return totemSlot == selectedSlot && selectedSlot >= 0 && selectedSlot <= 8;
    }

    /**
     * The single gate the runtime calls at the copy's lethal damage boundary.
     *
     * <p>Both halves are required: the incoming hit must really be lethal, and
     * the totem must be present, equipped and not already spent in this life.
     * A surviving copy must never be produced by a totem that is merely in the
     * inventory, nor by one that already fired.</p>
     */
    public static boolean activationAllowed(double currentHealth, double incomingDamage,
                                            boolean holdsTotem, boolean equipped,
                                            boolean usedInThisLife) {
        return wouldBeLethal(currentHealth, incomingDamage)
                && mayActivate(holdsTotem, equipped, usedInThisLife);
    }
}