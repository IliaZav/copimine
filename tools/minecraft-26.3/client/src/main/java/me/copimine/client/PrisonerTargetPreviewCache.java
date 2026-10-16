package me.copimine.client;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Shares one local entity-ray query across HUD and outline hooks during a world tick. */
final class PrisonerTargetPreviewCache {
    private Object level;
    private UUID playerId;
    private long gameTime = Long.MIN_VALUE;
    private PrisonerTargetSelector.Preview preview;

    PrisonerTargetSelector.Preview getOrCompute(
            Object currentLevel,
            UUID currentPlayerId,
            long currentGameTime,
            Supplier<PrisonerTargetSelector.Preview> compute
    ) {
        Objects.requireNonNull(currentLevel, "currentLevel");
        Objects.requireNonNull(currentPlayerId, "currentPlayerId");
        Objects.requireNonNull(compute, "compute");
        if (preview != null && level == currentLevel && playerId.equals(currentPlayerId)
                && gameTime == currentGameTime) {
            return preview;
        }

        invalidate();
        PrisonerTargetSelector.Preview next = compute.get();
        if (next == null) {
            next = new PrisonerTargetSelector.Preview(null, false, false);
        }
        level = currentLevel;
        playerId = currentPlayerId;
        gameTime = currentGameTime;
        preview = next;
        return next;
    }

    void invalidate() {
        level = null;
        playerId = null;
        gameTime = Long.MIN_VALUE;
        preview = null;
    }
}
