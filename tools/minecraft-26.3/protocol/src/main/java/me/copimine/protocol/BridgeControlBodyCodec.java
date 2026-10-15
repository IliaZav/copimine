package me.copimine.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/** Strict codecs for the two common v4 bridge-control acknowledgement bodies. */
public final class BridgeControlBodyCodec {
    public static final int SCHEMA = 1;
    public static final int HELLO_ACK_TYPE = 0;
    public static final int SENDER_READY_ACK_TYPE = 255;
    public static final int REQUEST_ID_BYTES = 16;

    private static final int HELLO_ACK_BYTES = 1 + 1 + 1 + 16 + 1 + REQUEST_ID_BYTES;
    private static final int SENDER_READY_ACK_BYTES = 1 + 1 + 1 + 16 + REQUEST_ID_BYTES + 8;

    private BridgeControlBodyCodec() {
    }

    public static byte[] encode(Body body) {
        if (body instanceof HelloAck helloAck) {
            requireSender(helloAck.sender());
            requireUuid(helloAck.sessionUuid());
            requireRequestId(helloAck.requestId());
            if (helloAck.requestKind() != 0 && helloAck.requestKind() != 1) {
                throw new IllegalArgumentException("Unknown hello_ack request kind");
            }
            return write(output -> {
                output.writeByte(SCHEMA);
                output.writeByte(HELLO_ACK_TYPE);
                output.writeByte(helloAck.sender().id());
                writeUuid(output, helloAck.sessionUuid());
                output.writeByte(helloAck.requestKind());
                output.write(helloAck.requestId());
            }, HELLO_ACK_BYTES);
        }
        if (body instanceof SenderReadyAck readyAck) {
            requireSender(readyAck.sender());
            requireUuid(readyAck.sessionUuid());
            requireRequestId(readyAck.requestId());
            if (readyAck.readyRequestCounter() <= 0) {
                throw new IllegalArgumentException("sender_ready_ack counter must be positive");
            }
            return write(output -> {
                output.writeByte(SCHEMA);
                output.writeByte(SENDER_READY_ACK_TYPE);
                output.writeByte(readyAck.sender().id());
                writeUuid(output, readyAck.sessionUuid());
                output.write(readyAck.requestId());
                output.writeLong(readyAck.readyRequestCounter());
            }, SENDER_READY_ACK_BYTES);
        }
        throw new IllegalArgumentException("Unknown bridge control body type");
    }

    public static Body decode(byte[] encoded) {
        if (encoded == null || encoded.length < 2) {
            throw new IllegalArgumentException("Invalid bridge control body length");
        }
        ByteBuffer input = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        int schema = readU8(input);
        int type = readU8(input);
        if (schema != SCHEMA) {
            throw new IllegalArgumentException("Unsupported bridge control body schema");
        }
        Body body;
        if (type == HELLO_ACK_TYPE) {
            requireExactLength(encoded, HELLO_ACK_BYTES);
            BridgeRequestCodec.Sender sender = readSender(input);
            UUID sessionUuid = readUuid(input);
            int requestKind = readU8(input);
            if (requestKind != 0 && requestKind != 1) {
                throw new IllegalArgumentException("Unknown hello_ack request kind");
            }
            body = new HelloAck(sender, sessionUuid, requestKind, readRequestId(input));
        } else if (type == SENDER_READY_ACK_TYPE) {
            requireExactLength(encoded, SENDER_READY_ACK_BYTES);
            BridgeRequestCodec.Sender sender = readSender(input);
            UUID sessionUuid = readUuid(input);
            byte[] requestId = readRequestId(input);
            long requestCounter = readLong(input);
            if (requestCounter <= 0) {
                throw new IllegalArgumentException("sender_ready_ack counter must be positive");
            }
            body = new SenderReadyAck(sender, sessionUuid, requestId, requestCounter);
        } else {
            throw new IllegalArgumentException("Unknown bridge control body type");
        }
        if (input.hasRemaining()) {
            throw new IllegalArgumentException("Trailing bridge control body bytes");
        }
        return body;
    }

    private static byte[] write(Writer writer, int expectedLength) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(expectedLength);
            DataOutputStream output = new DataOutputStream(bytes);
            writer.write(output);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length != expectedLength) {
                throw new IllegalStateException("Invalid internal bridge control body length");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode bridge control body", exception);
        }
    }

    private static void requireExactLength(byte[] encoded, int expectedLength) {
        if (encoded.length != expectedLength) {
            throw new IllegalArgumentException("Invalid bridge control body length");
        }
    }

    private static BridgeRequestCodec.Sender readSender(ByteBuffer input) {
        return switch (readU8(input)) {
            case 1 -> BridgeRequestCodec.Sender.END_EVENT;
            case 2 -> BridgeRequestCodec.Sender.NARCOTICS;
            default -> throw new IllegalArgumentException("Unknown bridge sender ID");
        };
    }

    private static UUID readUuid(ByteBuffer input) {
        if (input.remaining() < 16) {
            throw new IllegalArgumentException("Truncated bridge control UUID");
        }
        return new UUID(input.getLong(), input.getLong());
    }

    private static byte[] readRequestId(ByteBuffer input) {
        if (input.remaining() < REQUEST_ID_BYTES) {
            throw new IllegalArgumentException("Truncated bridge control request ID");
        }
        byte[] requestId = new byte[REQUEST_ID_BYTES];
        input.get(requestId);
        return requestId;
    }

    private static long readLong(ByteBuffer input) {
        if (input.remaining() < Long.BYTES) {
            throw new IllegalArgumentException("Truncated bridge control counter");
        }
        return input.getLong();
    }

    private static int readU8(ByteBuffer input) {
        if (!input.hasRemaining()) {
            throw new IllegalArgumentException("Truncated bridge control field");
        }
        return Byte.toUnsignedInt(input.get());
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        output.writeLong(uuid.getMostSignificantBits());
        output.writeLong(uuid.getLeastSignificantBits());
    }

    private static void requireSender(BridgeRequestCodec.Sender sender) {
        if (sender == null) {
            throw new IllegalArgumentException("Missing bridge control sender");
        }
    }

    private static void requireUuid(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("Missing bridge control session UUID");
        }
    }

    private static void requireRequestId(byte[] requestId) {
        if (requestId == null || requestId.length != REQUEST_ID_BYTES) {
            throw new IllegalArgumentException("Bridge control request ID must be 16 bytes");
        }
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }

    public sealed interface Body permits HelloAck, SenderReadyAck {
    }

    public record HelloAck(BridgeRequestCodec.Sender sender, UUID sessionUuid,
            int requestKind, byte[] requestId) implements Body {
        public HelloAck {
            requestId = requestId == null ? null : requestId.clone();
        }

        @Override
        public byte[] requestId() {
            return requestId == null ? null : requestId.clone();
        }
    }

    public record SenderReadyAck(BridgeRequestCodec.Sender sender, UUID sessionUuid,
            byte[] requestId, long readyRequestCounter) implements Body {
        public SenderReadyAck {
            requestId = requestId == null ? null : requestId.clone();
        }

        @Override
        public byte[] requestId() {
            return requestId == null ? null : requestId.clone();
        }
    }
}
