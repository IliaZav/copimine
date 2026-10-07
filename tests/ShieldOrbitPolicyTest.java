import java.util.List;
import me.copimine.endevent.domain.ShieldOrbitPolicy;

public final class ShieldOrbitPolicyTest {
    public static void main(String[] args) {
        testOneFiniteSegmentExistsPerLivingGuardian();
        testOrbitAdvancesAndRemainsFiniteAtLargeTicks();
        testFourShieldsStayCloseToTheBoss();
        System.out.println("ShieldOrbitPolicyTest OK");
    }

    private static void testOneFiniteSegmentExistsPerLivingGuardian() {
        List<ShieldOrbitPolicy.Segment> segments = ShieldOrbitPolicy.positions(10.0D, 64.0D,
                -5.0D, 40L, 3);
        check(segments.size() == 3, "one orbit segment per guardian");
        check(segments.stream().allMatch(ShieldOrbitPolicy.Segment::isFinite),
                "orbit coordinates and yaw are finite");
        check(segments.stream().allMatch(segment -> Math.abs(segment.y() - 67.1D) < 0.181D),
                "orbit stays readable around the guardian chest, not its knees");
    }

    private static void testOrbitAdvancesAndRemainsFiniteAtLargeTicks() {
        List<ShieldOrbitPolicy.Segment> first = ShieldOrbitPolicy.positions(10.0D, 64.0D,
                -5.0D, 40L, 3);
        List<ShieldOrbitPolicy.Segment> later = ShieldOrbitPolicy.positions(10.0D, 64.0D,
                -5.0D, 60L, 3);
        check(!first.equals(later), "orbit advances with server tick");
        check(ShieldOrbitPolicy.positions(10.0D, 64.0D, -5.0D, Long.MAX_VALUE, 3)
                        .stream().allMatch(ShieldOrbitPolicy.Segment::isFinite),
                "large server ticks must stay finite");
        check(ShieldOrbitPolicy.positions(Double.NaN, 64.0D, -5.0D, 0L, 3).isEmpty(),
                "invalid boss locations fail closed");
    }

    private static void testFourShieldsStayCloseToTheBoss() {
        List<ShieldOrbitPolicy.Segment> segments = ShieldOrbitPolicy.positions(
                10.0D, 64.0D, -5.0D, 0L, 4);
        check(segments.size() == 4, "the shield orbit keeps all four plates");
        check(segments.stream().allMatch(segment -> Math.hypot(
                        segment.x() - 10.0D, segment.z() + 5.0D) <= 0.85D),
                "all four shields stay within one block of the boss body");
        for (int index = 0; index < segments.size(); index++) {
            ShieldOrbitPolicy.Segment first = segments.get(index);
            ShieldOrbitPolicy.Segment next = segments.get((index + 1) % segments.size());
            check(Math.hypot(first.x() - next.x(), first.z() - next.z()) > 1.0D,
                    "adjacent full-size shield plates must not overlap");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
