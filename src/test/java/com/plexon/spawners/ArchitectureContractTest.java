package com.plexon.spawners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArchitectureContractTest {
    @Test
    void wildStackerIsHardDependencyAndPublicApiIsPinned() throws IOException {
        final String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        final String build = Files.readString(Path.of("build.gradle.kts"));
        assertTrue(pluginYml.contains("depend:\n  - WildStacker"));
        assertFalse(pluginYml.contains("softdepend:"));
        assertTrue(build.contains("com.bgsoftware:WildStackerAPI:2026.2"));
        assertFalse(build.contains("PlexonCore"));
    }

    @Test
    void directApiBridgeHasNoReflectionOrFallbackEngine() throws IOException {
        final String bridge = Files.readString(Path.of("src/main/java/com/plexon/spawners/integration/WildStackerBridge.java"));
        assertTrue(bridge.contains("WildStackerAPI.getStackedSpawner"));
        assertTrue(bridge.contains("getDropItem(amount)"));
        assertTrue(bridge.contains("runUnstack(amount, player)"));
        assertFalse(bridge.contains("Class.forName"));
        assertFalse(bridge.contains("java.lang.reflect"));
    }

    @Test
    void breakListenerUsesWildStackerLogicalAmountAndPublicEvents() throws IOException {
        final String listener = Files.readString(Path.of("src/main/java/com/plexon/spawners/listener/SpawnerBreakListener.java"));
        assertTrue(listener.contains("SpawnerUnstackEvent"));
        assertTrue(listener.contains("SpawnerDropEvent"));
        assertTrue(listener.contains("event.getAmount()"));
        assertFalse(listener.contains("PersistentDataContainer"));
        assertFalse(listener.contains("getNearbyEntities"));
    }

    @Test
    void adminConfigDoesNotExposeWildStackerOwnedStackControls() throws IOException {
        final String config = Files.readString(Path.of("src/main/resources/config.yml"));
        assertFalse(config.contains("merge-radius:"));
        assertFalse(config.contains("stack-limit:"));
        assertFalse(config.contains("redstone-lock:"));
        assertFalse(config.contains("stack-tier:"));
        assertTrue(config.contains("admin-gui:"));
        assertTrue(config.contains("custom-drop:"));
        assertTrue(config.contains("non-silk-reward-mode:"));
    }

    @Test
    void legacyManagedArchitectureIsAbsentFromSourceTree() throws IOException {
        final Path root = Path.of("src/main/java/com/plexon/spawners");
        try (var stream = Files.walk(root)) {
            final var paths = stream.filter(Files::isRegularFile).map(Path::toString).toList();
            assertFalse(paths.stream().anyMatch(path -> path.contains("/managed/") || path.contains("\\managed\\")));
            assertFalse(paths.stream().anyMatch(path -> path.endsWith("SpawnerPlaceListener.java")));
            assertFalse(paths.stream().anyMatch(path -> path.endsWith("SpawnerChunkListener.java")));
            assertFalse(paths.stream().anyMatch(path -> path.endsWith("PhysicalFallbackBackend.java")));
            assertFalse(paths.stream().anyMatch(path -> path.endsWith("WildStackerCompat.java")));
            assertFalse(paths.stream().anyMatch(path -> path.endsWith("RedstoneSpawnerLockService.java")));
            assertFalse(paths.stream().anyMatch(path -> path.endsWith("SpawnerMigrationService.java")));
        }
    }
}
