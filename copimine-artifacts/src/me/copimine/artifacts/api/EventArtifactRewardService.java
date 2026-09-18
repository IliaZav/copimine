package me.copimine.artifacts.api;

import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

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

    /** Fail-closed identity check used when a vanilla resource is consumed. */
    boolean isOfficialArtifact(ItemStack stack);

    /** Player-context authenticity check for an event utility item. */
    boolean isAuthenticArtifact(ItemStack stack, org.bukkit.entity.Player player, String context);
}
