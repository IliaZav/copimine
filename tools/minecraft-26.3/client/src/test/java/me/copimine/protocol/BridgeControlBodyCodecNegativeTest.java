package me.copimine.protocol;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BridgeControlBodyCodecNegativeTest {
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000022");

    @Test
    void heartbeatAndHelloAckRejectMalformedBodiesAndUnknownFeatures() {
        byte[] heartbeat = new byte[] {1, (byte) 253, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        for (int length = 0; length < heartbeat.length; length++) {
            byte[] truncated = Arrays.copyOf(heartbeat, length);
            assertThrows(IllegalArgumentException.class, () -> BridgeControlBodyCodec.decode(truncated));
        }
        assertThrows(IllegalArgumentException.class,
                () -> BridgeControlBodyCodec.decode(Arrays.copyOf(heartbeat, heartbeat.length + 1)));
        byte[] wrongSchema = heartbeat.clone();
        wrongSchema[0] = 2;
        assertThrows(IllegalArgumentException.class, () -> BridgeControlBodyCodec.decode(wrongSchema));
        byte[] wrongSender = heartbeat.clone();
        wrongSender[2] = 3;
        assertThrows(IllegalArgumentException.class, () -> BridgeControlBodyCodec.decode(wrongSender));

        byte[] extendedHello = new byte[37];
        extendedHello[0] = 1;
        extendedHello[1] = 0;
        extendedHello[2] = 1;
        extendedHello[19] = 0;
        extendedHello[36] = 2;
        assertThrows(IllegalArgumentException.class, () -> BridgeControlBodyCodec.decode(extendedHello));
        assertThrows(IllegalArgumentException.class, () -> BridgeControlBodyCodec.encode(
                new BridgeControlBodyCodec.HelloAck(BridgeRequestCodec.Sender.END_EVENT, SESSION, 0,
                        new byte[16], 2)));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeControlBodyCodec.decode(Arrays.copyOf(extendedHello, 38)));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeControlBodyCodec.decode(Arrays.copyOf(extendedHello, 35)));
    }

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
