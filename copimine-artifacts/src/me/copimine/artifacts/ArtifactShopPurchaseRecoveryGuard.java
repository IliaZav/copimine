package me.copimine.artifacts;

import java.util.HashSet;
import java.util.Set;

/** Coordinates shop recovery with the live charge-to-persistence window. */
public final class ArtifactShopPurchaseRecoveryGuard {
    private final Set<String> inFlightPurchaseIds = new HashSet<>();

    /** Both immediate rollback and deferred recovery must replay the same bank refund. */
    public static String refundIdempotencyKey(String purchaseId) {
        return "artifact-refund-" + requirePurchaseId(purchaseId);
    }

    public synchronized void begin(String purchaseId) {
        String validId = requirePurchaseId(purchaseId);
        if (!inFlightPurchaseIds.add(validId)) {
            throw new IllegalStateException("Artifact shop purchase is already in flight.");
        }
    }

    public synchronized void finish(String purchaseId) {
        inFlightPurchaseIds.remove(requirePurchaseId(purchaseId));
    }

    /** Check this before querying persisted state so a concurrent purchase cannot race the lookup. */
    public synchronized boolean isPurchaseInFlight(String purchaseId) {
        String validId = requirePurchaseId(purchaseId);
        return inFlightPurchaseIds.contains(validId);
    }

    /** Resolve a database result only after {@link #isPurchaseInFlight(String)} returned false. */
    public static RecoveryDecision decisionAfterPersistenceLookup(boolean purchasePersisted) {
        return purchasePersisted ? RecoveryDecision.SKIP : RecoveryDecision.REFUND;
    }

    private static String requirePurchaseId(String purchaseId) {
        if (purchaseId == null || purchaseId.isBlank()) {
            throw new IllegalArgumentException("Purchase id must not be null or blank.");
        }
        return purchaseId;
    }

    public enum RecoveryDecision {
        SKIP,
        REFUND
    }
}
