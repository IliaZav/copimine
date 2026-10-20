package me.copimine.artifacts;

/** Policies for reconciling shop transfers that already credit their recipient. */
public final class ArtifactRevenuePayoutPolicy {
    public static final long ORPHAN_REFUND_GRACE_PERIOD_MILLIS = 120_000L;

    private ArtifactRevenuePayoutPolicy() {
    }

    public static PendingRowAction pendingRowAction(String status, String bankTransactionId) {
        if (status == null || !"PENDING".equalsIgnoreCase(status.trim())) {
            return PendingRowAction.IGNORE;
        }
        if (bankTransactionId != null && !bankTransactionId.isBlank()) {
            return PendingRowAction.MARK_CREDITED;
        }
        return PendingRowAction.MANUAL_REVIEW;
    }

    /** Only explicit quota rejections are known to happen before purchase persistence can commit. */
    public static boolean shouldRefundAfterPersistenceFailure(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        String normalized = errorMessage.trim();
        return "ARTIFACT_LIMIT_SUPPLY".equalsIgnoreCase(normalized)
                || "ARTIFACT_LIMIT_PLAYER".equalsIgnoreCase(normalized);
    }

    /**
     * Transfer timestamps are stored in epoch milliseconds, while the plugin's
     * shared {@code now()} helper returns epoch seconds.
     */
    public static boolean isWithinOrphanRefundGracePeriod(long transferCreatedAtMillis, long nowEpochSeconds) {
        if (nowEpochSeconds < 0L || nowEpochSeconds > Long.MAX_VALUE / 1_000L) {
            return true;
        }
        long nowMillis = nowEpochSeconds * 1_000L;
        if (transferCreatedAtMillis > nowMillis) {
            return true;
        }
        long age;
        try {
            age = Math.subtractExact(nowMillis, transferCreatedAtMillis);
        } catch (ArithmeticException overflow) {
            return true;
        }
        return age < ORPHAN_REFUND_GRACE_PERIOD_MILLIS;
    }

    public static String purchasePersistenceLockKey(String purchaseId) {
        if (purchaseId == null || purchaseId.isBlank()) {
            throw new IllegalArgumentException("Purchase id must not be null or blank.");
        }
        return "artifact-purchase-persistence:" + purchaseId;
    }

    public enum PendingRowAction {
        IGNORE,
        MARK_CREDITED,
        MANUAL_REVIEW
    }
}
