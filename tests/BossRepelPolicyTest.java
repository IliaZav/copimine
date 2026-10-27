import me.copimine.endevent.domain.BossRepelPolicy;

/**
 * Canon: the 50% repel is a controlled arena-wide shove with a 3 s blind and a
 * 5 s slow, and it must never launch a player into the void.
 */
public final class BossRepelPolicyTest {
    public static void main(String[] args) {
        require(BossRepelPolicy.DARKNESS_TICKS == 3 * 20,
                "the blind must last the authored 3 s");
        require(BossRepelPolicy.SLOWNESS_TICKS == 5 * 20,
                "the slow must last the authored 5 s");
        require(BossRepelPolicy.SLOWNESS_AMPLIFIER >= 1,
                "the slow must be felt, not a cosmetic marker");

        double[] candidates = BossRepelPolicy.travelCandidates();
        require(candidates.length == 2, "the shove tests a full and a shortened landing spot");
        require(candidates[0] == BossRepelPolicy.FULL_DISTANCE
                        && candidates[1] == BossRepelPolicy.SHORT_DISTANCE,
                "candidates are ordered farthest first so the full shove is preferred");
        require(candidates[0] > candidates[1] && candidates[1] >= 1.0D,
                "the shove stays a short bounded displacement");
        require(BossRepelPolicy.IMPULSE > 0.0D && BossRepelPolicy.IMPULSE <= 2.0D,
                "the impulse is bounded and readable");
        require(BossRepelPolicy.LIFT > 0.0D && BossRepelPolicy.LIFT <= 1.0D,
                "the lift stays small so nobody is thrown high");

        System.out.println("BossRepelPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}