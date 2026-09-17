import me.copimine.endevent.domain.BossHitboxProfile;
import me.copimine.endevent.domain.BossHitboxProxyReconciliationPolicy;

import java.util.LinkedHashSet;
import java.util.Set;

public final class BossHitboxProxyReconciliationPolicyTest {
    public static void main(String[] args) {
        Set<BossHitboxProxyReconciliationPolicy.Key> expected =
                BossHitboxProxyReconciliationPolicy.expectedKeys(BossHitboxProfile.canonical());
        Set<BossHitboxProxyReconciliationPolicy.Key> live = new LinkedHashSet<>(expected);
        live.remove(new BossHitboxProxyReconciliationPolicy.Key(
                BossHitboxProfile.PartId.RIGHT_FOREARM, 0));

        BossHitboxProxyReconciliationPolicy.Result result =
                BossHitboxProxyReconciliationPolicy.reconcile(expected, live);
        require(result.missing().contains(new BossHitboxProxyReconciliationPolicy.Key(
                        BossHitboxProfile.PartId.RIGHT_FOREARM, 0)),
                "missing right forearm must be reported");
        require(result.stale().isEmpty(), "valid survivors must not be marked stale");
        require(result.missing().size() == 1, "only the removed proxy should be missing");
        require(result.requiresRebuild() && !result.isHealthy(),
                "a non-healthy rig must request reconciliation");

        Set<BossHitboxProxyReconciliationPolicy.Key> stale = new LinkedHashSet<>(live);
        stale.add(new BossHitboxProxyReconciliationPolicy.Key(
                BossHitboxProfile.PartId.RIGHT_FOREARM, 7));
        result = BossHitboxProxyReconciliationPolicy.reconcile(expected, stale);
        require(result.stale().contains(new BossHitboxProxyReconciliationPolicy.Key(
                        BossHitboxProfile.PartId.RIGHT_FOREARM, 7)),
                "unexpected segment must be reported stale");
        require(!result.isHealthy(), "missing/stale keys cannot be healthy");
        System.out.println("BossHitboxProxyReconciliationPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
