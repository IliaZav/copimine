package me.copimine.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Writes single-line client diagnostics with a fixed active and backup budget. */
final class BoundedClientLog {
    static final long MAX_FILE_BYTES = 512L * 1024L;
    static final int MAX_RECORD_BYTES = 8 * 1024;
    private static final String BACKUP_SUFFIX = ".1";

    private BoundedClientLog() {
    }

    static void append(Path logPath, String message) throws IOException {
        byte[] newline = System.lineSeparator().getBytes(StandardCharsets.UTF_8);
        String safeMessage = sanitizeAndTruncate(message, MAX_RECORD_BYTES - newline.length);
        byte[] record = (safeMessage + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        Path backupPath = logPath.resolveSibling(logPath.getFileName() + BACKUP_SUFFIX);

        synchronized (BoundedClientLog.class) {
            Files.createDirectories(logPath.getParent());
            if (Files.exists(backupPath) && Files.size(backupPath) > MAX_FILE_BYTES) {
                Files.delete(backupPath);
            }
            if (Files.exists(logPath)) {
                long size = Files.size(logPath);
                if (size > MAX_FILE_BYTES) {
                    Files.delete(logPath);
                } else if (size + record.length > MAX_FILE_BYTES) {
                    Files.deleteIfExists(backupPath);
                    Files.move(logPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.write(logPath, record, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    static String sanitizeAndTruncate(String message, int maximumBytes) {
        String input = message == null ? "" : message;
        StringBuilder result = new StringBuilder(Math.min(input.length(), maximumBytes));
        int bytes = 0;
        for (int offset = 0; offset < input.length();) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);
            String safe = switch (codePoint) {
                case '\r' -> "\\r";
                case '\n' -> "\\n";
                case '\t' -> "\\t";
                default -> isControlOrLineSeparator(codePoint) ? " " : new String(Character.toChars(codePoint));
            };
            int safeBytes = safe.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + safeBytes > maximumBytes) {
                break;
            }
            result.append(safe);
            bytes += safeBytes;
        }
        return result.toString();
    }

    private static boolean isControlOrLineSeparator(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
