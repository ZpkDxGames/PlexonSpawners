package com.plexon.spawners.gui.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AdminConfigPersistenceContractTest {
    @Test
    void staleAndValidationChecksOccurBeforeFilesystemMutation() throws IOException {
        final String source = source();
        final int stale = source.indexOf("session.sourceRevision() != revisions.current()");
        final int validation = source.indexOf("session.draft().validate()");
        final int configPath = source.indexOf("plugin.getDataFolder().toPath().resolve(\"config.yml\")");
        final int createDirectories = source.indexOf("Files.createDirectories(plugin.getDataFolder().toPath())");

        assertTrue(stale >= 0 && validation > stale);
        assertTrue(configPath > validation);
        assertTrue(createDirectories > configPath);
    }

    @Test
    void saveCreatesBackupAndUsesAtomicReplacementBeforeReload() throws IOException {
        final String source = source();
        final int backup = source.indexOf("backup = createBackup(config)");
        final int temp = source.indexOf("Files.createTempFile");
        final int replace = source.indexOf("replaceAtomically(temporary, config)");
        final int reload = source.indexOf("plugin.reloadRuntime()", replace);

        assertTrue(backup >= 0);
        assertTrue(temp > backup);
        assertTrue(replace > temp);
        assertTrue(reload > replace);
        assertTrue(source.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(source.contains("catch (final AtomicMoveNotSupportedException exception)"));
        assertTrue(source.contains("Files.move(temporary, config, StandardCopyOption.REPLACE_EXISTING)"));
    }

    @Test
    void failedRuntimeReloadRestoresBackupBeforeReturningFailure() throws IOException {
        final String source = source();
        final int reloadFailure = source.indexOf("catch (final RuntimeException reloadFailure)");
        final int restore = source.indexOf("Files.copy(backup, config, StandardCopyOption.REPLACE_EXISTING", reloadFailure);
        final int rollbackReload = source.indexOf("plugin.reloadRuntime()", restore);
        final int failedResult = source.indexOf("new SaveResult(Status.FAILED", rollbackReload);

        assertTrue(reloadFailure >= 0);
        assertTrue(restore > reloadFailure);
        assertTrue(rollbackReload > restore);
        assertTrue(failedResult > rollbackReload);
    }

    @Test
    void successfulSaveBumpsRevisionOnlyAfterRuntimeReload() throws IOException {
        final String source = source();
        final int replace = source.indexOf("replaceAtomically(temporary, config)");
        final int reload = source.indexOf("plugin.reloadRuntime()", replace);
        final int revision = source.indexOf("final long revision = revisions.bump()", reload);
        final int clean = source.indexOf("session.markClean(revision)", revision);
        final int saved = source.indexOf("new SaveResult(Status.SAVED", clean);

        assertTrue(replace >= 0);
        assertTrue(reload > replace);
        assertTrue(revision > reload);
        assertTrue(clean > revision);
        assertTrue(saved > clean);
    }

    private static String source() throws IOException {
        return Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/gui/admin/AdminConfigPersistence.java"));
    }
}
