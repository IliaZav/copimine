import me.copimine.endevent.domain.BossCastPolicy;
import me.copimine.endevent.domain.BossCastState;

public final class BossCastPolicyTest {
    public static void main(String[] args) {
        check(!BossCastPolicy.damageAllowed(BossCastState.ABSORPTION_CHANNEL, 1_000L, 2_000L),
                "active absorption must block damage");
        check(BossCastPolicy.damageAllowed(BossCastState.ABSORPTION_CHANNEL, 2_001L, 2_000L),
                "expired absorption must allow damage");
        BossCastPolicy.Reconciled expired = BossCastPolicy.reconcile(
                BossCastState.ABSORPTION_CHANNEL, 2_001L, 2_000L);
        check(expired.state() == BossCastState.NONE && !expired.invulnerable() && expired.deadlineMillis() == 0L,
                "expired cast must be repaired to a damageable state");
        BossCastPolicy.Reconciled active = BossCastPolicy.reconcile(
                BossCastState.JUDGMENT_CAST, 1_000L, 2_000L);
        check(active.state() == BossCastState.JUDGMENT_CAST && active.invulnerable(),
                "active judgment must remain invulnerable only during its deadline");
        BossCastPolicy.Reconciled exhausted = BossCastPolicy.reconcile(
                BossCastState.EXHAUSTED, 1_000L, 2_000L);
        check(exhausted.state() == BossCastState.EXHAUSTED && !exhausted.invulnerable(),
                "exhausted boss must be damageable");
        check(BossCastPolicy.damageAllowed(BossCastState.ABSORPTION_CHANNEL, 1_000L, 0L),
                "missing deadline must fail open to damage rather than permanent immunity");
        System.out.println("BossCastPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
