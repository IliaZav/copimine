package me.copimine.protocol;

import java.util.Optional;
import java.util.UUID;

/** Shared Paper service that returns a complete, encoded signed v4 envelope or no result. */
public interface BridgeSigner extends AutoCloseable {
    Optional<byte[]> sign(
            String nonce,
            UUID recipient,
            String streamId,
            long counter,
            long issuedAtMillis,
            long expiresAtMillis,
            byte[] exactBody);

    /** Implementations with no external secret can retain the default no-op shutdown behavior. */
    @Override
    default void close() {
    }

    /** A fail-closed service value for startup without a trusted external key. */
    static BridgeSigner unavailable() {
        return (nonce, recipient, streamId, counter, issuedAtMillis, expiresAtMillis, exactBody) -> Optional.empty();
    }
}
