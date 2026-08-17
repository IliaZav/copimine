import me.copimine.artifacts.api.EventArtifactRewardService;
import me.copimine.artifacts.api.EventArtifactRewardRequest;
import me.copimine.artifacts.api.RewardIssueResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ArtifactRewardServiceContractTest {
    public static void main(String[] args) {
        EventArtifactRewardRequest request = EventArtifactRewardRequest.toPlayer(
                "event-1",
                "end-event:event-1:participant:" + UUID.randomUUID(),
                "rift_core_shard",
                UUID.randomUUID(),
                "participant");
        if (!"event-1".equals(request.eventId())
                || !"rift_core_shard".equals(request.itemId())
                || request.recipientUuid() == null
                || request.worldDrop()) {
            throw new AssertionError("event reward request must preserve recipient and idempotency data");
        }
        if (EventArtifactRewardService.class.getDeclaredMethods().length < 2) {
            throw new AssertionError("Artifacts must expose typed player and world-drop reward methods");
        }
        RewardIssueResult result = RewardIssueResult.pending(request.idempotencyKey(), "PENDING_DELIVERY");
        if (!result.pending() || result.delivered() || result.idempotencyKey().isBlank()) {
            throw new AssertionError("reward result must report durable pending delivery without claiming success");
        }
        System.out.println("ArtifactRewardServiceContractTest OK");
    }
}
