import me.copimine.endevent.domain.RitualCasterHandPolicy;

public final class RitualCasterHandPolicyTest {
    public static void main(String[] args) {
        var hands = RitualCasterHandPolicy.hands(0.0D, 0.0D);
        check(hands.size() == 2, "each channel starts at a distinct raised hand");
        check(Math.abs(hands.get(0).x() + hands.get(1).x()) < 1e-8,
                "neutral hands are symmetric around the caster");
        for (var hand : hands) {
            check(Math.abs(hand.x()) > 0.6D && Math.abs(hand.x()) < 0.65D,
                    "30-pixel arms put palms outside the shoulder");
            check(hand.y() > 3.70D && hand.y() < 3.74D,
                    "raised palm height includes the actual renderer's 1.501 translation");
            check(hand.z() > 0.83D && hand.z() < 0.86D,
                    "beam originates forward at the palms, not on the eye's vertical axis");
        }
        var east = RitualCasterHandPolicy.hands(-90.0D, 0.0D);
        for (int i = 0; i < 2; i++) {
            check(Math.abs(east.get(i).x() - hands.get(i).z()) < 1e-8
                    && Math.abs(east.get(i).z() + hands.get(i).x()) < 1e-8,
                    "caster body yaw rotates both palm endpoints");
        }
        for (double pulse : new double[]{-1, 0, 1}) {
            for (var hand : RitualCasterHandPolicy.hands(137.0D, pulse))
                check(Double.isFinite(hand.x()) && hand.y() > 3.6D && hand.y() < 3.85D,
                        "casting sway preserves finite raised-hand origins");
        }
        try { RitualCasterHandPolicy.hands(Double.NaN, 0); throw new AssertionError("invalid yaw"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("RitualCasterHandPolicyTest OK");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
