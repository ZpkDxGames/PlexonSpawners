package com.plexon.spawners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WildStackerRedstoneRuntimeContractTest {
    private static String source(final String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/plexon/spawners").resolve(relative));
    }

    @Test
    void granularCapPreservesWildStackerOutputAndTrimsPendingLogicalStack() throws Exception {
        final String compat = source("compat/WildStackerCompat.java");
        final String listener = source("listener/NearbyStackCapListener.java");

        assertTrue(compat.contains("getStackedEntity"));
        assertTrue(compat.contains("setStackAmount\", int.class, boolean.class"));
        assertTrue(compat.contains("resizeLogicalEntity"));
        assertTrue(listener.contains("applyGranularStackBudget"));
        assertTrue(listener.contains("wildStacker.resizeLogicalEntity(pendingEntity, allowed)"));
        assertTrue(listener.contains("return true;\n    }\n\n    /** Called reflectively before WildStacker's direct"));
        assertFalse(listener.contains("cycle.remainingCapacity--;"));
    }

    @Test
    void redstoneLockFreezesAndRestoresCountdownWithCrashRecoveryMetadata() throws Exception {
        final String service = source("managed/RedstoneSpawnerLockService.java");

        assertTrue(service.contains("HOLD_DELAY = 32_000"));
        assertTrue(service.contains("new NamespacedKey(plugin, \"redstone_frozen_delay\")"));
        assertTrue(service.contains("spawner.setDelay(HOLD_DELAY)"));
        assertTrue(service.contains("spawner.setDelay(Math.max(0, delay))"));
        assertTrue(service.contains("event.setShouldAbortSpawn(true)"));
        assertTrue(service.contains("runTaskTimer"));
        assertTrue(service.contains("registry.entriesInChunk"));
        assertFalse(service.contains("getEntities()"));
    }

    @Test
    void controlGuiExposesLiveCountdownLockAndStackOutput() throws Exception {
        final String gui = source("gui/SpawnerControlGui.java");

        assertTrue(gui.contains("Next spawn"));
        assertTrue(gui.contains("nextSpawnTicks(record)"));
        assertTrue(gui.contains("Redstone lock"));
        assertTrue(gui.contains("Stack output"));
        assertTrue(gui.contains("Click to refresh live values."));
    }

    @Test
    void schemaSevenMaterializesRedstoneControls() throws Exception {
        final String plugin = source("PlexonSpawners.java");

        assertTrue(plugin.contains("configVersion < 7"));
        assertTrue(plugin.contains("managed.redstone-lock.enabled"));
        assertTrue(plugin.contains("managed.redstone-lock.poll-interval-ticks"));
        assertTrue(plugin.contains("contains(\"managed.redstone-lock.enabled\", true)"));
    }
}
