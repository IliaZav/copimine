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
        long[] six = new long[6];
        for (int index = 0; index < six.length; index++) {
            six[index] = RiftObeliskTimingPolicy.firstFireTick(100L, 80, index, 6);
        }
        for (int index = 1; index < six.length; index++) {
            check(six[index] > six[index - 1], "six obelisks need six unique first-shot slots");
        }
        boolean rejected = false;
        try {
            RiftObeliskTimingPolicy.firstFireTick(100L, 80, 6, 6);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "an out-of-profile obelisk index must fail closed");
        boolean invalidCountRejected = false;
        try {
            RiftObeliskTimingPolicy.firstFireTick(100L, 80, 0, 7);
        } catch (IllegalArgumentException expected) {
            invalidCountRejected = true;
        }
        check(invalidCountRejected, "a count above the physical W4 cap must fail closed");
        boolean zeroCountRejected = false;
        try {
            RiftObeliskTimingPolicy.staggerTicks(80, 0);
        } catch (IllegalArgumentException expected) {
            zeroCountRejected = true;
        }
        check(zeroCountRejected, "a zero-obelisk timing profile must fail closed");
        System.out.println("RiftObeliskTimingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
