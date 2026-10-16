package me.copimine.endevent.domain;

/** Reconciles the Wave 1 carrier's dedicated movement-speed modifier. */
public final class CarrierSlownessLeasePolicy {
    public static final double MOVEMENT_SPEED_MULTIPLIER = -0.30D;

    private CarrierSlownessLeasePolicy() { }

    public static Action reconcile(boolean carryingCharge, boolean modifierPresent) {
        if (carryingCharge) {
            return modifierPresent ? Action.KEEP : Action.ADD;
        }
        return modifierPresent ? Action.REMOVE : Action.NONE;
    }

    public enum Action {
        ADD,
        KEEP,
        REMOVE,
        NONE
    }
}
