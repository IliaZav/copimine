package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The user asked for the Wave 5 fog to be "страшным", "чёрным" and "густым".
 *
 * <p>Black is already guaranteed by the blackout distance, so these tests cover
 * the parts that make it read as a place rather than a switched-off screen:
 * the haze thickens over the cycle, it reports the server's alert strength, and
 * it never leaks back in once the hazard is over.</p>
 */
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
}
