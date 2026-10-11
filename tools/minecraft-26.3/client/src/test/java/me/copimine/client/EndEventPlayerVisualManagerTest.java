package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EndEventPlayerVisualManagerTest {
    @Test
    void carrierAndHuntFeedbackUseTheServerWindowAndTheMatchingDimension() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 100, 1, 0, "ACTIVE", 1_500), 1_000));
        assertEquals(EndEventPlayerVisualManager.Kind.CARRIER, manager.visualState("the_end", 1_100).kind());
        assertNull(manager.visualState("overworld", 1_100));
        assertNotNull(manager.visualState("the_end", 2_499));
        assertNull(manager.visualState("the_end", 2_500));
        assertTrue(manager.apply(packet("event", 4, 200, 2, 1, "ACTIVE", 11_000), 3_000));
        assertEquals(EndEventPlayerVisualManager.Kind.HUNT, manager.visualState("the_end", 13_999).kind());
        assertNull(manager.visualState("the_end", 14_000));
    }

    @Test
    void aDuplicateActiveCannotExtendAnExpiredWindow() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        BridgePayload active = packet("event", 4, 100, 1, 0, "ACTIVE", 1_500);
        assertTrue(manager.apply(active, 1_000));
        assertFalse(manager.apply(active, 2_000));
        assertNull(manager.visualState("the_end", 2_500));
    }

    @Test
    void deliveryClearsImmediatelyButAFreshPickupCanReuseTheSameChargeCycle() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 100, 1, 0, "ACTIVE", 1_500), 100));
        assertTrue(manager.apply(packet("event", 4, 200, 1, 0, "CLEAR", 1_500), 200));
        assertNull(manager.visualState("the_end", 200));
        assertFalse(manager.apply(packet("event", 4, 200, 1, 0, "ACTIVE", 1_500), 201));
        assertTrue(manager.apply(packet("event", 4, 201, 1, 0, "ACTIVE", 1_500), 201));
        assertNotNull(manager.visualState("the_end", 201));
    }

    @Test
    void clearBeforeActiveFencesTheDelayedPacketWithoutAdoptingAnEvent() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 200, 1, 0, "CLEAR", 1_500), 200));
        assertEquals("", manager.eventId());
        assertFalse(manager.apply(packet("event", 4, 100, 1, 0, "ACTIVE", 1_500), 201));
        assertFalse(manager.apply(packet("event", 4, 200, 1, 0, "ACTIVE", 1_500), 201));
        assertTrue(manager.apply(packet("event", 4, 201, 1, 0, "ACTIVE", 1_500), 201));
    }

    @Test
    void unknownClearCannotReplaceOrEraseTheCurrentEvent() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("current", 4, 100, 2, 1, "ACTIVE", 11_000), 100));
        assertTrue(manager.apply(packet("other", 500, 900, 2, 1, "CLEAR", 1_500), 200));
        assertEquals("current", manager.eventId());
        assertEquals(4, manager.generation());
        assertNotNull(manager.visualState("the_end", 200));
        assertFalse(manager.apply(packet("current", 5, 900, 2, 1, "CLEAR", 1_500), 200));
        assertNotNull(manager.visualState("the_end", 200));
    }

    @Test
    void targetReplacementRejectsOldCycleActiveAndClearEvenWithNewerTimestamps() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 100, 2, 1, "ACTIVE", 11_000), 100));
        assertTrue(manager.apply(packet("event", 4, 200, 2, 1, "CLEAR", 1_500), 200));
        assertTrue(manager.apply(packet("event", 4, 300, 2, 2, "ACTIVE", 11_000), 300));
        assertFalse(manager.apply(packet("event", 4, 400, 2, 1, "ACTIVE", 11_000), 400));
        assertFalse(manager.apply(packet("event", 4, 500, 2, 1, "CLEAR", 1_500), 500));
        assertNotNull(manager.visualState("the_end", 500));
    }

    @Test
    void duplicateClearIsIdempotentAndAStaleClearCannotEraseAFreshPickup() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        BridgePayload clear = packet("event", 4, 200, 1, 0, "CLEAR", 1_500);
        assertTrue(manager.apply(packet("event", 4, 100, 1, 0, "ACTIVE", 1_500), 100));
        assertTrue(manager.apply(clear, 200));
        assertTrue(manager.apply(clear, 201));
        assertTrue(manager.apply(packet("event", 4, 300, 1, 0, "ACTIVE", 1_500), 300));
        assertFalse(manager.apply(clear, 400));
        assertNotNull(manager.visualState("the_end", 400));
    }

    @Test
    void aLaterCycleCannotReuseTheTimestampOfAnEarlierClear() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 100, 2, 1, "ACTIVE", 11_000), 100));
        assertTrue(manager.apply(packet("event", 4, 200, 2, 1, "CLEAR", 1_500), 200));
        assertFalse(manager.apply(packet("event", 4, 200, 2, 2, "CLEAR", 1_500), 201));
        assertTrue(manager.apply(packet("event", 4, 201, 2, 1, "ACTIVE", 11_000), 201));
    }

    @Test
    void waveSixAndSevenEnvelopesCannotChangeThePlayerFeedbackChannels() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 100, 2, 1, "ACTIVE", 11_000), 100));
        assertFalse(manager.apply(rawPacket("event", 4, 200, "PRISONER_V1", "wave6-prisoner",
                "the_end|wave6|0", "ACTIVE", 1_500), 200));
        assertFalse(manager.apply(rawPacket("event", 4, 300, "RITUAL_V1", "wave7-ritual",
                "the_end|wave7|0", "CLEAR", 1_500), 300));
        assertEquals(EndEventPlayerVisualManager.Kind.HUNT, manager.visualState("the_end", 300).kind());
    }

    @Test
    void channelsHaveIndependentTimestampFencesAndOneClearLeavesTheOtherState() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 100, 1, 0, "ACTIVE", 1_500), 100));
        assertTrue(manager.apply(packet("event", 4, 100, 2, 1, "ACTIVE", 11_000), 100));
        assertTrue(manager.apply(packet("event", 4, 200, 1, 0, "CLEAR", 1_500), 200));
        assertEquals(EndEventPlayerVisualManager.Kind.HUNT, manager.visualState("the_end", 200).kind());
        assertTrue(manager.apply(packet("event", 4, 200, 2, 1, "CLEAR", 1_500), 200));
        assertNull(manager.visualState("the_end", 200));
    }

    @Test
    void aNewGenerationClearsBothChannelsAndRejectsOlderGenerations() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 900, 1, 0, "ACTIVE", 1_500), 100));
        assertTrue(manager.apply(packet("event", 5, 100, 2, 1, "ACTIVE", 11_000), 200));
        assertFalse(manager.apply(packet("event", 4, 1_000, 1, 1, "ACTIVE", 1_500), 300));
        assertFalse(manager.apply(packet("event", 4, 1_001, 2, 1, "CLEAR", 1_500), 300));
        assertEquals(EndEventPlayerVisualManager.Kind.HUNT, manager.visualState("the_end", 300).kind());
    }

    @Test
    void aRetiredEventCannotReopenAtTheSameOrAHigherGeneration() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("old", 4, 100, 1, 0, "ACTIVE", 1_500), 100));
        assertTrue(manager.apply(packet("current", 4, 200, 2, 1, "ACTIVE", 11_000), 200));
        assertFalse(manager.apply(packet("old", 4, 300, 1, 1, "ACTIVE", 1_500), 300));
        assertFalse(manager.apply(packet("old", 500, 400, 1, 1, "ACTIVE", 1_500), 400));
        assertFalse(manager.apply(packet("old", 500, 500, 2, 1, "CLEAR", 1_500), 500));
        assertEquals("current", manager.eventId());
    }

    @Test
    void anUnseenEventMustBeNewerThanTheWholeAcceptedSession() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 900, 1, 0, "ACTIVE", 1_500), 100));
        assertTrue(manager.apply(packet("event", 5, 100, 2, 1, "ACTIVE", 11_000), 200));
        assertFalse(manager.apply(packet("other", 1, 900, 1, 0, "ACTIVE", 1_500), 300));
        assertTrue(manager.apply(packet("other", 1, 901, 1, 0, "ACTIVE", 1_500), 300));
    }

    @Test
    void deathOrWorldExitBlocksTheGenerationUntilTheMatchingWorldIsExplicitlyResumed() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 100, 1, 0, "ACTIVE", 1_500), 100));
        manager.clear();
        assertNull(manager.visualState("the_end", 200));
        assertFalse(manager.apply(packet("event", 4, 200, 1, 1, "ACTIVE", 1_500), 200));
        assertFalse(manager.resumeAfterLocalExit("event", 5, 300, "the_end"));
        assertFalse(manager.resumeAfterLocalExit("event", 4, 300, "overworld"));
        assertFalse(manager.resumeAfterLocalExit("event", 4, 100, "the_end"));
        assertTrue(manager.resumeAfterLocalExit("event", 4, 300, "the_end"));
        assertFalse(manager.apply(packet("event", 4, 299, 1, 0, "ACTIVE", 1_500), 400));
        assertTrue(manager.apply(packet("event", 4, 300, 1, 0, "ACTIVE", 1_500), 400));
        assertNotNull(manager.visualState("the_end", 400));
    }

    @Test
    void aServerSuspendAlsoRequiresAnExplicitResume() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 100, 2, 1, "ACTIVE", 11_000), 100));
        assertTrue(manager.apply(packet("event", 4, 200, 2, 1, "SUSPEND", 1_500), 200));
        assertFalse(manager.apply(packet("event", 4, 300, 2, 1, "ACTIVE", 11_000), 300));
        assertTrue(manager.resumeAfterLocalExit("event", 4, 400, "the_end"));
        assertTrue(manager.apply(packet("event", 4, 400, 2, 1, "ACTIVE", 11_000), 400));
    }

    @Test
    void aConnectionResetAllowsTheServerToRehydrateTheExistingGeneration() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("old", 4, 100, 1, 0, "ACTIVE", 1_500), 100));
        assertTrue(manager.apply(packet("current", 4, 200, 2, 1, "ACTIVE", 11_000), 200));
        manager.clear();
        manager.reset();
        assertNull(manager.visualState("the_end", 300));
        assertEquals("", manager.eventId());
        assertEquals(0, manager.generation());
        assertTrue(manager.apply(packet("old", 4, 100, 1, 0, "ACTIVE", 1_500), 300));
    }

    @Test
    void eventHistoryFailsClosedAtItsBoundWithoutForgettingRetiredIdentities() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        for (int index = 0; index <= 128; index++) {
            assertTrue(manager.apply(packet("event-" + index, 4, index + 1, 1, 0, "ACTIVE", 1_500), index));
        }
        assertFalse(manager.apply(packet("overflow", 4, 1_000, 1, 0, "ACTIVE", 1_500), 1_000));
        assertFalse(manager.apply(packet("event-0", 500, 1_001, 1, 0, "ACTIVE", 1_500), 1_000));
        assertEquals("event-128", manager.eventId());
        assertTrue(manager.apply(packet("event-128", 5, 1_002, 1, 0, "ACTIVE", 1_500), 1_000));
        manager.reset();
        assertTrue(manager.apply(packet("fresh", 1, 1, 1, 0, "ACTIVE", 1_500), 1_000));
    }

    @Test
    void rejectsUnsupportedOrOversizedStateBeforeChangingTheCurrentPresentation() {
        EndEventPlayerVisualManager manager = new EndEventPlayerVisualManager();
        assertTrue(manager.apply(packet("event", 4, 100, 1, 0, "ACTIVE", 1_500), 100));
        assertFalse(manager.apply(packet("x".repeat(129), 4, 200, 1, 0, "ACTIVE", 1_500), 200));
        assertFalse(manager.apply(packet("event", 4, 200, 1, 0, "ACTIVE", 15_001), 200));
        assertFalse(manager.apply(rawPacket("event", 4, 200, "WAVE1_CARRIER_V1", "wave2-hunt-mark", "the_end|wave1|0", "ACTIVE", 1_500), 200));
        assertFalse(manager.apply(rawPacket("event", 4, 200, "WAVE1_CARRIER_V1", "wave1-carrier-charge", "moon|wave1|0", "ACTIVE", 1_500), 200));
        assertFalse(manager.apply(rawPacket("event", 4, 200, "WAVE1_CARRIER_V1", "wave1-carrier-charge", "the_end|wave1|-1", "ACTIVE", 1_500), 200));
        assertFalse(manager.apply(rawPacket("event", 4, 200, "WAVE1_CARRIER_V1", "wave1-carrier-charge", "the_end|wave1|2147483648", "ACTIVE", 1_500), 200));
        assertNotNull(manager.visualState("the_end", 200));
    }

    private static BridgePayload packet(String event, long generation, long timestamp, int wave, int cycle,
                                        String status, int duration) {
        return rawPacket(event, generation, timestamp,
                wave == 1 ? "WAVE1_CARRIER_V1" : "WAVE2_HUNT_MARK_V1",
                wave == 1 ? "wave1-carrier-charge" : "wave2-hunt-mark",
                "the_end|wave" + wave + "|" + cycle, status, duration);
    }

    private static BridgePayload rawPacket(String event, long generation, long timestamp, String visual,
                                           String instance, String mode, String status, int duration) {
        return new BridgePayload("END_EVENT:END_PLAYER_STATE", 2, generation, timestamp, event,
                instance, false, false, false, false, Set.of(), "", visual, duration, .8F,
                0, 0, mode, "", "", "", status);
    }
}
