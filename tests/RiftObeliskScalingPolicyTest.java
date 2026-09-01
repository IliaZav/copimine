import me.copimine.endevent.domain.RiftObeliskScalingPolicy;

public final class RiftObeliskScalingPolicyTest {
    public static void main(String[] args) {
        int[][] cases = {{0, 0}, {1, 0}, {2, 1}, {5, 1}, {6, 2}, {10, 2},
                {11, 3}, {15, 3}, {16, 4}, {20, 4}, {21, 4}, {100, 4}};
        for (int[] test : cases) {
            check(RiftObeliskScalingPolicy.countForPlayers(test[0]) == test[1],
                    "players=" + test[0] + " expected=" + test[1]);
        }
        check(RiftObeliskScalingPolicy.MAX_ACTIVE == 4, "hard active obelisk cap must be four");
        System.out.println("RiftObeliskScalingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
