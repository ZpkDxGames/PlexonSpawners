package com.plexon.spawners.gui.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AdminConfigPersistenceContractTest {
    @Test
    void saveUsesTwoPhaseBoundedIoAndPrimaryCommit() throws IOException {
        final String source = source();
        final int validation = source.indexOf("session.draft().validate()");
        final int immutable = source.indexOf("session.draft().copy()");
        final int io = source.indexOf("plugin.coreBridge().supplyIo");
        final int prepare = source.indexOf("prepareIo(baseYaml, immutableDraft)");
        final int primary = source.indexOf("plugin.coreBridge().runPrimary");
        final int runtimePrepare = source.indexOf("plugin.prepareRuntimeCandidate");
        final int runtimeCommit = source.indexOf("plugin.commitRuntime");
        final int revision = source.indexOf("revisions.bump()");

        assertTrue(validation >= 0);
        assertTrue(immutable > validation);
        assertTrue(io > immutable);
        assertTrue(prepare > io);
        assertTrue(primary > prepare);
        assertTrue(runtimePrepare > primary);
        assertTrue(runtimeCommit > runtimePrepare);
        assertTrue(revision > runtimeCommit);
    }

    @Test
    void filesystemMutationOccursOnlyInsideIoPreparationOrRollback() throws IOException {
        final String source = source();
        assertTrue(source.contains("Files.createDirectories"));
        assertTrue(source.contains("createBackup(config)"));
        assertTrue(source.contains("Files.createTempFile"));
        assertTrue(source.contains("replaceAtomically(temporary, config)"));
        assertTrue(source.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(source.contains("rollbackAsync"));
        assertFalse(source.contains("plugin.reloadConfig()"));
        assertFalse(source.contains("plugin.saveConfig()"));
    }

    @Test
    void saveIsSingleFlightAndGenerationChecked() throws IOException {
        final String source = source();
        assertTrue(source.contains("AtomicBoolean saveInFlight"));
        assertTrue(source.contains("saveInFlight.compareAndSet(false, true)"));
        assertTrue(source.contains("session.sourceRevision() != revisions.current()"));
        assertTrue(source.contains("session.sourceGeneration() != plugin.runtimeGeneration()"));
        assertTrue(source.contains("expectedGeneration != plugin.runtimeGeneration()"));
        assertTrue(source.contains("Status.BUSY"));
        assertTrue(source.contains("Status.STALE"));
    }

    @Test
    void runtimeFailureRollsDiskBackWithoutRevisionAdvance() throws IOException {
        final String source = source();
        final int commit = source.indexOf("plugin.commitRuntime");
        final int revision = source.indexOf("revisions.bump()", commit);
        final int catchBlock = source.indexOf("catch (final Exception exception)", revision);
        final int rollback = source.indexOf("rollbackAsync(prepared", catchBlock);
        assertTrue(commit >= 0 && revision > commit);
        assertTrue(catchBlock > revision);
        assertTrue(rollback > catchBlock);
    }

    private static String source() throws IOException {
        return Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/gui/admin/AdminConfigPersistence.java"));
    }
}
