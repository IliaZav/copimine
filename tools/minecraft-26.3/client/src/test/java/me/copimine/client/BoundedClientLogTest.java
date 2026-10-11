package me.copimine.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedClientLogTest {
    @TempDir
    Path tempDir;

    @Test
    void controlCharactersCannotCreateForgedLogRecords() throws Exception {
        Path log = tempDir.resolve("logs/copimineclient.log");
        BoundedClientLog.append(log, "server visual\r\n[FORGED]" + (char) 0x1b + "\u202Etail");

        List<String> lines = Files.readAllLines(log);
        assertEquals(1, lines.size());
        assertEquals("server visual\\r\\n[FORGED]  tail", lines.getFirst());
    }

    @Test
    void activeAndBackupLogsRemainWithinFixedByteLimits() throws Exception {
        Path log = tempDir.resolve("logs/copimineclient.log");
        String largeRecord = "界".repeat(8_000);
        for (int index = 0; index < 160; index++) {
            BoundedClientLog.append(log, largeRecord);
        }

        Path backup = log.resolveSibling(log.getFileName() + ".1");
        assertTrue(Files.size(log) <= BoundedClientLog.MAX_FILE_BYTES);
        assertTrue(Files.size(backup) <= BoundedClientLog.MAX_FILE_BYTES);
        assertTrue(Files.size(log) + Files.size(backup) <= 2L * BoundedClientLog.MAX_FILE_BYTES);
        for (String line : Files.readAllLines(log)) {
            assertTrue(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + System.lineSeparator().length()
                    <= BoundedClientLog.MAX_RECORD_BYTES);
        }
    }
}
