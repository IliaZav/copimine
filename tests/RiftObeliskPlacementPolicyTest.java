import java.util.HashSet;
import me.copimine.endevent.domain.RiftObeliskPlacementPolicy;

public final class RiftObeliskPlacementPolicyTest {
    public static void main(String[] args) {
        for (int count = 1; count <= 4; count++) {
            var points = RiftObeliskPlacementPolicy.candidates(count);
            check(points.size() == count, "candidate count must match requested count");
            check(new HashSet<>(points).size() == count, "candidates must not overlap");
            for (var point : points) {
                check(point.horizontalDistance() >= RiftObeliskPlacementPolicy.MIN_CORE_DISTANCE,
                        "candidate must not overlap core");
                check(point.horizontalDistance() <= RiftObeliskPlacementPolicy.MAX_ARENA_RADIUS,
                        "candidate must be inside arena radius");
            }
        }
        System.out.println("RiftObeliskPlacementPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
