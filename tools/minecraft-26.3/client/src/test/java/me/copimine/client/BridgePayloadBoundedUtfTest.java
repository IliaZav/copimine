package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgePayloadBoundedUtfTest {
    @Test
    void decodesModifiedUtfWithinItsEncodedByteBudget() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF("event-α");
        }

        String value = BridgePayload.readBoundedUTF(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
                "sessionId", 32);

        assertEquals("event-α", value);
    }

    @Test
    void rejectsOversizedModifiedUtfBeforeReadingOrDecodingItsBody() throws IOException {
        byte[] prefixOnly = {(byte) 0x04, (byte) 0x01};
        CountingInputStream input = new CountingInputStream(prefixOnly);

        assertThrows(IllegalArgumentException.class, () -> BridgePayload.readBoundedUTF(
                new DataInputStream(input), "sessionId", 1_024));

        assertEquals(2, input.consumedBytes());
    }

    private static final class CountingInputStream extends ByteArrayInputStream {
        private int consumedBytes;

        private CountingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read() {
            int result = super.read();
            if (result >= 0) consumedBytes++;
            return result;
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            int read = super.read(bytes, offset, length);
            if (read > 0) consumedBytes += read;
            return read;
        }

        private int consumedBytes() {
            return consumedBytes;
        }
    }
}
