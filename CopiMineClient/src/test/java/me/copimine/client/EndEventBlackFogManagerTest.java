package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventBlackFogManagerTest {
    @Test
    void leavingArenaSuspendsOneViewerWithoutClosingTheGlobalFogCycle() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("event-1", 4L, 100L, "the_end|wave5|1", .8F, "ACTIVE"), 100L));
        assertTrue(manager.apply(packet("event-1", 4L, 200L, "", 0F, "SUSPEND"), 200L));
        assertFalse(manager.active());
        assertFalse(manager.apply(packet("event-1", 4L, 250L, "the_end|wave5|1", .8F, "ACTIVE"), 250L));
        assertTrue(manager.resumeAfterLocalExit("event-1", 4L, 300L));
        assertTrue(manager.apply(packet("event-1", 4L, 300L, "the_end|wave5|1", .8F, "ACTIVE"), 300L));
        assertTrue(manager.active());
    }
    @Test
    void explicitServerResumeRestoresLiveFogAfterRespawnWithoutReopeningCompletedCycles() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("event-1", 4L, 100L, "the_end|wave5|1", .8F, "ACTIVE"), 100L));
        manager.clear();
        assertFalse(manager.resumeAfterLocalExit("event-1", 5L, 300L));
        assertFalse(manager.resumeAfterLocalExit("event-1", 4L, 90L));
        assertTrue(manager.resumeAfterLocalExit("event-1", 4L, 300L));
        assertFalse(manager.apply(packet("event-1", 4L, 200L, "the_end|wave5|1", .8F, "ACTIVE"), 400L));
        assertTrue(manager.apply(packet("event-1", 4L, 400L, "the_end|wave5|1", .8F, "ACTIVE"), 400L));
        assertTrue(manager.apply(packet("event-1", 4L, 500L, "", 0F, "CLEAR"), 500L));
        manager.clear();
        assertTrue(manager.resumeAfterLocalExit("event-1", 4L, 600L));
        assertFalse(manager.apply(packet("event-1", 4L, 700L, "the_end|wave5|1", .8F, "ACTIVE"), 700L));
    }
    @Test
    void appliesOnlyToTheMatchingDimensionAndExpiresWithoutARefresh() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();

        assertTrue(manager.apply(packet("event-1", 2L, 1_000L,
                "the_end|wave5|0", 0.9F, "ACTIVE"), 1_000L));
        assertEquals(12.0F, manager.fogEndBlocks("the_end", 1_100L), 0.001F);
        assertEquals(0.0F, manager.fogEndBlocks("overworld", 1_100L));
        assertEquals(0.0F, manager.fogEndBlocks("the_end", 9_001L));
        assertFalse(manager.active());
    }

    @Test
    void onlyAFreshServerRefreshCanExtendTheFogExpiry() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        BridgePayload original = packet("event-1", 2L, 1_000L,
                "the_end|wave5|0", 0.9F, "ACTIVE");
        assertTrue(manager.apply(original, 1_000L));
        assertFalse(manager.apply(original, 8_000L));
        assertEquals(0.0F, manager.fogEndBlocks("the_end", 9_000L));

        assertTrue(manager.apply(packet("event-1", 2L, 10_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 10_000L));
        assertTrue(manager.apply(packet("event-1", 2L, 12_000L,
                "the_end|wave5|0", 0.7F, "ACTIVE"), 12_000L));
        assertFalse(manager.apply(original, 19_000L));
        assertEquals(16.0F, manager.fogEndBlocks("the_end", 19_999L), 0.001F);
        assertEquals(0.0F, manager.fogEndBlocks("the_end", 20_000L));
    }

    @Test
    void rejectsStalePacketsAfterClearAndAcceptsANewGeneration() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("event-1", 4L, 1_000L,
                "the_end|wave5|1", 0.8F, "ACTIVE"), 1_000L));
        assertTrue(manager.apply(packet("event-1", 4L, 2_000L,
                "", 0.0F, "CLEAR"), 2_000L));
        assertFalse(manager.apply(packet("event-1", 4L, 1_500L,
                "the_end|wave5|1", 0.8F, "ACTIVE"), 2_100L));
        assertFalse(manager.active());

        assertTrue(manager.apply(packet("event-1", 5L, 100L,
                "the_end|wave5|2", 0.7F, "ACTIVE"), 2_200L));
        assertEquals(5L, manager.generation());
        assertTrue(manager.active());
    }

    @Test
    void clearClosesTheCycleEvenWhenAnActiveReplayHasTheSameOrANewerTimestamp() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("event-1", 4L, 1_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 1_000L));
        assertTrue(manager.apply(packet("event-1", 4L, 2_000L,
                "", 0.0F, "CLEAR"), 2_000L));

        for (long timestamp : new long[]{2_000L, 2_001L, 10_000L}) {
            assertFalse(manager.apply(packet("event-1", 4L, timestamp,
                    "the_end|wave5|0", 0.8F, "ACTIVE"), 10_000L));
            assertEquals(0.0F, manager.fogEndBlocks("the_end", 10_000L));
        }
    }

    @Test
    void allThreeCyclesCanRunInOneGenerationWithoutReopeningClearedCycles() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        for (int cycle = 0; cycle < 3; cycle++) {
            long timestamp = 1_000L + cycle * 3_000L;
            assertTrue(manager.apply(packet("event-1", 4L, timestamp,
                    "the_end|wave5|" + cycle, 0.8F, "ACTIVE"), timestamp));
            assertEquals(14.0F, manager.fogEndBlocks("the_end", timestamp), 0.001F);
            assertTrue(manager.apply(packet("event-1", 4L, timestamp + 1_000L,
                    "", 0.0F, "CLEAR"), timestamp + 1_000L));
            assertFalse(manager.apply(packet("event-1", 4L, timestamp + 2_000L,
                    "the_end|wave5|" + cycle, 0.8F, "ACTIVE"), timestamp + 2_000L));
            assertFalse(manager.active());
        }
    }

    @Test
    void advancingToALaterCycleRequiresAStrictlyNewerTimestamp() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("event-1", 4L, 1_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 1_000L));
        assertTrue(manager.apply(packet("event-1", 4L, 2_000L,
                "", 0.0F, "CLEAR"), 2_000L));
        assertFalse(manager.apply(packet("event-1", 4L, 2_000L,
                "the_end|wave5|1", 0.8F, "ACTIVE"), 2_000L));
        assertFalse(manager.active());
        assertTrue(manager.apply(packet("event-1", 4L, 2_001L,
                "the_end|wave5|1", 0.8F, "ACTIVE"), 2_001L));
    }

    @Test
    void anEarlierCycleCannotReplaceOrCloseTheCurrentCycle() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("event-1", 4L, 1_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 1_000L));
        assertTrue(manager.apply(packet("event-1", 4L, 2_000L,
                "", 0.0F, "CLEAR"), 2_000L));
        assertTrue(manager.apply(packet("event-1", 4L, 3_000L,
                "the_end|wave5|1", 0.7F, "ACTIVE"), 3_000L));

        assertFalse(manager.apply(packet("event-1", 4L, 4_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 4_000L));
        assertFalse(manager.apply(packet("event-1", 4L, 2_000L,
                "", 0.0F, "CLEAR"), 4_000L));
        assertEquals(16.0F, manager.fogEndBlocks("the_end", 4_000L), 0.001F);
    }

    @Test
    void aRetiredEventCannotReplaceANewerEventEvenWithAHigherGenerationOrTimestamp() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("retired-event", 10L, 1_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 1_000L));
        assertTrue(manager.apply(packet("new-event", 1L, 2_000L,
                "the_end|wave5|0", 0.7F, "ACTIVE"), 2_000L));

        assertFalse(manager.apply(packet("retired-event", 100L, 3_000L,
                "the_end|wave5|2", 0.8F, "ACTIVE"), 3_000L));
        assertFalse(manager.apply(packet("retired-event", 100L, 4_000L,
                "", 0.0F, "CLEAR"), 4_000L));
        assertEquals("new-event", manager.eventId());
        assertEquals(16.0F, manager.fogEndBlocks("the_end", 4_000L), 0.001F);
    }

    @Test
    void anUnseenEventRequiresATimestampBeyondTheAcceptedSessionHighWatermark() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("event-1", 4L, 2_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 2_000L));
        assertTrue(manager.apply(packet("event-1", 5L, 100L,
                "the_end|wave5|0", 0.7F, "ACTIVE"), 2_100L));

        for (long timestamp : new long[]{100L, 1_999L, 2_000L}) {
            assertFalse(manager.apply(packet("event-2", 1L, timestamp,
                    "overworld|wave5|0", 0.8F, "ACTIVE"), 2_200L));
            assertEquals("event-1", manager.eventId());
        }
        assertTrue(manager.apply(packet("event-2", 1L, 2_001L,
                "overworld|wave5|0", 0.8F, "ACTIVE"), 2_300L));
        assertEquals(14.0F, manager.fogEndBlocks("overworld", 2_300L), 0.001F);
    }

    @Test
    void anUnknownEventOrFutureGenerationClearCannotCloseTheCurrentFog() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertFalse(manager.apply(packet("unknown-event", 100L, 1_000L,
                "", 0.0F, "CLEAR"), 1_000L));
        assertTrue(manager.apply(packet("event-1", 4L, 2_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 2_000L));

        assertFalse(manager.apply(packet("future-event", 100L, 3_000L,
                "", 0.0F, "CLEAR"), 3_000L));
        assertFalse(manager.apply(packet("event-1", 5L, 4_000L,
                "", 0.0F, "CLEAR"), 4_000L));
        assertEquals("event-1", manager.eventId());
        assertEquals(4L, manager.generation());
        assertEquals(14.0F, manager.fogEndBlocks("the_end", 4_000L), 0.001F);
    }

    @Test
    void duplicateClearIsIdempotentAndAnOlderGenerationCannotReopenTheFog() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("event-1", 4L, 1_000L,
                "the_end|wave5|1", 0.8F, "ACTIVE"), 1_000L));
        BridgePayload clear = packet("event-1", 4L, 2_000L, "", 0.0F, "CLEAR");
        assertTrue(manager.apply(clear, 2_000L));
        assertTrue(manager.apply(clear, 2_001L));
        assertFalse(manager.apply(packet("event-1", 3L, 3_000L,
                "the_end|wave5|2", 0.8F, "ACTIVE"), 3_000L));
        assertFalse(manager.active());
    }

    @Test
    void aConnectionResetClearsFencesAndAllowsTheCurrentGenerationToRehydrate() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("event-1", 4L, 1_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 1_000L));
        assertTrue(manager.apply(packet("event-2", 1L, 2_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 2_000L));
        manager.reset();

        assertFalse(manager.active());
        assertEquals("", manager.eventId());
        assertEquals(0L, manager.generation());
        assertTrue(manager.apply(packet("event-1", 4L, 100L,
                "overworld|wave5|0", 0.8F, "ACTIVE"), 3_000L));
        assertEquals(14.0F, manager.fogEndBlocks("overworld", 3_000L), 0.001F);
    }

    @Test
    void deathOrWorldChangeClosesTheWholeGenerationUntilANewGenerationArrives() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertTrue(manager.apply(packet("event-1", 4L, 1_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 1_000L));
        manager.clear();

        assertFalse(manager.active());
        assertFalse(manager.apply(packet("event-1", 4L, 2_000L,
                "the_end|wave5|0", 0.8F, "ACTIVE"), 2_000L));
        assertFalse(manager.apply(packet("event-1", 4L, 3_000L,
                "the_end|wave5|2", 0.8F, "ACTIVE"), 3_000L));
        assertTrue(manager.apply(packet("event-1", 5L, 100L,
                "the_end|wave5|0", 0.7F, "ACTIVE"), 4_000L));
        assertEquals(16.0F, manager.fogEndBlocks("the_end", 4_000L), 0.001F);
    }

    @Test
    void rejectsUnsupportedPhaseDimensionAndFogVisual() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        assertFalse(manager.apply(packet("event-1", 1L, 100L,
                "overworld|wave4|0", 0.8F, "ACTIVE"), 100L));
        assertFalse(manager.apply(packet("event-1", 1L, 200L,
                "the_end|wave5|0", 0.8F, "ACTIVE", "OTHER_FOG"), 200L));
        assertFalse(manager.active());
    }

    @Test
    void eventHistoryIsBoundedWithoutReopeningForgottenSessions() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        for (int i = 0; i <= 128; i++) {
            assertTrue(manager.apply(packet("event-" + i, 1L, i + 1L,
                    "the_end|wave5|0", .8F, "ACTIVE"), i + 1L));
        }
        for (int i = 129; i < 4_096; i++) {
            assertFalse(manager.apply(packet("event-" + i, 1L, i + 1L,
                    "the_end|wave5|0", .8F, "ACTIVE"), i + 1L),
                    "unknown identities must fail closed once the retained event history is full");
        }
        assertEquals("event-128", manager.eventId());
        assertFalse(manager.apply(packet("event-0", 500L, 5_000L,
                "the_end|wave5|2", .9F, "ACTIVE"), 5_000L));
        assertTrue(manager.apply(packet("event-128", 2L, 5_001L,
                "the_end|wave5|0", .8F, "ACTIVE"), 5_001L),
                "the existing event may still advance generation at capacity");
        assertTrue(manager.apply(packet("event-128", 2L, 5_002L, "", 0F, "CLEAR"), 5_002L));
        manager.reset();
        assertTrue(manager.apply(packet("fresh-connection", 1L, 1L,
                "the_end|wave5|0", .8F, "ACTIVE"), 6_000L));
    }

    @Test
    void oversizedEventIdentitiesCannotBeRetainedOrReplaceCurrentFog() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();
        String oversized = "a".repeat(129);
        assertFalse(manager.apply(packet(oversized, 1L, 1L,
                "the_end|wave5|0", .8F, "ACTIVE"), 1L));
        assertEquals("", manager.eventId());
        assertTrue(manager.apply(packet("current", 1L, 2L,
                "the_end|wave5|0", .8F, "ACTIVE"), 2L));
        assertFalse(manager.apply(packet(oversized, 100L, 3L,
                "the_end|wave5|0", .9F, "ACTIVE"), 3L));
        assertEquals("current", manager.eventId());
        assertEquals(14F, manager.fogEndBlocks("the_end", 3L), .001F);
    }

    private static BridgePayload packet(String eventId, long generation, long timestamp,
                                        String mode, float intensity, String status) {
        return packet(eventId, generation, timestamp, mode, intensity, status, "WAVE5_FOG_V1");
    }

    private static BridgePayload packet(String eventId, long generation, long timestamp,
                                        String mode, float intensity, String status, String visualId) {
        return new BridgePayload("END_EVENT:END_FOG_STATE", 2, generation, timestamp,
                eventId, "wave5-black-fog", false, false, false, false, Set.of(), "",
                visualId, 8_000, intensity, 0, 0, mode, "", "", "", status);
    }
}
