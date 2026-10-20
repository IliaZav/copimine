package me.copimine.endevent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class BoundedBridgeUtfReaderTest {
    public static void main(String[] args) throws Exception {
        rejectsOversizedDeclaredLengthBeforeReadingPayload();
        acceptsEmptyAsciiAndModifiedUtfWithinCharacterBound();
        rejectsDecodedTextAboveCharacterBound();
        rejectsMalformedModifiedUtf();
        rejectsNegativeCharacterBound();
        System.out.println("BoundedBridgeUtfReaderTest OK");
    }

    private static void rejectsOversizedDeclaredLengthBeforeReadingPayload() throws Exception {
        byte[] message = new byte[]{(byte) 0xff, (byte) 0xff, 1, 2, 3};
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(message));
        expectIOException(() -> read(input, 8), "byte bound");
        if (input.available() != 3) {
            throw new AssertionError("Oversized UTF body was consumed before its declared length was rejected");
        }
    }

    private static void acceptsEmptyAsciiAndModifiedUtfWithinCharacterBound() throws Exception {
        assertEquals("", read(encode(""), 0));
        assertEquals("hello", read(encode("hello"), 5));
        assertEquals("雪é", read(encode("雪é"), 2));
        assertEquals("\u0000", read(encode("\u0000"), 1));
    }

    private static void rejectsDecodedTextAboveCharacterBound() throws Exception {
        expectIOException(() -> read(encode("abc"), 2), "character bound");
    }

    private static void rejectsMalformedModifiedUtf() throws Exception {
        byte[] malformed = new byte[]{0, 2, (byte) 0xc0, 0x01};
        expectIOException(() -> read(malformed, 1), null);
    }

    private static void rejectsNegativeCharacterBound() throws Exception {
        expectIOException(() -> read(encode(""), -1), "non-negative");
    }

    private static byte[] encode(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(value);
        }
        return bytes.toByteArray();
    }

    private static String read(byte[] bytes, int maximum) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return read(input, maximum);
        }
    }

    private static String read(DataInputStream input, int maximum) throws IOException {
        return BoundedBridgeUtfReader.read(input, maximum);
    }

    private static void expectIOException(IoOperation operation, String messageFragment) throws Exception {
        try {
            operation.run();
            throw new AssertionError("Expected IOException");
        } catch (IOException expected) {
            if (messageFragment != null && (expected.getMessage() == null
                    || !expected.getMessage().contains(messageFragment))) {
                throw new AssertionError("Unexpected IOException message: " + expected.getMessage());
            }
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected [" + expected + "], got [" + actual + "]");
        }
    }

    @FunctionalInterface
    private interface IoOperation {
        void run() throws Exception;
    }
}
