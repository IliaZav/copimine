package me.copimine.protocol;

import java.util.Objects;
import java.util.UUID;

/** Immutable v4 signed bridge envelope data. Arrays are defensively copied at the boundary. */
public record BridgeEnvelope(Metadata metadata, byte[] bodyBytes, byte[] signature) {
    public BridgeEnvelope {
        Objects.requireNonNull(metadata, "metadata");
        bodyBytes = bodyBytes == null ? null : bodyBytes.clone();
        signature = signature == null ? null : signature.clone();
    }

    @Override
    public byte[] bodyBytes() {
        return bodyBytes == null ? null : bodyBytes.clone();
    }

    @Override
    public byte[] signature() {
        return signature == null ? null : signature.clone();
    }

    /** Fields covered by the v4 signature, in the order defined by the wire protocol. */
    public record Metadata(
            int protocolVersion,
            String keyId,
            String clientNonce,
            UUID recipientUuid,
            String streamId,
            long counter,
            long issuedAtMillis,
            long expiresAtMillis) {
    }
}
