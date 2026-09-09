package com.plexon.spawners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SpawnersStabilizationContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/plexon/spawners").resolve(relative));
    }

    @Test
    void managedSpawnerIdentityIsNamespacedSchemaBoundAndFailClosed() throws Exception {
        String items = source("item/SpawnerItemService.java");
        assertTrue(items.contains("new NamespacedKey(plugin, \"managed_spawner\")"));
        assertTrue(items.contains("new NamespacedKey(plugin, \"spawner_type\")"));
        assertTrue(items.contains("new NamespacedKey(plugin, \"spawner_schema\")"));
        assertTrue(items.contains("schema != CURRENT_SCHEMA"));
        assertTrue(items.contains("if (!hasManagedMarker(pdc))"));
        assertTrue(items.contains("key == null || key.isBlank()"));
    }

    @Test
    void placementReadsManagedIdentityOnceAndRejectsUnknownMetadata() throws Exception {
        String place = source("listener/SpawnerPlaceListener.java");
        assertTrue(place.contains("readSpawnerType(event.getItemInHand())"));
        assertTrue(place.contains("if (type == null)"));
        assertTrue(place.contains("vanillaSpawnerPlacementReject"));
        assertTrue(place.contains("spawner.setSpawnedType(type)"));
    }

    @Test
    void breakPathPreventsVanillaAndManagedDuplicateDrops() throws Exception {
        String breaking = source("listener/SpawnerBreakListener.java");
        assertTrue(breaking.contains("event.setCancelled(true)"));
        assertTrue(breaking.contains("event.setDropItems(false)"));
        assertTrue(breaking.contains("dropSpawnerWhenQualified"));
        assertTrue(breaking.contains("createSpawner(entityType, 1)"));
        assertTrue(breaking.contains("WildStackerCompat.Result.SUCCESS"));
    }

    @Test
    void inventoryFullEssenceFallsBackToGroundWithoutLoss() throws Exception {
        String breaking = source("listener/SpawnerBreakListener.java");
        assertTrue(breaking.contains("player.getInventory().addItem(stacks)"));
        assertTrue(breaking.contains("leftovers.values().forEach"));
        assertTrue(breaking.contains("dropItemNaturally(sourceLocation, leftover)"));
    }

    @Test
    void placementAndBreakHotPathsContainNoFileOrDatabaseIo() throws Exception {
        for (String path : new String[]{"listener/SpawnerBreakListener.java", "listener/SpawnerPlaceListener.java"}) {
            String listener = source(path);
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
        String items = source("item/SpawnerItemService.java");
        String plugin = source("PlexonSpawners.java");
        assertTrue(items.contains("templateCache.clear()"));
        assertTrue(plugin.contains("spawnerItemService.reload()"));
        assertTrue(plugin.contains("wildStackerCompat.refresh()"));
    }
}
