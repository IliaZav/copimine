package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndEventPacketTest {
    @Test
    void decodesTheServerEnvelopeWithBoundedFields() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF(ClientBridgeProtocol.END_EVENT_MAGIC);
        out.writeUTF("END_CONTROL_START");
        out.writeUTF("event-1");
        out.writeLong(7L);
        out.writeUTF("instance-1");
        out.writeLong(5_000L);
        out.writeUTF("subject-1");
        out.writeUTF("boss-id");
        out.writeUTF("control-id");
        out.flush();

        EndEventPacket packet = EndEventPacket.parse(bytes.toByteArray());

        assertEquals("END_CONTROL_START", packet.type());
        assertEquals("event-1", packet.eventId());
        assertEquals(7L, packet.generation());
        assertEquals("instance-1", packet.instanceId());
        assertEquals(5_000L, packet.durationMillis());
        assertEquals("subject-1", packet.subjectId());
    }

    @Test
    void rejectsWrongMagicAndUnknownEventType() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> EndEventPacket.parse(envelope("WRONG", "END_BOSS_BIND")));
        assertThrows(IllegalArgumentException.class, () -> EndEventPacket.parse(envelope(ClientBridgeProtocol.END_EVENT_MAGIC, "UNKNOWN")));
    }

    private static byte[] envelope(String magic, String type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF(magic);
        out.writeUTF(type);
        out.writeUTF("event");
        out.writeLong(1L);
        out.writeUTF("instance");
        out.writeLong(1_000L);
        out.writeUTF("");
        out.writeUTF("");
        out.writeUTF("");
        out.flush();
        return bytes.toByteArray();
    }
}
