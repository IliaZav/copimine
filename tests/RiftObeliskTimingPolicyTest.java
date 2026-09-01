import me.copimine.endevent.domain.RiftObeliskTimingPolicy;

public final class RiftObeliskTimingPolicyTest {
    public static void main(String[] args) {
        int first = (int) RiftObeliskTimingPolicy.firstFireTick(100L, 80, 0, 4);
        int second = (int) RiftObeliskTimingPolicy.firstFireTick(100L, 80, 1, 4);
        int fourth = (int) RiftObeliskTimingPolicy.firstFireTick(100L, 80, 3, 4);
        check(first == 180, "first obelisk must use the configured interval");
        check(second > first && fourth > second, "first shots must be staggered");
        check(RiftObeliskTimingPolicy.staggerTicks(1, 4) >= 5,
                "stagger must retain a safe minimum");
        System.out.println("RiftObeliskTimingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
