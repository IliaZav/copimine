package me.copimine.client;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Set;

/** Bounded parser for the optional End Rift messages carried by the existing bridge channel. */
public record EndEventPacket(
        String type,
        String eventId,
        long generation,
        String instanceId,
        long durationMillis,
        String subjectId,
        String bossId,
        String controlId) {
    private static final int MAX_PACKET_BYTES = 8_192;
    private static final int MAX_STRING_LENGTH = 256;
    private static final long MAX_DURATION_MILLIS = 600_000L;
    private static final Set<String> TYPES = Set.of(
            "END_BOSS_BIND",
            "END_BOSS_UNBIND",
            "END_BOSS_PHASE",
            "END_BOSS_BAR",
            "END_ENTITY_BIND",
            "END_ENTITY_UNBIND",
            "END_CONTROL_START",
            "END_CONTROL_STOP");

    public EndEventPacket {
        type = bounded(type, "type");
        eventId = bounded(eventId, "eventId");
        instanceId = bounded(instanceId, "instanceId");
        subjectId = bounded(subjectId, "subjectId");
        bossId = bounded(bossId, "bossId");
        controlId = bounded(controlId, "controlId");
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown End Rift event type: " + type);
        }
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("End Rift eventId is required");
        }
        if (generation <= 0L) {
            throw new IllegalArgumentException("End Rift generation must be positive");
        }
        if (durationMillis < 0L || durationMillis > MAX_DURATION_MILLIS) {
            throw new IllegalArgumentException("End Rift duration is outside the safe bound");
        }
    }

    /** The legacy bossId wire slot carries the bounded visual id for mob binds. */
    public String visualId() {
        return bossId;
    }

    /** The legacy bossId wire slot also carries the bounded boss phase/model id. */
    public String phaseId() {
        return bossId;
    }

    public static EndEventPacket parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("End Rift packet is outside the safe size bound");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String magic = input.readUTF();
            if (!ClientBridgeProtocol.END_EVENT_MAGIC.equals(magic)) {
                throw new IllegalArgumentException("Not an End Rift event packet");
            }
            EndEventPacket packet = readAfterMagic(input);
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing bytes in End Rift event packet");
            }
            return packet;
        } catch (IOException error) {
            throw new IllegalArgumentException("Malformed End Rift event packet", error);
        }
    }

    static EndEventPacket readAfterMagic(DataInputStream input) throws IOException {
        return new EndEventPacket(
                input.readUTF(),
                input.readUTF(),
                input.readLong(),
                input.readUTF(),
                input.readLong(),
                input.readUTF(),
                input.readUTF(),
                input.readUTF());
    }

    private static String bounded(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("End Rift " + field + " is too long");
        }
        return normalized;
    }
}
