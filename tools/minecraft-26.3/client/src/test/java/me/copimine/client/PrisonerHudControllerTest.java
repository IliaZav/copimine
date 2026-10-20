package me.copimine.client;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerHudControllerTest {
    private static final UUID PRISONER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long[] COOLDOWNS = {0L, 0L, 0L, 0L};
    private static final long MAX_SUPPORTED_GENERATION = PrisonerHudController.MAX_GENERATION;

    @Test
    void rejectsAnOutOfRangeGenerationWithoutPoisoningTheNextValidSession() {
        PrisonerHudController controller = new PrisonerHudController();

        assertFalse(controller.applyState("event", Long.MAX_VALUE, PRISONER, 0, COOLDOWNS,
                PrisonerTargetEligibility.empty(), 1_000L, 1_000L));
        assertTrue(controller.applyState("event", 1L, PRISONER, 0, COOLDOWNS,
                PrisonerTargetEligibility.empty(), 1_001L, 1_001L));
        assertTrue(controller.clear("event", 1L));
    }

    @Test
    void remoteTimestampCannotBlockTheMatchingClear() {
        PrisonerHudController controller = new PrisonerHudController();

        assertTrue(controller.applyState("event", 1L, PRISONER, 0, COOLDOWNS,
                PrisonerTargetEligibility.empty(), 1_000L, Long.MAX_VALUE));
        assertTrue(controller.clear("event", 1L));
        assertFalse(controller.applyState("event", 1L, PRISONER, 0, COOLDOWNS,
                PrisonerTargetEligibility.empty(), 1_001L, 1_001L));
        assertFalse(controller.activeFor(PRISONER));
    }

    @Test
    void localClockRollbackCannotBlockTheMatchingClear() {
        PrisonerHudController controller = new PrisonerHudController();

        assertTrue(controller.applyState("event", 1L, PRISONER, 0, COOLDOWNS,
                PrisonerTargetEligibility.empty(), 1_000L, 1_000L));
        assertTrue(controller.clear("event", 1L));
        assertFalse(controller.activeFor(PRISONER));
    }

    @Test
    void sameMillisecondResumeCanRestoreTheCurrentSession() {
        PrisonerHudController controller = new PrisonerHudController();

        assertTrue(controller.applyState("event", 1L, PRISONER, 0, COOLDOWNS,
                PrisonerTargetEligibility.empty(), 1_000L, 1_000L));
        assertTrue(controller.clear("event", 1L));
        assertTrue(controller.resumeSession("event", 1L));
        assertTrue(controller.applyState("event", 1L, PRISONER, 0, COOLDOWNS,
                PrisonerTargetEligibility.empty(), 1_001L, 1_001L));
        assertTrue(controller.activeFor(PRISONER));
    }

    @Test
    void rejectsAnExcessiveGenerationButAllowsTheDocumentedBoundary() {
        assertEquals(Long.MAX_VALUE - 1L, MAX_SUPPORTED_GENERATION,
                "client generation bound must match the server wire contract");
        PrisonerHudController controller = new PrisonerHudController();
        assertFalse(controller.applyState("event", MAX_SUPPORTED_GENERATION + 1L, PRISONER, 0, COOLDOWNS,
                PrisonerTargetEligibility.empty(), 1_000L, 1_000L));
        assertTrue(controller.applyState("event", MAX_SUPPORTED_GENERATION, PRISONER, 0, COOLDOWNS,
                PrisonerTargetEligibility.empty(), 1_001L, 1_001L));
    }
}
