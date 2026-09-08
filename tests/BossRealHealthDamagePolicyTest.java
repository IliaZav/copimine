import me.copimine.endevent.domain.BossCastState;
import me.copimine.endevent.domain.BossRealHealthDamagePolicy;

public final class BossRealHealthDamagePolicyTest {
    public static void main(String[] args) {
        var first = BossRealHealthDamagePolicy.apply(5000, 10, BossCastState.NONE, 5000);
        var second = BossRealHealthDamagePolicy.apply(first.remainingHealth(), 14, BossCastState.NONE, 5000);
        var third = BossRealHealthDamagePolicy.apply(second.remainingHealth(), 8, BossCastState.NONE, 5000);
        check(third.remainingHealth() == 4968, "three same-tick hits must sum on real HP");
        var exhausted = BossRealHealthDamagePolicy.apply(100, 10, BossCastState.EXHAUSTED, 5000);
        check(exhausted.appliedDamage() == 15, "EXHAUSTED multiplier must apply exactly once");
        var lethal = BossRealHealthDamagePolicy.apply(5, 10, BossCastState.NONE, 5000);
        check(lethal.lethal() && lethal.remainingHealth() == 0, "lethal real-health transaction must clamp to zero");
        System.out.println("BossRealHealthDamagePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
