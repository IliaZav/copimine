import me.copimine.endevent.domain.RitualShieldOrbitPolicy;

public final class RitualShieldOrbitPolicyTest {
    public static void main(String[] args) {
        for (int count = 1; count <= 3; count++) {
            for (int slot = 0; slot < count; slot++) {
                var pose = RitualShieldOrbitPolicy.pose(slot, count, 57L);
                check(Math.abs(Math.hypot(pose.x(), pose.z()) - 1.0) < 1e-8, "shield stays close to its caster");
                check(pose.y() >= 1.2 && pose.y() <= 1.8, "shield stays at torso height");
                check(Double.isFinite(pose.yawDegrees()), "finite shield rotation");
                check(!pose.equals(RitualShieldOrbitPolicy.pose(slot, count, 67L)), "shield moves continuously");
            }
        }
        var unchangedSlot = RitualShieldOrbitPolicy.pose(2, 3, 57L);
        check(unchangedSlot.equals(RitualShieldOrbitPolicy.pose(2, 3, 57L)), "other guard death cannot renumber a surviving slot");
        System.out.println("RitualShieldOrbitPolicyTest OK");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
