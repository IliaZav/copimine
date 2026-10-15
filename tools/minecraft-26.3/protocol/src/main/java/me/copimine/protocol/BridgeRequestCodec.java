package me.copimine.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Strict codec for client-originated CopiMine bridge v4 request records. */
public final class BridgeRequestCodec {
    public static final int MAX_REQUEST_BYTES = 1_024;
    public static final int COMMON_HEADER_BYTES = 1 + 1 + 1 + BridgeSigningPolicy.CLIENT_NONCE_BASE64URL_LENGTH + 16 + 16;

    private static final int HELLO_ID_BYTES = 16;
    private static final int REQUEST_ID_BYTES = 16;
    private static final int SHA256_BYTES = 32;
    private static final Set<String> PRISONER_ABILITIES = Set.of("HEAL", "BATTLE_SURGE", "GUARDIAN_LINK", "TURNCOAT");

    private BridgeRequestCodec() {
    }

    public static byte[] encode(Request request) {
        Objects.requireNonNull(request, "request");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(COMMON_HEADER_BYTES + 64);
            DataOutputStream output = new DataOutputStream(bytes);
            int type = writeCommonHeader(output, request);
            if (request instanceof ControlHello hello) {
                writeFixed(output, hello.helloId(), HELLO_ID_BYTES, "hello id");
            } else if (request instanceof PrisonerAbilityRequest ability) {
                writePositiveLong(output, ability.requestCounter(), "request counter");
                writeStream(output, ability.streamId());
                writeUuid(output, requireUuid(ability.eventUuid(), "event UUID"));
                if (ability.generation() <= 0 || ability.generation() == Long.MAX_VALUE) {
                    throw new IllegalArgumentException("Invalid End Event generation");
                }
                output.writeLong(ability.generation());
                writeAbilityId(output, ability.abilityId());
                Optional<UUID> target = requireOptional(ability.targetUuid(), "ability target");
                output.writeByte(target.isPresent() ? 1 : 0);
                if (target.isPresent()) {
                    writeUuid(output, target.get());
                }
            } else if (request instanceof StreamReopenRequest reopen) {
                writePositiveLong(output, reopen.requestCounter(), "request counter");
                output.writeByte(requireSender(reopen.sender()).id());
                Optional<String> expected = requireOptional(reopen.expectedStreamId(), "expected stream ID");
                if (expected.isEmpty()) {
                    output.writeByte(0);
                } else {
                    writeStream(output, expected.get());
                }
                writeFixed(output, reopen.requestId(), REQUEST_ID_BYTES, "reopen request ID");
            } else if (request instanceof SenderReady ready) {
                writePositiveLong(output, ready.requestCounter(), "request counter");
                output.writeByte(requireSender(ready.sender()).id());
                writeStream(output, ready.streamId());
                writeFixed(output, ready.requestId(), REQUEST_ID_BYTES, "ready request ID");
                writeFixed(output, ready.ackEnvelopeSha256(), SHA256_BYTES, "hello_ack digest");
            } else if (request instanceof SenderReadyConfirm confirm) {
                output.writeByte(requireSender(confirm.sender()).id());
                writeStream(output, confirm.streamId());
                writeFixed(output, confirm.readyRequestId(), REQUEST_ID_BYTES, "ready request ID");
                writePositiveLong(output, confirm.readyRequestCounter(), "ready request counter");
                writeFixed(output, confirm.ackEnvelopeSha256(), SHA256_BYTES, "sender_ready_ack digest");
            } else {
                throw new IllegalArgumentException("Unknown bridge v4 request type");
            }
            output.flush();
            if (bytes.size() > MAX_REQUEST_BYTES) {
                throw new IllegalArgumentException("Bridge v4 request exceeds the size limit");
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode bridge v4 request", exception);
        }
    }

    public static Request decode(byte[] encoded) {
        if (encoded == null || encoded.length < COMMON_HEADER_BYTES || encoded.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Invalid bridge v4 request length");
        }
        ByteBuffer input = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        try {
            int protocol = readU8(input);
            if (protocol != BridgeSigningPolicy.PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported bridge v4 request protocol");
            }
            int type = readU8(input);
            int nonceLength = readU8(input);
            if (nonceLength != BridgeSigningPolicy.CLIENT_NONCE_BASE64URL_LENGTH) {
                throw new IllegalArgumentException("Invalid bridge v4 request nonce length");
            }
            String nonce = readAscii(input, nonceLength);
            if (!BridgeSigningPolicy.isCanonicalBase64Url(nonce,
                    BridgeSigningPolicy.CLIENT_NONCE_BYTES, BridgeSigningPolicy.CLIENT_NONCE_BASE64URL_LENGTH)) {
                throw new IllegalArgumentException("Noncanonical bridge v4 request nonce");
            }
            UUID playerUuid = readUuid(input);
            UUID sessionUuid = readUuid(input);
            Request request = switch (type) {
                case 0 -> new ControlHello(nonce, playerUuid, sessionUuid, readFixed(input, HELLO_ID_BYTES));
                case 1 -> decodeAbility(input, nonce, playerUuid, sessionUuid);
                case 2 -> decodeReopen(input, nonce, playerUuid, sessionUuid);
                case 3 -> decodeReady(input, nonce, playerUuid, sessionUuid);
                case 4 -> decodeReadyConfirm(input, nonce, playerUuid, sessionUuid);
                default -> throw new IllegalArgumentException("Unknown bridge v4 request type");
            };
            if (input.hasRemaining()) {
                throw new IllegalArgumentException("Trailing bridge v4 request bytes");
            }
            return request;
        } catch (java.nio.BufferUnderflowException exception) {
            throw new IllegalArgumentException("Truncated bridge v4 request", exception);
        }
    }

    private static int writeCommonHeader(DataOutputStream output, Request request) throws IOException {
        String nonce = request.clientNonce();
        if (!BridgeSigningPolicy.isCanonicalBase64Url(nonce,
                BridgeSigningPolicy.CLIENT_NONCE_BYTES, BridgeSigningPolicy.CLIENT_NONCE_BASE64URL_LENGTH)) {
            throw new IllegalArgumentException("Invalid bridge v4 client nonce");
        }
        UUID player = requireUuid(request.playerUuid(), "player UUID");
        UUID session = requireUuid(request.clientModSessionUuid(), "client-mod session UUID");
        int type;
        if (request instanceof ControlHello) {
            type = 0;
        } else if (request instanceof PrisonerAbilityRequest) {
            type = 1;
        } else if (request instanceof StreamReopenRequest) {
            type = 2;
        } else if (request instanceof SenderReady) {
            type = 3;
        } else if (request instanceof SenderReadyConfirm) {
            type = 4;
        } else {
            throw new IllegalArgumentException("Unknown bridge v4 request type");
        }
        output.writeByte(BridgeSigningPolicy.PROTOCOL_VERSION);
        output.writeByte(type);
        output.writeByte(BridgeSigningPolicy.CLIENT_NONCE_BASE64URL_LENGTH);
        output.write(nonce.getBytes(StandardCharsets.US_ASCII));
        writeUuid(output, player);
        writeUuid(output, session);
        return type;
    }

    private static PrisonerAbilityRequest decodeAbility(ByteBuffer input, String nonce, UUID player, UUID session) {
        long counter = readPositiveLong(input, "request counter");
        String stream = readStream(input);
        UUID event = readUuid(input);
        long generation = readPositiveLong(input, "event generation");
        if (generation == Long.MAX_VALUE) {
            throw new IllegalArgumentException("End Event generation is reserved");
        }
        String abilityId = readAbilityId(input);
        int present = readU8(input);
        if (present != 0 && present != 1) {
            throw new IllegalArgumentException("Invalid target-present flag");
        }
        Optional<UUID> target = present == 1 ? Optional.of(readUuid(input)) : Optional.empty();
        return new PrisonerAbilityRequest(nonce, player, session, counter, stream, event,
                generation, abilityId, target);
    }

    private static StreamReopenRequest decodeReopen(ByteBuffer input, String nonce, UUID player, UUID session) {
        long counter = readPositiveLong(input, "request counter");
        Sender sender = Sender.fromId(readU8(input));
        int streamLength = readU8(input);
        Optional<String> stream;
        if (streamLength == 0) {
            stream = Optional.empty();
        } else if (streamLength == BridgeSigningPolicy.STREAM_ID_BASE64URL_LENGTH) {
            stream = Optional.of(readStreamBytes(input, streamLength));
        } else {
            throw new IllegalArgumentException("Invalid expected stream ID length");
        }
        byte[] requestId = readFixed(input, REQUEST_ID_BYTES);
        return new StreamReopenRequest(nonce, player, session, counter, sender, stream, requestId);
    }

    private static SenderReady decodeReady(ByteBuffer input, String nonce, UUID player, UUID session) {
        long counter = readPositiveLong(input, "request counter");
        Sender sender = Sender.fromId(readU8(input));
        String stream = readStream(input);
        byte[] requestId = readFixed(input, REQUEST_ID_BYTES);
        byte[] digest = readFixed(input, SHA256_BYTES);
        return new SenderReady(nonce, player, session, counter, sender, stream, requestId, digest);
    }

    private static SenderReadyConfirm decodeReadyConfirm(ByteBuffer input, String nonce, UUID player, UUID session) {
        Sender sender = Sender.fromId(readU8(input));
        String stream = readStream(input);
        byte[] readyRequestId = readFixed(input, REQUEST_ID_BYTES);
        long readyRequestCounter = readPositiveLong(input, "ready request counter");
        byte[] digest = readFixed(input, SHA256_BYTES);
        return new SenderReadyConfirm(nonce, player, session, sender, stream,
                readyRequestId, readyRequestCounter, digest);
    }

    private static String readStream(ByteBuffer input) {
        int length = readU8(input);
        if (length != BridgeSigningPolicy.STREAM_ID_BASE64URL_LENGTH) {
            throw new IllegalArgumentException("Invalid stream ID length");
        }
        return readStreamBytes(input, length);
    }

    private static String readStreamBytes(ByteBuffer input, int length) {
        String stream = readAscii(input, length);
        if (!BridgeSigningPolicy.isCanonicalBase64Url(stream,
                BridgeSigningPolicy.STREAM_ID_BYTES, BridgeSigningPolicy.STREAM_ID_BASE64URL_LENGTH)) {
            throw new IllegalArgumentException("Noncanonical stream ID");
        }
        return stream;
    }

    private static String readIdentifierString(ByteBuffer input, int maximumBytes, String fieldName) {
        if (input.remaining() < 2) {
            throw new IllegalArgumentException("Truncated " + fieldName);
        }
        int length = Short.toUnsignedInt(input.getShort());
        if (length < 1 || length > maximumBytes || input.remaining() < length) {
            throw new IllegalArgumentException("Invalid " + fieldName + " length");
        }
        String value = readAscii(input, length);
        requireIdentifier(value, maximumBytes, fieldName);
        return value;
    }

    private static String readAbilityId(ByteBuffer input) {
        String abilityId = readIdentifierString(input, 128, "ability id");
        if (!PRISONER_ABILITIES.contains(abilityId)) {
            throw new IllegalArgumentException("Unknown prisoner ability ID");
        }
        return abilityId;
    }

    private static String readAscii(ByteBuffer input, int length) {
        if (length < 0 || input.remaining() < length) {
            throw new IllegalArgumentException("Truncated ASCII field");
        }
        byte[] bytes = new byte[length];
        input.get(bytes);
        for (byte value : bytes) {
            int character = Byte.toUnsignedInt(value);
            if (character < 0x20 || character > 0x7e) {
                throw new IllegalArgumentException("Bridge request fields must be printable ASCII");
            }
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static UUID readUuid(ByteBuffer input) {
        if (input.remaining() < 16) {
            throw new IllegalArgumentException("Truncated UUID");
        }
        return new UUID(input.getLong(), input.getLong());
    }

    private static byte[] readFixed(ByteBuffer input, int length) {
        if (input.remaining() < length) {
            throw new IllegalArgumentException("Truncated fixed-size field");
        }
        byte[] bytes = new byte[length];
        input.get(bytes);
        return bytes;
    }

    private static int readU8(ByteBuffer input) {
        if (!input.hasRemaining()) {
            throw new IllegalArgumentException("Truncated byte field");
        }
        return Byte.toUnsignedInt(input.get());
    }

    private static long readPositiveLong(ByteBuffer input, String fieldName) {
        if (input.remaining() < 8) {
            throw new IllegalArgumentException("Truncated " + fieldName);
        }
        long value = input.getLong();
        if (value <= 0) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
        return value;
    }

    private static void writeStream(DataOutputStream output, String streamId) throws IOException {
        if (!BridgeSigningPolicy.isCanonicalBase64Url(streamId,
                BridgeSigningPolicy.STREAM_ID_BYTES, BridgeSigningPolicy.STREAM_ID_BASE64URL_LENGTH)) {
            throw new IllegalArgumentException("Invalid canonical stream ID");
        }
        output.writeByte(BridgeSigningPolicy.STREAM_ID_BASE64URL_LENGTH);
        output.write(streamId.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeIdentifierString(DataOutputStream output, String value, int maximumBytes,
            String fieldName) throws IOException {
        requireIdentifier(value, maximumBytes, fieldName);
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static void writeAbilityId(DataOutputStream output, String abilityId) throws IOException {
        if (abilityId == null || !PRISONER_ABILITIES.contains(abilityId)) {
            throw new IllegalArgumentException("Unknown prisoner ability ID");
        }
        writeIdentifierString(output, abilityId, 128, "ability id");
    }

    private static void requireIdentifier(String value, int maximumBytes, String fieldName) {
        if (value == null || value.isEmpty() || value.length() > maximumBytes) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'A' && character <= 'Z') && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9') && character != '_' && character != '.'
                    && character != ':' && character != '-') {
                throw new IllegalArgumentException("Invalid " + fieldName);
            }
        }
    }

    private static void writePositiveLong(DataOutputStream output, long value, String fieldName) throws IOException {
        if (value <= 0) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
        output.writeLong(value);
    }

    private static void writeFixed(DataOutputStream output, byte[] value, int expectedLength, String fieldName)
            throws IOException {
        if (value == null || value.length != expectedLength) {
            throw new IllegalArgumentException("Invalid " + fieldName + " length");
        }
        output.write(value);
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID requireUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + fieldName);
        }
        return value;
    }

    private static Sender requireSender(Sender sender) {
        if (sender == null) {
            throw new IllegalArgumentException("Missing bridge sender");
        }
        return sender;
    }

    private static <T> Optional<T> requireOptional(Optional<T> value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + fieldName + " option");
        }
        return value;
    }

    private static byte[] cloneBytes(byte[] value) {
        return value == null ? null : value.clone();
    }

    public sealed interface Request permits ControlHello, PrisonerAbilityRequest,
            StreamReopenRequest, SenderReady, SenderReadyConfirm {
        String clientNonce();

        UUID playerUuid();

        UUID clientModSessionUuid();
    }

    public enum Sender {
        END_EVENT(1),
        NARCOTICS(2);

        private final int id;

        Sender(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        private static Sender fromId(int id) {
            return switch (id) {
                case 1 -> END_EVENT;
                case 2 -> NARCOTICS;
                default -> throw new IllegalArgumentException("Unknown bridge sender ID");
            };
        }
    }

    public record ControlHello(String clientNonce, UUID playerUuid, UUID clientModSessionUuid,
            byte[] helloId) implements Request {
        public ControlHello {
            helloId = cloneBytes(helloId);
        }

        @Override
        public byte[] helloId() {
            return cloneBytes(helloId);
        }
    }

    public record PrisonerAbilityRequest(String clientNonce, UUID playerUuid, UUID clientModSessionUuid,
            long requestCounter, String streamId, UUID eventUuid, long generation, String abilityId,
            Optional<UUID> targetUuid) implements Request {
    }

    public record StreamReopenRequest(String clientNonce, UUID playerUuid, UUID clientModSessionUuid,
            long requestCounter, Sender sender, Optional<String> expectedStreamId, byte[] requestId) implements Request {
        public StreamReopenRequest {
            requestId = cloneBytes(requestId);
        }

        @Override
        public byte[] requestId() {
            return cloneBytes(requestId);
        }
    }

    public record SenderReady(String clientNonce, UUID playerUuid, UUID clientModSessionUuid,
            long requestCounter, Sender sender, String streamId, byte[] requestId,
            byte[] ackEnvelopeSha256) implements Request {
        public SenderReady {
            requestId = cloneBytes(requestId);
            ackEnvelopeSha256 = cloneBytes(ackEnvelopeSha256);
        }

        @Override
        public byte[] requestId() {
            return cloneBytes(requestId);
        }

        @Override
        public byte[] ackEnvelopeSha256() {
            return cloneBytes(ackEnvelopeSha256);
        }
    }

    public record SenderReadyConfirm(String clientNonce, UUID playerUuid, UUID clientModSessionUuid,
            Sender sender, String streamId, byte[] readyRequestId, long readyRequestCounter,
            byte[] ackEnvelopeSha256) implements Request {
        public SenderReadyConfirm {
            readyRequestId = cloneBytes(readyRequestId);
            ackEnvelopeSha256 = cloneBytes(ackEnvelopeSha256);
        }

        @Override
        public byte[] readyRequestId() {
            return cloneBytes(readyRequestId);
        }

        @Override
        public byte[] ackEnvelopeSha256() {
            return cloneBytes(ackEnvelopeSha256);
        }
    }
}
