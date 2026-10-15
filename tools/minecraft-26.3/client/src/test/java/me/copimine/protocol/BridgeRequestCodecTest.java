package me.copimine.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgeRequestCodecTest {
    private static final byte[] NONCE_BYTES = sequence(32, 0x20);
    private static final String NONCE = Base64.getUrlEncoder().withoutPadding().encodeToString(NONCE_BYTES);
    private static final String END_STREAM = Base64.getUrlEncoder().withoutPadding().encodeToString(sequence(16, 0x40));
    private static final UUID PLAYER = UUID.fromString("d8d4966c-f2c4-4a13-9e33-82940f1dc2d6");
    private static final UUID SESSION = UUID.fromString("ff199a7d-3672-4b35-babb-e4e3e7faee20");
    private static final UUID EVENT = UUID.fromString("1ec25d21-9c8d-4e68-aecd-4e6f1611c515");
    private static final UUID TARGET = UUID.fromString("b6fd906f-a220-465f-b101-0a2b1c8d6016");

    @Test
    void encodesAndDecodesAllFiveRequestTypesWithExactFixtures() throws Exception {
        byte[] helloId = sequence(16, 0x60);
        byte[] reopenId = sequence(16, 0x70);
        byte[] readyId = sequence(16, 0x80);
        byte[] helloDigest = sequence(32, 0x10);
        byte[] readyDigest = sequence(32, 0x30);
        BridgeRequestCodec.Request[] requests = {
                new BridgeRequestCodec.ControlHello(NONCE, PLAYER, SESSION, helloId),
                new BridgeRequestCodec.PrisonerAbilityRequest(NONCE, PLAYER, SESSION, 1, END_STREAM,
                        EVENT, 3, "HEAL", Optional.of(TARGET)),
                new BridgeRequestCodec.StreamReopenRequest(NONCE, PLAYER, SESSION, 4,
                        BridgeRequestCodec.Sender.END_EVENT, Optional.of(END_STREAM), reopenId),
                new BridgeRequestCodec.SenderReady(NONCE, PLAYER, SESSION, 5,
                        BridgeRequestCodec.Sender.NARCOTICS, END_STREAM, readyId, helloDigest),
                new BridgeRequestCodec.SenderReadyConfirm(NONCE, PLAYER, SESSION,
                        BridgeRequestCodec.Sender.END_EVENT, END_STREAM, readyId, 5, readyDigest)
        };
        ByteArrayOutputStream[] fixtures = {
                fixtureHeader(0),
                fixtureHeader(1),
                fixtureHeader(2),
                fixtureHeader(3),
                fixtureHeader(4)
        };
        append(fixtures[0], helloId);
        append(fixtures[1], longBytes(1));
        append(fixtures[1], new byte[]{22});
        append(fixtures[1], END_STREAM.getBytes(StandardCharsets.US_ASCII));
        append(fixtures[1], uuidBytes(EVENT));
        append(fixtures[1], longBytes(3));
        appendString(fixtures[1], "HEAL");
        append(fixtures[1], new byte[]{1});
        append(fixtures[1], uuidBytes(TARGET));
        append(fixtures[2], longBytes(4));
        append(fixtures[2], new byte[]{1, 22});
        append(fixtures[2], END_STREAM.getBytes(StandardCharsets.US_ASCII));
        append(fixtures[2], reopenId);
        append(fixtures[3], longBytes(5));
        append(fixtures[3], new byte[]{2, 22});
        append(fixtures[3], END_STREAM.getBytes(StandardCharsets.US_ASCII));
        append(fixtures[3], readyId);
        append(fixtures[3], helloDigest);
        append(fixtures[4], new byte[]{1, 22});
        append(fixtures[4], END_STREAM.getBytes(StandardCharsets.US_ASCII));
        append(fixtures[4], readyId);
        append(fixtures[4], longBytes(5));
        append(fixtures[4], readyDigest);

        for (int i = 0; i < requests.length; i++) {
            byte[] encoded = BridgeRequestCodec.encode(requests[i]);
            assertArrayEquals(fixtures[i].toByteArray(), encoded, "request fixture index " + i);
            assertArrayEquals(encoded, BridgeRequestCodec.encode(BridgeRequestCodec.decode(encoded)));
        }
    }

    @Test
    void rejectsMalformedHeadersCountersIdentifiersAndTrailingBytes() {
        byte[] hello = BridgeRequestCodec.encode(new BridgeRequestCodec.ControlHello(
                NONCE, PLAYER, SESSION, new byte[16]));
        byte[] trailing = Arrays.copyOf(hello, hello.length + 1);
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestCodec.decode(trailing));
        byte[] unknownProtocol = hello.clone();
        unknownProtocol[0] = 3;
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestCodec.decode(unknownProtocol));
        byte[] unknownType = hello.clone();
        unknownType[1] = 99;
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestCodec.decode(unknownType));

        assertThrows(IllegalArgumentException.class,
                () -> BridgeRequestCodec.encode(new BridgeRequestCodec.PrisonerAbilityRequest(
                        NONCE, PLAYER, SESSION, 0, END_STREAM, EVENT, 1, "ABILITY", Optional.empty())));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeRequestCodec.encode(new BridgeRequestCodec.PrisonerAbilityRequest(
                        NONCE, PLAYER, SESSION, 1, END_STREAM, EVENT, 0, "ABILITY", Optional.empty())));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeRequestCodec.encode(new BridgeRequestCodec.PrisonerAbilityRequest(
                        NONCE, PLAYER, SESSION, 1, END_STREAM, EVENT, 1, "bad ability", Optional.empty())));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeRequestCodec.encode(new BridgeRequestCodec.PrisonerAbilityRequest(
                        NONCE, PLAYER, SESSION, 1, END_STREAM, EVENT, 1, "PRISONER_FREE", Optional.empty())));

        byte[] badNonce = hello.clone();
        badNonce[2 + 42] = (byte) '+';
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestCodec.decode(badNonce));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeRequestCodec.decode(new byte[BridgeRequestCodec.MAX_REQUEST_BYTES + 1]));
    }

    @Test
    void acceptsOnlyTheTwoLegalReopenStreamLengthsAndExactReadyAckDigests() {
        byte[] requestId = sequence(16, 1);
        BridgeRequestCodec.StreamReopenRequest noStream = new BridgeRequestCodec.StreamReopenRequest(
                NONCE, PLAYER, SESSION, 1, BridgeRequestCodec.Sender.NARCOTICS, Optional.empty(), requestId);
        assertArrayEquals(BridgeRequestCodec.encode(noStream),
                BridgeRequestCodec.encode(BridgeRequestCodec.decode(BridgeRequestCodec.encode(noStream))));

        assertThrows(IllegalArgumentException.class,
                () -> BridgeRequestCodec.encode(new BridgeRequestCodec.SenderReady(NONCE, PLAYER, SESSION, 1,
                        BridgeRequestCodec.Sender.END_EVENT, "short", requestId, new byte[32])));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeRequestCodec.encode(new BridgeRequestCodec.SenderReadyConfirm(NONCE, PLAYER, SESSION,
                        BridgeRequestCodec.Sender.END_EVENT, END_STREAM, requestId, 0, new byte[32])));
    }

    private static ByteArrayOutputStream fixtureHeader(int type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(4);
        out.writeByte(type);
        out.writeByte(43);
        out.write(NONCE.getBytes(StandardCharsets.US_ASCII));
        out.write(uuidBytes(PLAYER));
        out.write(uuidBytes(SESSION));
        return bytes;
    }

    private static void append(ByteArrayOutputStream target, byte[] suffix) {
        target.write(suffix, 0, suffix.length);
    }

    private static void appendString(ByteArrayOutputStream target, String value) {
        append(target, new byte[]{(byte) (value.length() >>> 8), (byte) value.length()});
        append(target, value.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] uuidBytes(UUID uuid) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        return bytes.toByteArray();
    }

    private static byte[] longBytes(long value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8);
        new DataOutputStream(bytes).writeLong(value);
        return bytes.toByteArray();
    }

    private static byte[] sequence(int length, int start) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (start + i);
        }
        return bytes;
    }
}
