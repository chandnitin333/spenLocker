package com.spendlocker.backup;

import com.spendlocker.db.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Zips/unzips the whole vault directory (encrypted database + documents + attachments).
 * A backup is only as safe as the master password protecting the vault file inside it.
 */
public class BackupService {

    public void exportBackup(File destinationZip) throws IOException {
        Path vaultDir = Path.of(DatabaseManager.vaultDirectory());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destinationZip.toPath()));
             Stream<Path> paths = Files.walk(vaultDir)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String entryName = vaultDir.relativize(path).toString().replace(File.separatorChar, '/');
                try {
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** Overwrites the current vault directory with the backup's contents. Caller must close the DB connection first. */
    public void restoreBackup(File sourceZip) throws IOException {
        Path vaultDir = Path.of(DatabaseManager.vaultDirectory());
        Files.createDirectories(vaultDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(sourceZip.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = vaultDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(vaultDir)) {
                    continue; // guard against a malicious zip entry escaping the vault directory
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
}
