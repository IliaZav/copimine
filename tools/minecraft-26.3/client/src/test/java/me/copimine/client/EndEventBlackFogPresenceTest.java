package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Server lease progress remains separate from the smooth rendering fade. */
class EndEventBlackFogPresenceTest {
    private static BridgePayload packet(long seq, long timestamp, float intensity) {
        return new BridgePayload("END_EVENT:END_FOG_STATE", ClientBridgeProtocol.PROTOCOL_VERSION,
                seq, timestamp, "session-fog", "wave5-black-fog", false, false, false, false,
                java.util.Set.of(), "", "WAVE5_FOG_V1", 8_000, intensity, 0, 0,
                "the_end|wave5|1", "", "", "", "ACTIVE");
    }

    @Test
    void aRunningCycleReportsItsProgressFromZeroToOne() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet(1L, 1_000L, 0.9F), 1_000L));

        float early = manager.cycleProgress(1_100L);
        float late = manager.cycleProgress(8_900L);
        assertTrue(early >= 0.0F && early < 0.5F, "a fresh cycle must not start maximally thick");
        assertTrue(late > early, "the haze thickens as the cycle runs");
        assertEquals(1.0F, manager.cycleProgress(9_500L), 0.0001F,
                "past the deadline the cycle is fully elapsed");
    }

    @Test
    void anInactiveManagerReportsNoPressure() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertEquals(0.0F, manager.cycleProgress(5_000L), 0.0001F);
        assertEquals(0.0F, manager.intensity(), 0.0001F);
    }

    @Test
    void theServerAlertStrengthSurvivesForTheCycle() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet(1L, 1_000L, 0.62F), 1_000L));
        assertEquals(0.62F, manager.intensity(), 0.0001F);
        assertTrue(manager.active());
    }

    @Test
    void progressNeverExceedsOneEvenLongPastTheDeadline() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet(1L, 1_000L, 1.0F), 1_000L));
        float progress = manager.cycleProgress(1_000_000L);
        assertTrue(progress >= 0.0F && progress <= 1.0F);
    }

    @Test
    void repeatedLeaseDoesNotRestartOnsetOrProgress() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet(4L, 1_000L, 0.9F), 1_000L));
        assertEquals(0.0F, manager.fadeStrength("the_end", 1_000L));
        float quarter = manager.fadeStrength("the_end", 1_312L);
        assertTrue(quarter > 0.0F && quarter < 0.5F);
        assertEquals(1.0F, manager.fadeStrength("the_end", 2_250L));
        float progress = manager.cycleProgress(3_000L);
        assertTrue(manager.apply(packet(4L, 3_000L, 0.9F), 3_000L));
        assertEquals(1.0F, manager.fadeStrength("the_end", 3_000L));
        assertEquals(progress, manager.cycleProgress(3_000L));
    }

    @Test
    void lateRefreshPreservesTheExpiryFadeWithoutAFlash() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet(4L, 1_000L, 0.9F), 1_000L));
        float tail = manager.fadeStrength("the_end", 9_250L);
        assertTrue(tail > 0.8F && tail < 1.0F);
        assertTrue(manager.apply(packet(4L, 9_250L, 0.9F), 9_250L));
        assertEquals(tail, manager.fadeStrength("the_end", 9_250L));
        assertTrue(manager.fadeStrength("the_end", 9_500L) >= tail);
        assertEquals(1.0F, manager.cycleProgress(9_500L));
    }

    @Test
    void localExitClearsImmediatelyAndRequiresAuthoritativeResume() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet(4L, 1_000L, 0.9F), 1_000L));
        manager.clear();
        assertEquals(0.0F, manager.fadeStrength("the_end", 2_500L));
        org.junit.jupiter.api.Assertions.assertFalse(manager.apply(packet(4L, 3_000L, 0.9F), 3_000L));
        assertTrue(manager.resumeAfterLocalExit("session-fog", 4L, 4_000L));
        assertTrue(manager.apply(packet(4L, 4_000L, 0.9F), 4_000L));
        assertEquals(0.0F, manager.fadeStrength("the_end", 4_000L));
        assertEquals(1.0F, manager.fadeStrength("the_end", 5_250L));
        manager.reset();
        assertEquals(0.0F, manager.fadeStrength("the_end", 5_500L));
    }
}
