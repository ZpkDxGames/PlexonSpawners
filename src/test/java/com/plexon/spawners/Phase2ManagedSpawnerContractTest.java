package com.plexon.spawners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase2ManagedSpawnerContractTest {
    private static String source(final String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/plexon/spawners").resolve(relative));
    }

    @Test
    void persistenceUsesOneBoundedCoordinatorAndChunkIndexes() throws Exception {
        final String registry = source("managed/ManagedSpawnerRegistry.java");
        final String plugin = source("PlexonSpawners.java");
        assertTrue(registry.contains("Executors.newSingleThreadExecutor"));
        assertTrue(registry.contains("ConcurrentMap<ChunkKey, Set<UUID>> byChunk"));
        assertTrue(registry.contains("writerRunning.compareAndSet(false, true)"));
        assertTrue(registry.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(plugin.contains("runTaskTimer"));
        assertTrue(plugin.contains("managedRegistry::flushAsync"));
        assertFalse(source("listener/SpawnerPlaceListener.java").contains("runTaskTimer"));
        assertFalse(source("listener/SpawnerBreakListener.java").contains("runTaskTimer"));
        assertFalse(source("listener/SpawnerProvenanceListener.java").contains("runTaskTimer"));
    }

    @Test
    void provenanceIsPdcBackedAndChunkBounded() throws Exception {
        final String origin = source("managed/SpawnerOriginService.java");
        final String listener = source("listener/SpawnerProvenanceListener.java");
        assertTrue(origin.contains("new NamespacedKey(plugin, \"spawner_origin\")"));
        assertTrue(origin.contains("new NamespacedKey(plugin, \"spawner_origin_source\")"));
        assertTrue(listener.contains("CreatureSpawnEvent.SpawnReason.SPAWNER"));
        assertTrue(listener.contains("registry.nearest("));
        assertTrue(listener.contains("originService.mark(event.getEntity(), source.id())"));
        assertTrue(listener.contains("registry.incrementSpawnCount(source.id())"));
        assertFalse(listener.contains("getNearbyEntities"));
    }

    @Test
    void ownershipAndAccessAreEnforcedBeforeManagedBreaks() throws Exception {
        final String access = source("managed/SpawnerAccess.java");
        final String breaking = source("listener/SpawnerBreakListener.java");
        assertTrue(access.contains("boolean canBreak"));
        assertTrue(access.contains("this == PUBLIC"));
        assertTrue(breaking.contains("managed.access().canBreak"));
        assertTrue(breaking.contains("plexonspawners.bypass.access"));
        assertTrue(breaking.contains("event.setCancelled(true)"));
    }

    @Test
    void upgradeFlowReservesAppliesRollsBackAndRefunds() throws Exception {
        final String gui = source("gui/SpawnerControlGui.java");
        assertTrue(gui.contains("countEssence(player) < cost"));
        assertTrue(gui.contains("consumeEssence(player, cost)"));
        assertTrue(gui.contains("registry.updateTier(current.id(), nextTier.level())"));
        assertTrue(gui.contains("registry.updateTier(current.id(), current.tier())"));
        assertTrue(gui.contains("refundEssence(player, cost)"));
        assertTrue(gui.contains("InventoryDragEvent"));
    }

    @Test
    void reconciliationOnlyWalksIndexedManagedRecords() throws Exception {
        final String chunk = source("listener/SpawnerChunkListener.java");
        assertTrue(chunk.contains("registry.entriesInChunk("));
        assertTrue(chunk.contains("registry.snapshot()"));
        assertTrue(chunk.contains("world.isChunkLoaded"));
        assertFalse(chunk.contains("getTileEntities"));
        assertFalse(chunk.contains("getEntities"));
    }
}
