package me.copimine.artifacts.api;

import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;

/**
 * Typed, event-only boundary for durable rewards owned by CopiMineArtifacts.
 *
 * <p>Callers never receive a raw ItemStack and never write the artifact tables
 * themselves.  The implementation owns catalog authentication, the durable
 * idempotency record, the owner-bound instance and pending-delivery recovery.</p>
 */
public interface EventArtifactRewardService {
    CompletableFuture<RewardIssueResult> issueToPlayer(EventArtifactRewardRequest request);

    CompletableFuture<RewardIssueResult> issueWorldDrop(
            EventArtifactRewardRequest request, Location location);
}
