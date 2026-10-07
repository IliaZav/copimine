import me.copimine.endevent.domain.CollapseRingGeometryPolicy;

public final class CollapseRingGeometryPolicyTest {
    public static void main(String[] args) {
        check(CollapseRingGeometryPolicy.RING_COUNT == 3,
                "Wave 6 must keep three collapse rings");
        check(CollapseRingGeometryPolicy.ringRadius(0) == 8.0D,
                "first ring radius must leave the Core readable");
        check(CollapseRingGeometryPolicy.ringRadius(1) == 14.0D,
                "second ring must be visibly separated");
        check(CollapseRingGeometryPolicy.ringRadius(2) == 19.0D,
                "third ring must use the outer arena lane");
        check(CollapseRingGeometryPolicy.visualPointCount(0) >= 48,
                "first ring needs a readable circumference");
        check(CollapseRingGeometryPolicy.visualPointCount(2) >= 64,
                "outer ring needs enough points for a continuous visual");
        check(CollapseRingGeometryPolicy.visualPointCount(2)
                        <= CollapseRingGeometryPolicy.MAX_VISUAL_POINTS,
                "ring visuals must stay bounded");
        check(CollapseRingGeometryPolicy.inRingBand(1, 14.0D, 0.0D),
                "a guard on the second ring must be inside its lane");
        check(!CollapseRingGeometryPolicy.inRingBand(1, 2.0D, 0.0D),
                "the Core must not count as a ring lane");
        check(CollapseRingGeometryPolicy.clampToRingRadius(2, 18.0D) == 19.0D,
                "ring leash must clamp an escaped guard to its lane");
        check(CollapseRingGeometryPolicy.PLAYER_LANE_HALF_WIDTH == 1.0D,
                "player containment must leave a readable playable lane");
        check(CollapseRingGeometryPolicy.inPlayerLane(1, 14.0D, 0.0D),
                "a player at the active ring must be inside the playable lane");
        check(!CollapseRingGeometryPolicy.inPlayerLane(1, 3.0D, 0.0D),
                "a player near the Core must not count as being on a ring");
        check(CollapseRingGeometryPolicy.clampToPlayerLaneRadius(1, 3.0D) == 13.0D,
                "player movement must stop at the inner active-ring boundary");
        check(CollapseRingGeometryPolicy.clampToPlayerLaneRadius(1, 30.0D) == 15.0D,
                "player movement must stop at the outer active-ring boundary");
        System.out.println("CollapseRingGeometryPolicyTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
