package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventClientStateTest {
    @Test
    void acceptsBossBindingAndOneControlEffect() {
        EndEventClientState state = new EndEventClientState();

        assertTrue(state.apply(packet("END_BOSS_BIND", "event-1", 1L, "boss-bind", 0L, "boss-uuid", "boss-id", "control-id"), 100L));
        assertTrue(state.isBossBound("boss-uuid"));
        assertTrue(state.apply(packet("END_CONTROL_START", "event-1", 1L, "control-1", 10_000L, "", "boss-id", "control-id"), 100L));
        assertTrue(state.isReverseActive(101L));
        assertTrue(state.controlInstanceId().equals("control-1"));
    }

    @Test
    void staleStopCannotCancelNewerControlInstance() {
        EndEventClientState state = new EndEventClientState();
        state.apply(packet("END_CONTROL_START", "event-1", 1L, "old", 10_000L, "", "", ""), 100L);
        state.apply(packet("END_CONTROL_START", "event-1", 1L, "new", 10_000L, "", "", ""), 200L);

        assertFalse(state.apply(packet("END_CONTROL_STOP", "event-1", 1L, "old", 0L, "", "", ""), 300L));
        assertTrue(state.isReverseActive(301L));
        assertTrue(state.controlInstanceId().equals("new"));
    }

    @Test
    void generationAndEventChangesClearOldState() {
        EndEventClientState state = new EndEventClientState();
        state.apply(packet("END_BOSS_BIND", "event-1", 1L, "boss-bind", 0L, "boss-1", "", ""), 100L);
        state.apply(packet("END_CONTROL_START", "event-1", 1L, "control-1", 100L, "", "", ""), 100L);

        assertTrue(state.apply(packet("END_BOSS_BIND", "event-2", 1L, "boss-bind-2", 0L, "boss-2", "", ""), 200L));
        assertFalse(state.isBossBound("boss-1"));
        assertFalse(state.isReverseActive(201L));
        assertTrue(state.isBossBound("boss-2"));
    }

    @Test
    void expiryAndExplicitClearRemoveEffects() {
        EndEventClientState state = new EndEventClientState();
        state.apply(packet("END_CONTROL_START", "event-1", 1L, "control-1", 100L, "", "", ""), 100L);

        assertTrue(state.isReverseActive(199L));
        assertFalse(state.isReverseActive(200L));
        state.clear();
        assertFalse(state.isReverseActive(201L));
        assertFalse(state.hasBossBinding());
    }

    private static EndEventPacket packet(String type, String eventId, long generation, String instance,
                                         long duration, String subject, String bossId, String controlId) {
        return new EndEventPacket(type, eventId, generation, instance, duration, subject, bossId, controlId);
    }
}
