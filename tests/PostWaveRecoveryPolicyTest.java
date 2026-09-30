import me.copimine.endevent.domain.PostWaveRecoveryPolicy;

public final class PostWaveRecoveryPolicyTest {
    public static void main(String[] args) {
        check(PostWaveRecoveryPolicy.repairAmount(100) == 30,
                "repair must use thirty percent of maximum durability");
        check(PostWaveRecoveryPolicy.repairAmount(3) == 1,
                "repair amount must round to the nearest durability point");
        check(PostWaveRecoveryPolicy.repairedDamage(75, 100) == 45,
                "current damage decreases by thirty percent of maximum durability");
        check(PostWaveRecoveryPolicy.repairedDamage(20, 100) == 0,
                "repair cannot make durability damage negative");
        check(PostWaveRecoveryPolicy.repairedDamage(4, 0) == 4,
                "non-damageable items are left untouched");
        check(PostWaveRecoveryPolicy.repairedDamage(-4, 100) == 0,
                "invalid negative damage is clamped safely");
        System.out.println("PostWaveRecoveryPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
