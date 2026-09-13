import me.copimine.endevent.domain.CollapseRingGeometryPolicy;

public final class CollapseRingGeometryPolicyTest {
    public static void main(String[] args) {
        check(CollapseRingGeometryPolicy.RING_COUNT == 3,
                "Wave 6 must keep three collapse rings");
        check(CollapseRingGeometryPolicy.ringRadius(0) == 6.0D,
                "first ring radius must leave the Core readable");
        check(CollapseRingGeometryPolicy.ringRadius(1) == 11.0D,
                "second ring must be visibly separated");
        check(CollapseRingGeometryPolicy.ringRadius(2) == 16.0D,
                "third ring must use the outer arena lane");
        check(CollapseRingGeometryPolicy.visualPointCount(0) >= 48,
                "first ring needs a readable circumference");
        check(CollapseRingGeometryPolicy.visualPointCount(2) >= 64,
                "outer ring needs enough points for a continuous visual");
        check(CollapseRingGeometryPolicy.visualPointCount(2)
                        <= CollapseRingGeometryPolicy.MAX_VISUAL_POINTS,
                "ring visuals must stay bounded");
        check(CollapseRingGeometryPolicy.inRingBand(1, 11.0D, 0.0D),
                "a guard on the second ring must be inside its lane");
        check(!CollapseRingGeometryPolicy.inRingBand(1, 2.0D, 0.0D),
                "the Core must not count as a ring lane");
        check(CollapseRingGeometryPolicy.clampToRingRadius(2, 18.0D) == 16.0D,
                "ring leash must clamp an escaped guard to its lane");
        System.out.println("CollapseRingGeometryPolicyTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
