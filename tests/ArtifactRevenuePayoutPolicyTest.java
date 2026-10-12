import me.copimine.artifacts.ArtifactRevenuePayoutPolicy;

public final class ArtifactRevenuePayoutPolicyTest {
    public static void main(String[] args) {
        reconcilesDirectTransfersInsteadOfCreditingAgain();
        reviewsPendingRowsWithoutARecordedTransfer();
        ignoresRowsThatAreAlreadySettled();
        refundsOnlyDefinitivePreCommitLimitRejections();
        defersOrphanRefundsDuringThePersistenceGracePeriod();
        System.out.println("ArtifactRevenuePayoutPolicyTest passed.");
    }

    private static void reconcilesDirectTransfersInsteadOfCreditingAgain() {
        check(ArtifactRevenuePayoutPolicy.pendingRowAction("PENDING", "tx-shop-1")
                        == ArtifactRevenuePayoutPolicy.PendingRowAction.MARK_CREDITED,
                "a persisted direct transfer must be reconciled by its existing bank transaction ID");
    }

    private static void reviewsPendingRowsWithoutARecordedTransfer() {
        check(ArtifactRevenuePayoutPolicy.pendingRowAction("PENDING", " ")
                        == ArtifactRevenuePayoutPolicy.PendingRowAction.MANUAL_REVIEW,
                "a legacy pending payout without a transaction ID must not mint money");
    }

    private static void ignoresRowsThatAreAlreadySettled() {
        check(ArtifactRevenuePayoutPolicy.pendingRowAction("CREDITED", "tx-shop-2")
                        == ArtifactRevenuePayoutPolicy.PendingRowAction.IGNORE,
                "settled payout rows must not be processed again");
    }

    private static void refundsOnlyDefinitivePreCommitLimitRejections() {
        check(ArtifactRevenuePayoutPolicy.shouldRefundAfterPersistenceFailure("ARTIFACT_LIMIT_SUPPLY"),
                "the supply-limit rejection is known to happen before a purchase can commit");
        check(ArtifactRevenuePayoutPolicy.shouldRefundAfterPersistenceFailure(" ARTIFACT_LIMIT_PLAYER "),
                "the player-limit rejection is known to happen before a purchase can commit");
        check(!ArtifactRevenuePayoutPolicy.shouldRefundAfterPersistenceFailure("08006"),
                "a connection failure has an ambiguous commit outcome and must not trigger an immediate refund");
        check(!ArtifactRevenuePayoutPolicy.shouldRefundAfterPersistenceFailure("duplicate key value violates unique constraint"),
                "a generic SQL failure can occur after a successful commit acknowledgement was lost");
    }

    private static void defersOrphanRefundsDuringThePersistenceGracePeriod() {
        long nowEpochSeconds = 1_800_000_000L;
        long nowMillis = nowEpochSeconds * 1_000L;
        long recentTransferCreatedAtMillis = nowMillis - 119_999L;
        check(ArtifactRevenuePayoutPolicy.isWithinOrphanRefundGracePeriod(
                        recentTransferCreatedAtMillis, nowEpochSeconds),
                "recent transfers must wait while an ambiguous persistence transaction settles");
        check(ArtifactRevenuePayoutPolicy.isWithinOrphanRefundGracePeriod(nowMillis + 1L, nowEpochSeconds),
                "clock skew into the future must defer automatic refund");
        check(!ArtifactRevenuePayoutPolicy.isWithinOrphanRefundGracePeriod(
                        nowMillis - ArtifactRevenuePayoutPolicy.ORPHAN_REFUND_GRACE_PERIOD_MILLIS,
                        nowEpochSeconds),
                "an orphan becomes eligible for durable reconciliation after the bounded grace period");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
