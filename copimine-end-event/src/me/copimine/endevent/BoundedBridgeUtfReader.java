package me.copimine.endevent;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** Reads one modified-UTF field while bounding its encoded bytes before decoding. */
final class BoundedBridgeUtfReader {
    private static final long MAX_MODIFIED_UTF_BYTES_PER_CHARACTER = 3L;
    private static final long MAX_MODIFIED_UTF_BYTES = 65_535L;

    private BoundedBridgeUtfReader() {
    }

    static String read(DataInputStream input, int maximumCharacters) throws IOException {
        if (maximumCharacters < 0) {
            throw new IOException("Bridge field character bound must be non-negative");
        }

        int encodedLength = input.readUnsignedShort();
        long maximumEncodedLength = Math.min(MAX_MODIFIED_UTF_BYTES,
                (long) maximumCharacters * MAX_MODIFIED_UTF_BYTES_PER_CHARACTER);
        if (encodedLength > maximumEncodedLength) {
            throw new IOException("Bridge field exceeds byte bound: " + maximumEncodedLength);
        }

        byte[] framed = new byte[encodedLength + 2];
        framed[0] = (byte) (encodedLength >>> 8);
        framed[1] = (byte) encodedLength;
        input.readFully(framed, 2, encodedLength);

        String value;
        try (DataInputStream decoder = new DataInputStream(new ByteArrayInputStream(framed))) {
            value = decoder.readUTF();
        }
        if (value.length() > maximumCharacters) {
            throw new IOException("Bridge field exceeds character bound: " + maximumCharacters);
        }
        return value;
    }
}
