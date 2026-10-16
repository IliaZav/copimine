package me.copimine.protocol;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BridgeControlBodyCodecNegativeTest {
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000022");

    @Test
    void type254ConfirmReceiptRejectsEveryTruncationAndTrailingBytes() {
        byte[] requestId = new byte[BridgeControlBodyCodec.REQUEST_ID_BYTES];
        byte[] confirmDigest = new byte[BridgeControlBodyCodec.SHA256_BYTES];
        byte[] encoded = BridgeControlBodyCodec.encode(new BridgeControlBodyCodec.SenderReadyConfirmAck(
                BridgeRequestCodec.Sender.END_EVENT, SESSION, requestId, 4L, confirmDigest));

        for (int length = 0; length < encoded.length; length++) {
            byte[] truncated = Arrays.copyOf(encoded, length);
            assertThrows(IllegalArgumentException.class, () -> BridgeControlBodyCodec.decode(truncated),
                    "truncated type-254 body at length " + length);
        }

        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IllegalArgumentException.class, () -> BridgeControlBodyCodec.decode(trailing));
    }
}
