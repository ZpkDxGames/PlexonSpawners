package com.plexon.spawners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SpawnersStabilizationContractTest {
    private static String source(final String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/plexon/spawners").resolve(relative));
    }

    @Test
    void managedSpawnerItemIdentitySupportsLegacySchemaAndTieredSchema() throws Exception {
        final String items = source("item/SpawnerItemService.java");
        assertTrue(items.contains("new NamespacedKey(plugin, \"managed_spawner\")"));
        assertTrue(items.contains("new NamespacedKey(plugin, \"spawner_type\")"));
        assertTrue(items.contains("new NamespacedKey(plugin, \"spawner_schema\")"));
        assertTrue(items.contains("new NamespacedKey(plugin, \"spawner_tier\")"));
        assertTrue(items.contains("CURRENT_SCHEMA = 2"));
        assertTrue(items.contains("MIN_SUPPORTED_SCHEMA = 1"));
        assertTrue(items.contains("schema < MIN_SUPPORTED_SCHEMA || schema > CURRENT_SCHEMA"));
        assertTrue(items.contains("schema == null || schema == 1"));
        assertTrue(items.contains("if (!hasManagedMarker(pdc))"));
    }

    @Test
    void placementUsesTwoPhaseApplyAndCommit() throws Exception {
        final String place = source("listener/SpawnerPlaceListener.java");
        assertTrue(place.contains("EventPriority.HIGHEST"));
        assertTrue(place.contains("EventPriority.MONITOR"));
        assertTrue(place.contains("pendingPlacements"));
        assertTrue(place.contains("readSpawnerType(event.getItemInHand())"));
        assertTrue(place.contains("stateService.apply(spawner, record, tuning.tier(tier))"));
        assertTrue(place.contains("registerStandalone(pending)"));
        assertTrue(place.contains("registry.findAutoStackTarget"));
        assertTrue(place.contains("NativeStackPolicy.merge"));
        assertTrue(place.contains("pending.withStackAmount(merge.remainder())"));
    }

    @Test
    void nativeBreakPreventsDuplicateDropsAndPreservesTier() throws Exception {
        final String breaking = source("listener/SpawnerBreakListener.java");
        assertTrue(breaking.contains("event.setCancelled(true)"));
        assertTrue(breaking.contains("event.setDropItems(false)"));
        assertTrue(breaking.contains("dropSpawnerWhenQualified"));
        assertTrue(breaking.contains("createSpawnerStacks(type, amount, tier)"));
        assertTrue(breaking.contains("registry.updateStackAmount"));
        assertTrue(breaking.contains("registry.remove(managed.id())"));
        assertTrue(breaking.contains("managed.migrationState().blocksMutation()"));
        // WildStacker remains only as the legacy/unmanaged provider path.
        assertTrue(breaking.contains("handleLegacyProviderBreak"));
        assertTrue(breaking.contains("WildStackerCompat.Result.SUCCESS"));
    }

    @Test
    void inventoryFullEssenceFallsBackToGroundWithoutLoss() throws Exception {
        final String breaking = source("listener/SpawnerBreakListener.java");
        assertTrue(breaking.contains("player.getInventory().addItem(stacks)"));
        assertTrue(breaking.contains("leftovers.values().forEach"));
        assertTrue(breaking.contains("dropItemNaturally(sourceLocation, leftover)"));
    }

    @Test
    void highFrequencyListenersContainNoFileOrDatabaseIo() throws Exception {
        for (final String path : new String[]{
            "listener/SpawnerBreakListener.java",
            "listener/SpawnerPlaceListener.java",
            "listener/SpawnerProvenanceListener.java"
        }) {
            final String listener = source(path);
            assertFalse(listener.contains("java.sql"));
            assertFalse(listener.contains("Files."));
            assertFalse(listener.contains("FileInputStream"));
            assertFalse(listener.contains("FileOutputStream"));
            assertFalse(listener.contains("saveConfig("));
            assertFalse(listener.contains("loadConfig("));
        }
    }

    @Test
    void reloadInvalidatesManagedItemTemplateCacheAndProviderRefreshIsCentralized() throws Exception {
        final String items = source("item/SpawnerItemService.java");
        final String plugin = source("PlexonSpawners.java");
        assertTrue(items.contains("templateCache.clear()"));
        assertTrue(plugin.contains("spawnerItemService.reload()"));
        assertTrue(plugin.contains("wildStackerCompat.refresh()"));
    }
}
