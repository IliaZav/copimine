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

    /** Whether the configured trusted signing capability is currently available. */
    default boolean isAvailable() {
        return true;
    }

    /** Implementations with no external secret can retain the default no-op shutdown behavior. */
    @Override
    default void close() {
    }

    /** A fail-closed service value for startup without a trusted external key. */
    static BridgeSigner unavailable() {
        return new BridgeSigner() {
            @Override
            public Optional<byte[]> sign(String nonce, UUID recipient, String streamId, long counter,
                    long issuedAtMillis, long expiresAtMillis, byte[] exactBody) {
                return Optional.empty();
            }

            @Override
            public boolean isAvailable() {
                return false;
            }
        };
    }
}
