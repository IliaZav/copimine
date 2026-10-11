package me.copimine.client;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

final class ShaderpackExportFileWriter {
    private ShaderpackExportFileWriter() {
    }

    /** Returns the backup path when an existing, differing file was preserved. */
    static Path writeIfChanged(Path target, byte[] bytes, Path backupRoot) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refusing to replace non-regular shaderpack target: " + target);
            }
            byte[] existing = Files.readAllBytes(target);
            if (MessageDigest.isEqual(sha256(existing), sha256(bytes))) {
                return null;
            }
            Path backup = backupPath(target, existing, backupRoot);
            preserveBackup(backup, existing);
            writeAtomically(target, bytes, true);
            return backup;
        }
        writeAtomically(target, bytes, false);
        return null;
    }

    private static Path backupPath(Path target, byte[] existing, Path backupRoot) throws IOException {
        String targetKey = hex(sha256(target.toAbsolutePath().normalize().toString()
                .getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        String filename = target.getFileName().toString();
        String originalHash = hex(sha256(existing));
        return backupRoot.resolve(targetKey).resolve(filename + "." + originalHash + ".bak");
    }

    private static void preserveBackup(Path backup, byte[] existing) throws IOException {
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)
                    || !MessageDigest.isEqual(sha256(Files.readAllBytes(backup)), sha256(existing))) {
                throw new IOException("Existing shaderpack backup does not match its content address: " + backup);
            }
            return;
        }
        writeAtomically(backup, existing, false);
    }

    private static void writeAtomically(Path target, byte[] bytes, boolean replace) throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("Shaderpack target has no parent directory: " + target);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".copimine-shaderpack-", ".tmp");
        try {
            Files.write(temporary, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                if (replace) {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException ignored) {
                if (replace) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, target);
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] sha256(byte[] bytes) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xF, 16));
            result.append(Character.forDigit(value & 0xF, 16));
        }
        return result.toString();
    }
}
