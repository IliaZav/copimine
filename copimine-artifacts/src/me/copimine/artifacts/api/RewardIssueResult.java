package me.copimine.artifacts.api;

/** Honest outcome of a durable event reward request. */
public record RewardIssueResult(
        String idempotencyKey,
        String status,
        String uniqueItemId,
        String message) {

    public RewardIssueResult {
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
        status = status == null ? "REJECTED" : status;
        uniqueItemId = uniqueItemId == null ? "" : uniqueItemId;
        message = message == null ? "" : message;
    }

    public static RewardIssueResult pending(String idempotencyKey, String status) {
        return pending(idempotencyKey, status, "");
    }

    public static RewardIssueResult pending(
            String idempotencyKey, String status, String uniqueItemId) {
        return new RewardIssueResult(idempotencyKey, status, uniqueItemId,
                "Reward is durably queued");
    }

    public static RewardIssueResult delivered(
            String idempotencyKey, String uniqueItemId, String message) {
        return new RewardIssueResult(idempotencyKey, "DELIVERED", uniqueItemId, message);
    }

    public static RewardIssueResult alreadyIssued(
            String idempotencyKey, String uniqueItemId, String status) {
        return new RewardIssueResult(idempotencyKey, status, uniqueItemId,
                "Reward idempotency key was already recorded");
    }

    public static RewardIssueResult rejected(String idempotencyKey, String message) {
        return new RewardIssueResult(idempotencyKey, "REJECTED", "", message);
    }

    public boolean pending() {
        return "PENDING_DELIVERY".equals(status) || "WORLD_PENDING".equals(status);
    }

    public boolean delivered() {
        return "DELIVERED".equals(status);
    }

    public boolean accepted() {
        return pending() || delivered() || "ALREADY_ISSUED".equals(status);
    }
}
