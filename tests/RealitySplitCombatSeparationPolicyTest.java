import me.copimine.endevent.domain.RealitySplitCombatSeparationPolicy;

public final class RealitySplitCombatSeparationPolicyTest {
    public static void main(String[] args) {
        RealitySplitCombatSeparationPolicy.Point pulled =
                RealitySplitCombatSeparationPolicy.preferSafeTargetPoint(
                        7, true,
                        8.5D, -39.5D,
                        8.5D, 68.0D, -49.5D,
                        8.5D, 68.0D, -49.5D,
                        1.75D);
        check(pulled != null, "a closed Wave 7 target must produce a point");
        check(pulled.z() > -49.5D, "a north-room target must pull mobs toward the Core");
        check(Math.abs(pulled.x() - 8.5D) < 0.0001D,
                "the inward correction must preserve the target's lateral line");
        check(Math.abs(Math.hypot(pulled.x() - 8.5D, pulled.z() + 39.5D)
                - 8.25D) < 0.0001D,
                "the corrected point must remain in the target room at the requested separation");

        RealitySplitCombatSeparationPolicy.Point alreadySeparated =
                RealitySplitCombatSeparationPolicy.preferSafeTargetPoint(
                        7, true,
                        8.5D, -39.5D,
                        8.5D, 68.0D, -49.5D,
                        10.5D, 68.0D, -49.5D,
                        1.75D);
        check(alreadySeparated.x() == 10.5D && alreadySeparated.z() == -49.5D,
                "an already separated tactical point must not be rewritten");

        check(RealitySplitCombatSeparationPolicy.preferSafeTargetPoint(
                6, true, 0.0D, 0.0D, 0.0D, 68.0D, 8.0D,
                0.0D, 68.0D, 8.0D, 1.75D) == null,
                "Wave 6 must not inherit the closed-room correction");
        check(RealitySplitCombatSeparationPolicy.preferSafeTargetPoint(
                7, false, 0.0D, 0.0D, 0.0D, 68.0D, 8.0D,
                0.0D, 68.0D, 8.0D, 1.75D) == null,
                "an opened room must not retain the closed-room correction");
        System.out.println("RealitySplitCombatSeparationPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
