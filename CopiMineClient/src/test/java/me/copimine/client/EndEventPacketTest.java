package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndEventPacketTest {
    @Test
    void keepsCurrentEventSemanticsExplicit() {
        EndEventPacket packet = new EndEventPacket(
                "END_BOSS_PHASE", "event-1", 7L, "instance-1", 5_000L,
                "subject-1", "", "RIFT|CAST_RELEASE", "control-id");

        assertEquals("END_BOSS_PHASE", packet.type());
        assertEquals("event-1", packet.eventId());
        assertEquals(7L, packet.generation());
        assertEquals("instance-1", packet.instanceId());
        assertEquals(5_000L, packet.durationMillis());
        assertEquals("subject-1", packet.subjectId());
        assertEquals("RIFT|CAST_RELEASE", packet.phaseId());
    }

    @Test
    void rejectsUnknownTypeAndUnsafeIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new EndEventPacket(
                "UNKNOWN", "event", 1L, "instance", 1_000L,
                "", "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new EndEventPacket(
                "END_BOSS_BIND", "event", 0L, "instance", 1_000L,
                "subject", "visual", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new EndEventPacket(
                "END_BOSS_BIND", "event", 1L, "instance", 600_001L,
                "subject", "visual", "", ""));
    }
}
