package me.copimine.client;

/** Client-side distance guard aligned with the server-validated prisoner abilities. */
public final class PrisonerTargetRangePolicy {
    public static final double SUPPORT_TARGET_RANGE = 24.0D;
    public static final double TURNCOAT_TARGET_RANGE = 28.0D;

    private PrisonerTargetRangePolicy() {
    }

    public static boolean allows(PrisonerHudController.Ability ability, double distance) {
        if (ability == null || !Double.isFinite(distance) || distance < 0.0D) {
            return false;
        }
        double maximumDistance = switch (ability) {
            case HEAL, BATTLE_SURGE, GUARDIAN_LINK -> SUPPORT_TARGET_RANGE;
            case TURNCOAT -> TURNCOAT_TARGET_RANGE;
        };
        return distance <= maximumDistance;
    }
}
