package me.copimine.clientbridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ClientReadyPayloads {
    public static final String READY_CHANNEL = "copimine:client_ready";
    public static final String ACK_CHANNEL = "copimine:client_ready_ack";
    public static final int MAX_PAYLOAD_BYTES = 256;
    public static final int MAX_CLIENT_VERSION_LENGTH = 64;
    public static final int MAX_REASON_LENGTH = 64;

    private ClientReadyPayloads() {
    }

    public static ClientReadyAdmission.ReadyRequest decodeReady(byte[] payload) throws MalformedReadyException {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new MalformedReadyException("MALFORMED_READY");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int protocol = input.readInt();
            String version = bounded(input.readUTF(), MAX_CLIENT_VERSION_LENGTH);
            if (version.isBlank() || input.available() != 0) {
                throw new MalformedReadyException("MALFORMED_READY");
            }
            return new ClientReadyAdmission.ReadyRequest(protocol, version);
        } catch (MalformedReadyException error) {
            throw error;
        } catch (Exception error) {
            throw new MalformedReadyException("MALFORMED_READY", error);
        }
    }

    public static byte[] encodeReady(int protocol, String clientVersion) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(protocol);
            output.writeUTF(bounded(clientVersion, MAX_CLIENT_VERSION_LENGTH));
            output.flush();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to encode CLIENT_READY", error);
        }
    }

    public static byte[] encodeAck(boolean accepted, int requiredProtocol, int receivedProtocol, String reason) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeBoolean(accepted);
            output.writeInt(requiredProtocol);
            output.writeInt(receivedProtocol);
            output.writeUTF(bounded(reason, MAX_REASON_LENGTH));
            output.flush();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to encode CLIENT_READY_ACK", error);
        }
    }

    private static String bounded(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    public static final class MalformedReadyException extends Exception {
        public MalformedReadyException(String message) {
            super(message);
        }

        public MalformedReadyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
