package me.copimine.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShaderpackExportFileWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesNewPackWithoutCreatingBackup() throws IOException {
        Path target = tempDir.resolve("shaderpacks/CopiMine/acid.zip");
        byte[] bundled = {1, 2, 3};

        assertNull(ShaderpackExportFileWriter.writeIfChanged(
                target, bundled, tempDir.resolve("config/backups")));
        assertArrayEquals(bundled, Files.readAllBytes(target));
    }

    @Test
    void leavesIdenticalPackInPlaceWithoutCreatingBackup() throws IOException {
        Path target = tempDir.resolve("shaderpacks/copimine_acid.zip");
        byte[] bundled = {4, 5, 6};
        Files.createDirectories(target.getParent());
        Files.write(target, bundled);

        assertNull(ShaderpackExportFileWriter.writeIfChanged(
                target, bundled, tempDir.resolve("config/backups")));
        assertArrayEquals(bundled, Files.readAllBytes(target));
    }

    @Test
    void preservesModifiedPackBeforeInstallingBundledUpdate() throws IOException {
        Path target = tempDir.resolve("shaderpacks/copimine_acid.zip");
        Path backupRoot = tempDir.resolve("config/backups");
        byte[] userPack = {7, 8, 9};
        byte[] bundled = {10, 11, 12};
        Files.createDirectories(target.getParent());
        Files.write(target, userPack);

        Path backup = ShaderpackExportFileWriter.writeIfChanged(target, bundled, backupRoot);

        assertNotNull(backup);
        assertArrayEquals(userPack, Files.readAllBytes(backup));
        assertArrayEquals(bundled, Files.readAllBytes(target));
    }

    @Test
    void doesNotReplaceModifiedPackWhenBackupCannotBeWritten() throws IOException {
        Path target = tempDir.resolve("shaderpacks/copimine_acid.zip");
        Path backupRoot = tempDir.resolve("backup-root-is-a-file");
        byte[] userPack = {13, 14, 15};
        Files.createDirectories(target.getParent());
        Files.write(target, userPack);
        Files.writeString(backupRoot, "block");

        assertThrows(IOException.class, () -> ShaderpackExportFileWriter.writeIfChanged(
                target, new byte[]{16, 17, 18}, backupRoot));
        assertArrayEquals(userPack, Files.readAllBytes(target));
    }
}
