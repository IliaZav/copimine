import java.util.List;
import java.util.Map;
import me.copimine.artifacts.OrphanShopTransferRecoveryState;

public final class OrphanShopTransferRecoveryStateTest {
    public static void main(String[] args) {
        pagesAdvancePastPermanentBadRowsAndRetryableRefundFailures();
        findsRefundFailuresCreatedAfterTheFirstScan();
        findsNewTransfersWhileAnOlderRefundIsStillPending();
        findsNormalTransfersAfterFutureTimestampRowsAndClockRollback();
        rejectsUnorderedKeysetPages();
        System.out.println("OrphanShopTransferRecoveryStateTest passed.");
    }

    private static void pagesAdvancePastPermanentBadRowsAndRetryableRefundFailures() {
        OrphanShopTransferRecoveryState<String> state = new OrphanShopTransferRecoveryState<>();
        state.recordFetchedPage(List.of(
                new OrphanShopTransferRecoveryState.Cursor(10L, "a-poison-row"),
                new OrphanShopTransferRecoveryState.Cursor(10L, "b-poison-row")),
                2);
        check(!state.isScanComplete(), "a full page must continue to the next keyset page");
        check(state.cursor().equals(new OrphanShopTransferRecoveryState.Cursor(10L, "b-poison-row")),
                "the cursor must advance across rows before row validation");

        state.defer("transient-refund", "valid transfer that temporarily failed");
        state.recordFetchedPage(List.of(new OrphanShopTransferRecoveryState.Cursor(11L, "valid-later-row")), 2);
        check(state.isScanComplete(), "a short page must complete the scan");
        check(state.cursor().transactionId().equals("valid-later-row"), "the later row must be reached");
        Map<String, String> retries = state.pendingSnapshot();
        check(retries.size() == 1 && retries.containsKey("transient-refund"),
                "transient failures must remain scheduled after scanning later pages");
        boolean immutable = false;
        try {
            retries.clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "retry snapshots must not expose mutable scheduler state");
        check(!state.isComplete(), "the scan must remain active while a refund is pending");
        state.resolved("transient-refund");
        check(state.isComplete(), "a resolved final refund must finish the scan");
    }

    private static void rejectsUnorderedKeysetPages() {
        OrphanShopTransferRecoveryState<Object> state = new OrphanShopTransferRecoveryState<>();
        boolean rejected = false;
        try {
            state.recordFetchedPage(List.of(
                    new OrphanShopTransferRecoveryState.Cursor(10L, "z"),
                    new OrphanShopTransferRecoveryState.Cursor(10L, "a")), 2);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "unordered keyset pages must not move the cursor backwards");
    }

    private static void findsRefundFailuresCreatedAfterTheFirstScan() {
        OrphanShopTransferRecoveryState<String> state = new OrphanShopTransferRecoveryState<>();
        state.recordFetchedPage(List.of(new OrphanShopTransferRecoveryState.Cursor(1_000_000L, "z-existing")), 128);
        check(state.isComplete(), "a short page must complete the first scan");

        state.beginNextScan(1_000_000L, 60_000L);
        check(state.cursor().equals(new OrphanShopTransferRecoveryState.Cursor(940_000L, "")),
                "the next scan must overlap its cursor window to include equal-time transfers");
        OrphanShopTransferRecoveryState.Cursor transferCreatedAfterScan =
                new OrphanShopTransferRecoveryState.Cursor(1_000_500L, "a-later-refund-failure");
        state.recordFetchedPage(List.of(transferCreatedAfterScan), 128);
        state.defer(transferCreatedAfterScan.transactionId(), "later transient refund failure");
        check(!state.isComplete(), "a refund failure discovered by a later scan must remain pending");
        check(state.pendingSnapshot().containsKey("a-later-refund-failure"),
                "the later failed refund must be scheduled for retry");

        state.resolved("a-later-refund-failure");
        check(state.isComplete(), "the recurring scan must complete after its deferred refund succeeds");
    }

    private static void findsNewTransfersWhileAnOlderRefundIsStillPending() {
        OrphanShopTransferRecoveryState<String> state = new OrphanShopTransferRecoveryState<>();
        state.recordFetchedPage(List.of(new OrphanShopTransferRecoveryState.Cursor(2_000_000L, "old-transfer")), 128);
        state.defer("old-transfer", "refund still unavailable");
        check(state.isScanComplete() && !state.isComplete(), "the old refund must remain pending after its scan completes");

        state.beginNextScan(2_000_000L, 60_000L);
        check(state.pendingSnapshot().containsKey("old-transfer"),
                "starting another keyset scan must preserve older retryable refunds");
        OrphanShopTransferRecoveryState.Cursor later = new OrphanShopTransferRecoveryState.Cursor(2_001_000L, "later-transfer");
        state.recordFetchedPage(List.of(later), 128);
        state.defer(later.transactionId(), "new refund must also be retried");
        check(state.pendingSnapshot().size() == 2,
                "a later scan must discover new orphan transfers while an older refund remains pending");

        state.resolved("old-transfer");
        state.resolved("later-transfer");
        check(state.isComplete(), "all deferred refunds should resolve independently of scan cycles");
    }

    private static void findsNormalTransfersAfterFutureTimestampRowsAndClockRollback() {
        OrphanShopTransferRecoveryState<String> state = new OrphanShopTransferRecoveryState<>();
        long futureTimestamp = 10_000_000L;
        state.recordFetchedPage(List.of(new OrphanShopTransferRecoveryState.Cursor(futureTimestamp, "clock-skew-row")), 128);
        check(state.isScanComplete(), "a future-dated transfer must not keep the initial scan open");

        state.beginNextScan(2_000_000L, 60_000L);
        check(state.cursor().equals(new OrphanShopTransferRecoveryState.Cursor(1_940_000L, "")),
                "the next scan must cap a future cursor at current time before applying replay overlap");

        OrphanShopTransferRecoveryState.Cursor laterNormalTransfer =
                new OrphanShopTransferRecoveryState.Cursor(2_000_001L, "normal-transfer-after-clock-rollback");
        state.recordFetchedPage(List.of(laterNormalTransfer), 128);
        check(state.cursor().equals(laterNormalTransfer),
                "a later ordinary transfer must be discoverable after a future timestamp and clock rollback");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
