import me.copimine.endevent.domain.BossThresholdPolicy;

public final class BossThresholdPolicyTest {
    public static void main(String[] args) {
        BossThresholdPolicy.Decision half = BossThresholdPolicy.evaluate(
                600.0D, 150.0D, 1000.0D, 500.0D, 100.0D, 200.0D, false, false);
        check(half.triggerHalf(), "first projected crossing at 500 must trigger the half phase");
        check(!half.triggerFinal(), "half phase must not trigger final phase");
        check(close(half.appliedHealth(), 500.0D), "a crossing hit must clamp the boss at the half threshold");

        BossThresholdPolicy.Decision skip = BossThresholdPolicy.evaluate(
                510.0D, 1000.0D, 1000.0D, 500.0D, 100.0D, 200.0D, false, false);
        check(skip.triggerHalf() && !skip.triggerFinal(), "a lethal first hit must not skip the half phase");
        check(close(skip.appliedHealth(), 500.0D), "the first crossing must stop at 500 HP");

        BossThresholdPolicy.Decision lethal = BossThresholdPolicy.evaluate(
                130.0D, 500.0D, 1000.0D, 500.0D, 100.0D, 200.0D, true, false);
        check(lethal.triggerFinal(), "a lethal hit crossing 10 percent must trigger final phase");
        check(close(lethal.appliedHealth(), 200.0D), "final phase must clamp boss to exactly 200 HP");

        BossThresholdPolicy.Decision after = BossThresholdPolicy.evaluate(
                200.0D, 500.0D, 1000.0D, 500.0D, 100.0D, 200.0D, true, true);
        check(!after.triggerHalf() && !after.triggerFinal(), "threshold side effects must be exactly once");
        check(close(after.appliedHealth(), 200.0D), "invulnerable final boss health must not be lowered by policy");
        System.out.println("BossThresholdPolicyTest OK");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000001D;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
