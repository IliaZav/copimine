import me.copimine.endevent.domain.V2BossHealthScalingPolicy;

public final class V2BossHealthScalingPolicyTest {
    public static void main(String[] args) {
        double[] expected = {
                5000, 6000, 7000, 8500, 9500, 10500, 11500, 12500, 13500,
                14500, 15000, 15500, 16000, 17500, 18000, 18500, 19000, 19500, 20000
        };
        for (int players = 2; players <= 20; players++) {
            check(V2BossHealthScalingPolicy.maxHealthFor(players) == expected[players - 2],
                    "exact V2 HP table entry missing for " + players);
        }
        check(V2BossHealthScalingPolicy.maxHealthFor(1) == 5000,
                "below minimum clamps to the two-player pool");
        check(V2BossHealthScalingPolicy.maxHealthFor(99) == 20000,
                "above maximum clamps to the twenty-player pool");
        System.out.println("V2BossHealthScalingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
