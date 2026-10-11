package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PrisonerTargetPreviewCacheTest {
    @Test
    void reusesOnePreviewForHudAndEntityOutlineCallsInTheSameWorldTick() {
        PrisonerTargetPreviewCache cache = new PrisonerTargetPreviewCache();
        Object level = new Object();
        UUID player = UUID.randomUUID();
        AtomicInteger queries = new AtomicInteger();
        PrisonerTargetSelector.Preview expected = new PrisonerTargetSelector.Preview(null, false, false);

        PrisonerTargetSelector.Preview hud = cache.getOrCompute(level, player, 20L, () -> {
            queries.incrementAndGet();
            return expected;
        });
        PrisonerTargetSelector.Preview outline = cache.getOrCompute(level, player, 20L, () -> {
            queries.incrementAndGet();
            return new PrisonerTargetSelector.Preview(null, true, false);
        });

        assertSame(expected, hud);
        assertSame(hud, outline);
        assertEquals(1, queries.get());
    }

    @Test
    void recomputesAfterTickWorldOrPlayerChangesAndAfterExplicitInvalidation() {
        PrisonerTargetPreviewCache cache = new PrisonerTargetPreviewCache();
        Object firstLevel = new Object();
        Object secondLevel = new Object();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        AtomicInteger queries = new AtomicInteger();

        cache.getOrCompute(firstLevel, firstPlayer, 20L, () -> preview(queries));
        cache.getOrCompute(firstLevel, firstPlayer, 21L, () -> preview(queries));
        cache.getOrCompute(secondLevel, firstPlayer, 21L, () -> preview(queries));
        cache.getOrCompute(secondLevel, secondPlayer, 21L, () -> preview(queries));
        cache.invalidate();
        cache.getOrCompute(secondLevel, secondPlayer, 21L, () -> preview(queries));

        assertEquals(5, queries.get());
    }

    private static PrisonerTargetSelector.Preview preview(AtomicInteger queries) {
        queries.incrementAndGet();
        return new PrisonerTargetSelector.Preview(null, false, false);
    }
}
