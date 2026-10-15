package me.copimine.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeBodyCodecTest {
    private static final long TIMESTAMP = 1_800_000_000_000L;
    private static final UUID EVENT = UUID.fromString("1ec25d21-9c8d-4e68-aecd-4e6f1611c515");
    private static final UUID PLAYER = UUID.fromString("d8d4966c-f2c4-4a13-9e33-82940f1dc2d6");
    private static final UUID ENTITY = UUID.fromString("b6fd906f-a220-465f-b101-0a2b1c8d6016");
    private static final UUID OWNER = UUID.fromString("ff199a7d-3672-4b35-babb-e4e3e7faee20");

    @Test
    void everySupportedEndEventTypeMatchesAnIndependentExactBinaryFixture() throws Exception {
        List<EndEventBridgeBodyCodec.Body> fixtures = endEventFixtures();
        assertEquals(22, fixtures.size());
        for (EndEventBridgeBodyCodec.Body body : fixtures) {
            byte[] expected = endEventFixture(body);
            byte[] actual = EndEventBridgeBodyCodec.encode(body);
            assertArrayEquals(expected, actual, "exact fixture for " + body.type());
            assertEquals(body, EndEventBridgeBodyCodec.decode(expected), "round trip for " + body.type());
        }
    }

    @Test
    void endEventDecoderRejectsReservedBytesAndTrailingData() throws Exception {
        EndEventBridgeBodyCodec.Body body = endEventFixtures().getFirst();
        byte[] fixture = endEventFixture(body);
        int flagsOffset = 1 + 1 + 4 + 8 + 8 + 16 + 2;
        byte[] nonzeroFlag = fixture.clone();
        nonzeroFlag[flagsOffset] = 1;
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.decode(nonzeroFlag));

        byte[] supportedCount = fixture.clone();
        supportedCount[flagsOffset + 4 + 3] = 1;
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.decode(supportedCount));

        byte[] trailing = java.util.Arrays.copyOf(fixture, fixture.length + 1);
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.decode(trailing));

        byte[] negativeZero = fixture.clone();
        int progressOffset = 1 + 1 + 4 + 8 + 8 + 16 + 2 + 4 + 4 + 2 + 2 + 4;
        negativeZero[progressOffset] = (byte) 0x80;
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.decode(negativeZero));
    }

    @Test
    void endEventEncoderRejectsNondefaultFieldsUnknownTokensAndInvalidBounds() {
        EndEventBridgeBodyCodec.Body base = endEventFixtures().get(1);
        assertThrows(IllegalArgumentException.class,
                () -> EndEventBridgeBodyCodec.encode(copy(base, "unexpected", base.controlId(), base.visualId(),
                        base.durationMillis(), base.progressOrIntensity(), base.health(), base.maxHealth(),
                        base.subjectOrMode(), base.clearPolicy(), base.status())));

        EndEventBridgeBodyCodec.Body bind = endEventFixtures().getFirst();
        assertThrows(IllegalArgumentException.class,
                () -> EndEventBridgeBodyCodec.encode(copy(bind, bind.instanceId(), bind.controlId(), bind.visualId(),
                        bind.durationMillis(), bind.progressOrIntensity(), bind.health(), bind.maxHealth(),
                        "the_end|+0,1,2|1,2,3", bind.clearPolicy(), bind.status())));

        EndEventBridgeBodyCodec.Body beam = endEventFixtures().get(17);
        assertThrows(IllegalArgumentException.class,
                () -> EndEventBridgeBodyCodec.encode(copy(beam, beam.instanceId(), beam.controlId(), beam.visualId(),
                        beam.durationMillis(), 0.5f, beam.health(), beam.maxHealth(), beam.subjectOrMode(),
                        beam.clearPolicy(), beam.status())));

        assertThrows(IllegalArgumentException.class,
                () -> EndEventBridgeBodyCodec.encode(copy(beam, EVENT + ":1:world:other", beam.controlId(),
                        beam.visualId(), beam.durationMillis(), beam.progressOrIntensity(), beam.health(),
                        beam.maxHealth(), beam.subjectOrMode(), beam.clearPolicy(), beam.status())));

        assertThrows(IllegalArgumentException.class,
                () -> EndEventBridgeBodyCodec.encode(copy(bind, bind.instanceId(), bind.controlId(), bind.visualId(),
                        bind.durationMillis(), -0.0f, bind.health(), bind.maxHealth(), bind.subjectOrMode(),
                        bind.clearPolicy(), bind.status())));
    }

    @Test
    void everyNarcoticsTypeHasAnExactBinaryFixtureAndStrictUnusedDefaults() throws Exception {
        List<NarcoticsBridgeBodyCodec.Body> fixtures = narcoticsFixtures();
        assertEquals(3, fixtures.size());
        for (NarcoticsBridgeBodyCodec.Body body : fixtures) {
            byte[] expected = narcoticsFixture(body);
            byte[] actual = NarcoticsBridgeBodyCodec.encode(body);
            assertArrayEquals(expected, actual, "exact fixture for " + body.type());
            assertEquals(body, NarcoticsBridgeBodyCodec.decode(expected), "round trip for " + body.type());
        }

        NarcoticsBridgeBodyCodec.Body start = fixtures.getFirst();
        assertThrows(IllegalArgumentException.class,
                () -> NarcoticsBridgeBodyCodec.encode(new NarcoticsBridgeBodyCodec.Body(start.type(),
                        start.commandSeq(), start.bodyTimestampMillis(), start.sessionUuid(), "UNKNOWN_EFFECT",
                        start.shaderpackId(), start.durationMillis(), start.intensity(), start.fadeInMillis(),
                        start.fadeOutMillis(), start.mode(), start.clearPolicy(), start.source(), start.reason(),
                        start.status(), start.visualInstanceId(), start.targetVisualInstanceId(),
                        start.clearThroughCounter())));

        NarcoticsBridgeBodyCodec.Body stop = fixtures.get(1);
        assertThrows(IllegalArgumentException.class,
                () -> NarcoticsBridgeBodyCodec.encode(new NarcoticsBridgeBodyCodec.Body(stop.type(),
                        stop.commandSeq(), stop.bodyTimestampMillis(), stop.sessionUuid(), stop.effectId(),
                        stop.shaderpackId(), stop.durationMillis(), -0.0f, stop.fadeInMillis(), stop.fadeOutMillis(),
                        stop.mode(), stop.clearPolicy(), stop.source(), stop.reason(), stop.status(),
                        stop.visualInstanceId(), stop.targetVisualInstanceId(), stop.clearThroughCounter())));
    }

    @Test
    void commonControlAckBodiesHaveExactFixturesAndStrictFields() throws Exception {
        BridgeControlBodyCodec.HelloAck hello = new BridgeControlBodyCodec.HelloAck(
                BridgeRequestCodec.Sender.END_EVENT, EVENT, 1,
                new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
        byte[] helloExpected = controlHelloAckFixture(hello);
        assertArrayEquals(helloExpected, BridgeControlBodyCodec.encode(hello));
        BridgeControlBodyCodec.HelloAck decodedHello =
                (BridgeControlBodyCodec.HelloAck) BridgeControlBodyCodec.decode(helloExpected);
        assertEquals(hello.sender(), decodedHello.sender());
        assertEquals(hello.sessionUuid(), decodedHello.sessionUuid());
        assertEquals(hello.requestKind(), decodedHello.requestKind());
        assertArrayEquals(hello.requestId(), decodedHello.requestId());

        BridgeControlBodyCodec.SenderReadyAck ready = new BridgeControlBodyCodec.SenderReadyAck(
                BridgeRequestCodec.Sender.NARCOTICS, EVENT,
                new byte[] {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0}, 19);
        byte[] readyExpected = controlReadyAckFixture(ready);
        assertArrayEquals(readyExpected, BridgeControlBodyCodec.encode(ready));
        BridgeControlBodyCodec.SenderReadyAck decodedReady =
                (BridgeControlBodyCodec.SenderReadyAck) BridgeControlBodyCodec.decode(readyExpected);
        assertEquals(ready.sender(), decodedReady.sender());
        assertEquals(ready.sessionUuid(), decodedReady.sessionUuid());
        assertEquals(ready.readyRequestCounter(), decodedReady.readyRequestCounter());
        assertArrayEquals(ready.requestId(), decodedReady.requestId());

        byte[] trailing = java.util.Arrays.copyOf(helloExpected, helloExpected.length + 1);
        assertThrows(IllegalArgumentException.class, () -> BridgeControlBodyCodec.decode(trailing));
        byte[] invalidRequestKind = helloExpected.clone();
        invalidRequestKind[19] = 2;
        assertThrows(IllegalArgumentException.class, () -> BridgeControlBodyCodec.decode(invalidRequestKind));
        byte[] unknownSender = readyExpected.clone();
        unknownSender[2] = 3;
        assertThrows(IllegalArgumentException.class, () -> BridgeControlBodyCodec.decode(unknownSender));
    }

    @Test
    void contextAabbVolumeAndBeamEndpointsAreCheckedAgainstInclusiveBlocks() {
        EndEventBridgeBodyCodec.Body context = body(EndEventBridgeBodyCodec.Type.CONTEXT_BIND, "", "", "",
                0, 0, 0, 0, "the_end|0,64,0|1,65,1", "", "");
        EndEventBridgeBodyCodec.ArenaBounds bounds = EndEventBridgeBodyCodec.arenaBoundsFromContextBind(context);
        EndEventBridgeBodyCodec.Body validBeam = body(EndEventBridgeBodyCodec.Type.END_WORLD_BEAM,
                EVENT + ":1:world:edge", "", "RIFT_BEAM", 1_000, 0.1f, 0, 0,
                "the_end|edge|0.000,64.000,0.000", "1.999,65.999,1.999|AABBCC", "");
        assertTrue(EndEventBridgeBodyCodec.beamWithinBounds(validBeam, bounds));

        EndEventBridgeBodyCodec.Body outsideBeam = copy(validBeam, validBeam.instanceId(), validBeam.controlId(),
                validBeam.visualId(), validBeam.durationMillis(), validBeam.progressOrIntensity(),
                validBeam.health(), validBeam.maxHealth(), validBeam.subjectOrMode(),
                "2.000,65.000,1.000|AABBCC", validBeam.status());
        assertFalse(EndEventBridgeBodyCodec.beamWithinBounds(outsideBeam, bounds));

        EndEventBridgeBodyCodec.Body maximumVolume = body(EndEventBridgeBodyCodec.Type.CONTEXT_BIND,
                "", "", "", 0, 0, 0, 0, "the_end|0,0,0|63,63,63", "", "");
        EndEventBridgeBodyCodec.arenaBoundsFromContextBind(maximumVolume);
        EndEventBridgeBodyCodec.Body excessiveVolume = copy(maximumVolume, "", "", "", 0, 0, 0, 0,
                "the_end|0,0,0|63,64,63", "", "");
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.encode(excessiveVolume));
    }

    @Test
    void rejectsInvalidStringEncodingAndTimestampBodyMismatchCanBeDetected() throws Exception {
        EndEventBridgeBodyCodec.Body body = endEventFixtures().get(1);
        byte[] encoded = endEventFixture(body);
        byte[] nonAscii = encoded.clone();
        int firstString = 1 + 1 + 4 + 8 + 8 + 16 + 2;
        nonAscii[firstString] = (byte) 0xc3;
        assertThrows(IllegalArgumentException.class, () -> EndEventBridgeBodyCodec.decode(nonAscii));

        NarcoticsBridgeBodyCodec.Body narcotics = narcoticsFixtures().getFirst();
        byte[] narcoticsBytes = NarcoticsBridgeBodyCodec.encode(narcotics);
        assertEquals(narcotics.bodyTimestampMillis(), NarcoticsBridgeBodyCodec.decode(narcoticsBytes).bodyTimestampMillis());
        NarcoticsBridgeBodyCodec.Body stop = narcoticsFixtures().get(1);
        byte[] negativeZeroIntensity = narcoticsFixture(stop);
        int intensityOffset = 1 + 1 + 4 + 8 + 8 + 16 + 2 + stop.effectId().length() + 2 + 4;
        negativeZeroIntensity[intensityOffset] = (byte) 0x80;
        assertThrows(IllegalArgumentException.class,
                () -> NarcoticsBridgeBodyCodec.decode(negativeZeroIntensity));
        assertThrows(IllegalArgumentException.class,
                () -> NarcoticsBridgeBodyCodec.validateTimestampMatches(narcotics, TIMESTAMP + 1));
        assertThrows(IllegalArgumentException.class,
                () -> EndEventBridgeBodyCodec.validateTimestampMatches(body, TIMESTAMP + 1));
    }

    @Test
    void retriesOmitOnlyTimestampAndPreserveTheirOriginalClearFence() {
        NarcoticsBridgeBodyCodec.Body clear = narcoticsFixtures().get(2);
        NarcoticsBridgeBodyCodec.Body retry = new NarcoticsBridgeBodyCodec.Body(clear.type(), clear.commandSeq(),
                clear.bodyTimestampMillis() + 500, clear.sessionUuid(), clear.effectId(), clear.shaderpackId(),
                clear.durationMillis(), clear.intensity(), clear.fadeInMillis(), clear.fadeOutMillis(), clear.mode(),
                clear.clearPolicy(), clear.source(), clear.reason(), clear.status(), clear.visualInstanceId(),
                clear.targetVisualInstanceId(), clear.clearThroughCounter());
        assertArrayEquals(NarcoticsBridgeBodyCodec.logicalComparisonBytes(clear),
                NarcoticsBridgeBodyCodec.logicalComparisonBytes(retry));
        assertArrayEquals(NarcoticsBridgeBodyCodec.logicalFingerprint(clear),
                NarcoticsBridgeBodyCodec.logicalFingerprint(retry));
        NarcoticsBridgeBodyCodec.validateFenceForEnvelope(clear, 3, true);
        NarcoticsBridgeBodyCodec.validateFenceForEnvelope(retry, 4, false);
        assertThrows(IllegalArgumentException.class,
                () -> NarcoticsBridgeBodyCodec.validateFenceForEnvelope(retry, 4, true));
        assertThrows(IllegalArgumentException.class,
                () -> NarcoticsBridgeBodyCodec.validateFenceForEnvelope(retry, 2, false));

        NarcoticsBridgeBodyCodec.Body changedFence = new NarcoticsBridgeBodyCodec.Body(retry.type(),
                retry.commandSeq(), retry.bodyTimestampMillis(), retry.sessionUuid(), retry.effectId(),
                retry.shaderpackId(), retry.durationMillis(), retry.intensity(), retry.fadeInMillis(),
                retry.fadeOutMillis(), retry.mode(), retry.clearPolicy(), retry.source(), retry.reason(),
                retry.status(), retry.visualInstanceId(), retry.targetVisualInstanceId(),
                retry.clearThroughCounter() + 1);
        assertNotEquals(NarcoticsBridgeBodyCodec.logicalFingerprint(retry),
                NarcoticsBridgeBodyCodec.logicalFingerprint(changedFence));
    }

    private static List<EndEventBridgeBodyCodec.Body> endEventFixtures() {
        EndEventBridgeBodyCodec.Type[] types = EndEventBridgeBodyCodec.Type.values();
        List<EndEventBridgeBodyCodec.Body> bodies = new ArrayList<>(types.length);
        for (EndEventBridgeBodyCodec.Type type : types) {
            bodies.add(switch (type) {
                case CONTEXT_BIND -> body(type, "", "", "", 0, 0, 0, 0,
                        "the_end|-3,64,-3|3,72,3", "", "");
                case CONTEXT_UNBIND -> body(type, "", "", "", 0, 0, 0, 0, "", "", "");
                case END_BOSS_BIND -> body(type, "boss.1", "", "END_RIFT_BOSS_V1", 0, 0, 0, 0,
                        uuid(ENTITY), "", "");
                case END_BOSS_UNBIND -> body(type, "boss.1", "", "", 0, 0, 0, 0, "", "", "");
                case END_BOSS_PHASE -> body(type, "boss.1", "", "IDLE_BREATH", 0, 0, 0, 0,
                        uuid(ENTITY), "", "");
                case END_BOSS_BAR -> body(type, "boss.1", "", "HUNT|NONE", 0, 0.5f, 5, 10,
                        uuid(ENTITY), "", "");
                case END_ENTITY_BIND -> body(type, "mob.1", "", "END_RIFT_SERVANT_V1", 0, 0, 0, 0,
                        uuid(ENTITY), "", "");
                case END_ENTITY_PHASE -> body(type, "mob.1", "", "WAVE_COMBAT", 0, 0, 0, 0,
                        uuid(ENTITY), "", "");
                case END_ENTITY_UNBIND -> body(type, "mob.1", "", "", 0, 0, 0, 0, uuid(ENTITY), "", "");
                case END_CONTROL_START -> body(type, uuid(PLAYER), "REVERSE", "", 1_000, 0, 0, 0,
                        "", "", "");
                case END_CONTROL_STOP -> body(type, uuid(PLAYER), "", "", 0, 0, 0, 0, "", "", "");
                case END_PLAYER_STATE -> body(type, "carrier.1", "", "WAVE1_CARRIER_V1", 1_000, 0.5f, 0, 0,
                        "the_end|wave1|1", "", "ACTIVE");
                case END_FOG_STATE -> body(type, "", "", "WAVE5_FOG_V1", 1_000, 0.25f, 0, 0,
                        "the_end|wave5|1", "", "ACTIVE");
                case END_PRESENTATION_RESUME -> body(type, "", "", "PRESENTATION_RESUME_V1", 0, 0, 0, 0,
                        "the_end", "", "");
                case END_PRISONER_RESUME -> body(type, uuid(PLAYER), "", "PRISONER_SESSION_V1", 0, 0, 0, 0,
                        "the_end", "", "");
                case END_PRISONER_STATE -> body(type, uuid(PLAYER), "", "PRISONER_V1", 0, 0, 0, 0,
                        "2", "0|1|2|3", "PRISONER_TARGETS_V1|" + uuid(ENTITY) + "|" + uuid(OWNER));
                case END_PRISONER_CLEAR -> body(type, uuid(PLAYER), "", "", 0, 0, 0, 0, "", "", "");
                case END_WORLD_BEAM -> body(type, EVENT + ":1:world:beam_1", "", "RIFT_BEAM", 1_000, 0.1f,
                        0, 0, "the_end|beam_1|1.000,64.000,-1.500", "2.000,70.000,3.500|AABBCC", "");
                case END_WORLD_VFX_CLEAR -> body(type, EVENT + ":1:world:beam_1", "", "RIFT_BEAM", 0, 0,
                        0, 0, "", "", "");
                case END_ECHO_BIND, END_ECHO_STATE, END_ECHO_REMOVE -> body(type,
                        "duel.1", "", "END_RIFT_ECHO_V1", 0, 0, 0, 0, uuid(ENTITY),
                        uuid(PLAYER) + "|1|1|STANDING|0|NONE|0|0|0|0|0|0", "minecraft:the_end");
            });
        }
        return bodies;
    }

    private static EndEventBridgeBodyCodec.Body body(EndEventBridgeBodyCodec.Type type, String instance,
            String control, String visual, long duration, float intensity, long health, long maxHealth,
            String subject, String clearPolicy, String status) {
        return new EndEventBridgeBodyCodec.Body(type, 1, TIMESTAMP, EVENT, instance, control, visual,
                duration, intensity, health, maxHealth, subject, clearPolicy, "", "", status);
    }

    private static EndEventBridgeBodyCodec.Body copy(EndEventBridgeBodyCodec.Body body, String instance,
            String control, String visual, long duration, float intensity, long health, long maxHealth,
            String subject, String clearPolicy, String status) {
        return new EndEventBridgeBodyCodec.Body(body.type(), body.generation(), body.bodyTimestampMillis(),
                body.eventUuid(), instance, control, visual, duration, intensity, health, maxHealth,
                subject, clearPolicy, body.reservedOne(), body.reservedTwo(), status);
    }

    private static byte[] endEventFixture(EndEventBridgeBodyCodec.Body body) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        out.writeByte(body.type().code());
        out.writeInt(4);
        out.writeLong(body.generation());
        out.writeLong(body.bodyTimestampMillis());
        writeUuid(out, body.eventUuid());
        writeString(out, body.instanceId());
        out.writeByte(0);
        out.writeByte(0);
        out.writeByte(0);
        out.writeByte(0);
        out.writeInt(0);
        writeString(out, body.controlId());
        writeString(out, body.visualId());
        out.writeInt((int) body.durationMillis());
        out.writeFloat(body.progressOrIntensity());
        out.writeInt((int) body.health());
        out.writeInt((int) body.maxHealth());
        writeString(out, body.subjectOrMode());
        writeString(out, body.clearPolicy());
        writeString(out, body.reservedOne());
        writeString(out, body.reservedTwo());
        writeString(out, body.status());
        return bytes.toByteArray();
    }

    private static List<NarcoticsBridgeBodyCodec.Body> narcoticsFixtures() {
        return List.of(
                new NarcoticsBridgeBodyCodec.Body(NarcoticsBridgeBodyCodec.Type.VISUAL_START,
                        1, TIMESTAMP, EVENT, "INVERT", "", 10_000, 0.5f, 500, 500,
                        "CLIENT_MOD", "PRESERVE", "end-event", "", "",
                        ENTITY, new UUID(0, 0), 0),
                new NarcoticsBridgeBodyCodec.Body(NarcoticsBridgeBodyCodec.Type.VISUAL_STOP,
                        2, TIMESTAMP, EVENT, "INVERT", "", 0, 0, 0, 0,
                        "CLIENT_MOD", "", "", "USER_STOP", "",
                        new UUID(0, 0), ENTITY, 0),
                new NarcoticsBridgeBodyCodec.Body(NarcoticsBridgeBodyCodec.Type.VISUAL_CLEAR_ALL,
                        3, TIMESTAMP, EVENT, "", "", 0, 0, 0, 0,
                        "CLIENT_MOD", "REPLACE_ALL_FULLSCREEN", "SYSTEM", "SESSION_END", "",
                        new UUID(0, 0), new UUID(0, 0), 2));
    }

    private static byte[] narcoticsFixture(NarcoticsBridgeBodyCodec.Body body) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        out.writeByte(body.type().code());
        out.writeInt(4);
        out.writeLong(body.commandSeq());
        out.writeLong(body.bodyTimestampMillis());
        writeUuid(out, body.sessionUuid());
        writeString(out, body.effectId());
        writeString(out, body.shaderpackId());
        out.writeInt((int) body.durationMillis());
        out.writeFloat(body.intensity());
        out.writeInt((int) body.fadeInMillis());
        out.writeInt((int) body.fadeOutMillis());
        writeString(out, body.mode());
        writeString(out, body.clearPolicy());
        writeString(out, body.source());
        writeString(out, body.reason());
        writeString(out, body.status());
        writeUuid(out, body.visualInstanceId());
        writeUuid(out, body.targetVisualInstanceId());
        out.writeLong(body.clearThroughCounter());
        return bytes.toByteArray();
    }

    private static byte[] controlHelloAckFixture(BridgeControlBodyCodec.HelloAck body) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        out.writeByte(0);
        out.writeByte(body.sender().id());
        writeUuid(out, body.sessionUuid());
        out.writeByte(body.requestKind());
        out.write(body.requestId());
        return bytes.toByteArray();
    }

    private static byte[] controlReadyAckFixture(BridgeControlBodyCodec.SenderReadyAck body) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1);
        out.writeByte(255);
        out.writeByte(body.sender().id());
        writeUuid(out, body.sessionUuid());
        out.write(body.requestId());
        out.writeLong(body.readyRequestCounter());
        return bytes.toByteArray();
    }

    private static void writeString(DataOutputStream out, String value) throws Exception {
        byte[] ascii = value.getBytes(StandardCharsets.US_ASCII);
        out.writeShort(ascii.length);
        out.write(ascii);
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws Exception {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static String uuid(UUID uuid) {
        return uuid.toString();
    }
}
