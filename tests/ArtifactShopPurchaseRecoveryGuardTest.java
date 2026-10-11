import me.copimine.artifacts.ArtifactShopPurchaseRecoveryGuard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class ArtifactShopPurchaseRecoveryGuardTest {
    public static void main(String[] args) {
        checksInFlightStateBeforePersistedPurchaseState();
        tracksPurchasesIndependently();
        reusesTheNormalRefundKeyForDeferredRecovery();
        rejectsInvalidPurchaseIds();
        System.out.println("ArtifactShopPurchaseRecoveryGuardTest passed.");
    }

    private static void checksInFlightStateBeforePersistedPurchaseState() {
        ArtifactShopPurchaseRecoveryGuard guard = new ArtifactShopPurchaseRecoveryGuard();
        String purchaseId = "purchase-racing-with-recovery";

        guard.begin(purchaseId);
        check(guard.isPurchaseInFlight(purchaseId),
                "recovery must defer a transfer while its purchase flow is active");

        guard.finish(purchaseId);
        check(!guard.isPurchaseInFlight(purchaseId),
                "the database lookup can start only after the live purchase flow finishes");
        check(ArtifactShopPurchaseRecoveryGuard.decisionAfterPersistenceLookup(true) == ArtifactShopPurchaseRecoveryGuard.RecoveryDecision.SKIP,
                "a persisted purchase must be kept after the live flow finishes");
        check(ArtifactShopPurchaseRecoveryGuard.decisionAfterPersistenceLookup(false) == ArtifactShopPurchaseRecoveryGuard.RecoveryDecision.REFUND,
                "an unpersisted purchase may be refunded after the live flow ends");
    }

    private static void tracksPurchasesIndependently() {
        ArtifactShopPurchaseRecoveryGuard guard = new ArtifactShopPurchaseRecoveryGuard();
        guard.begin("purchase-a");
        guard.begin("purchase-b");

        guard.finish("purchase-a");

        check(!guard.isPurchaseInFlight("purchase-a"),
                "completed purchase must leave the in-flight set");
        check(guard.isPurchaseInFlight("purchase-b"),
                "finishing one purchase must not release another");
    }

    private static void reusesTheNormalRefundKeyForDeferredRecovery() {
        String purchaseId = "purchase-refund-once";
        String normalRollbackKey = refundKey(purchaseId);
        String deferredRecoveryKey = refundKey(purchaseId);

        check("artifact-refund-purchase-refund-once".equals(normalRollbackKey),
                "normal purchase rollback must use its stable refund idempotency key");
        check(normalRollbackKey.equals(deferredRecoveryKey),
                "a queued recovery retry must resolve to the same key and cannot credit twice");
    }

    private static String refundKey(String purchaseId) {
        try {
            Method method = ArtifactShopPurchaseRecoveryGuard.class.getMethod("refundIdempotencyKey", String.class);
            return (String) method.invoke(null, purchaseId);
        } catch (NoSuchMethodException error) {
            throw new AssertionError("purchase rollback and recovery need a shared refund-key function", error);
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new AssertionError("refund-key function could not be called", error);
        }
    }

    private static void rejectsInvalidPurchaseIds() {
        ArtifactShopPurchaseRecoveryGuard guard = new ArtifactShopPurchaseRecoveryGuard();
        expectInvalid(() -> guard.begin(" "));
        expectInvalid(() -> guard.isPurchaseInFlight(null));
    }

    private static void expectInvalid(Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "blank or missing purchase ids must be rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
