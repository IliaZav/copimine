import me.copimine.endevent.domain.RiftFireballCollisionPolicy;

public final class RiftFireballCollisionPolicyTest {
    public static void main(String[] args) {
        testSweptPathHitsTheObeliskWhenFiveTickSamplingWouldTunnel();
        testMissOutsideHorizontalRadius();
        testMissOutsideVerticalBounds();
        testReturnPathReachesRaisedCrownSocket();
        testZeroLengthPathUsesCurrentPoint();
        System.out.println("RiftFireballCollisionPolicyTest OK");
    }

    private static void testSweptPathHitsTheObeliskWhenFiveTickSamplingWouldTunnel() {
        check(RiftFireballCollisionPolicy.segmentIntersectsObelisk(
                        0.0D, 70.0D, 0.0D,
                        6.0D, 70.0D, 0.0D,
                        3.0D, 70.0D, 0.0D,
                        1.25D, 68.0D, 72.0D),
                "a fast reflected projectile crossing the obelisk must be detected");
    }

    private static void testMissOutsideHorizontalRadius() {
        check(!RiftFireballCollisionPolicy.segmentIntersectsObelisk(
                        0.0D, 70.0D, 4.0D,
                        6.0D, 70.0D, 4.0D,
                        3.0D, 70.0D, 0.0D,
                        1.25D, 68.0D, 72.0D),
                "a path outside the obelisk radius must not consume a hit");
    }

    private static void testMissOutsideVerticalBounds() {
        check(!RiftFireballCollisionPolicy.segmentIntersectsObelisk(
                        0.0D, 75.0D, 0.0D,
                        6.0D, 75.0D, 0.0D,
                        3.0D, 70.0D, 0.0D,
                        1.25D, 68.0D, 72.0D),
                "a path above the physical obelisk must not consume a hit");
    }

    private static void testZeroLengthPathUsesCurrentPoint() {
        check(RiftFireballCollisionPolicy.segmentIntersectsObelisk(
                        3.0D, 70.0D, 0.0D,
                        3.0D, 70.0D, 0.0D,
                        3.0D, 70.0D, 0.0D,
                        1.25D, 68.0D, 72.0D),
                "a point inside the obelisk bounds must be a hit");
    }

    private static void testReturnPathReachesRaisedCrownSocket() {
        check(RiftFireballCollisionPolicy.segmentIntersectsObelisk(
                        8.5D, 69.0D, -46.0D,
                        17.5D, 73.8D, -38.5D,
                        17.5D, 68.0D, -38.5D,
                        2.2D, 67.5D, 74.25D),
                "a reflected path must still reach the authored crown launch socket");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
