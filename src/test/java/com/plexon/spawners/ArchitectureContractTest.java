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
    void legacyManagedArchitectureIsAbsentFromSourceTree() throws IOException {
        final Path root = Path.of("src/main/java/com/plexon/spawners");
        try (var stream = Files.walk(root)) {
            final var paths = stream.filter(Files::isRegularFile).map(Path::toString).toList();
            assertFalse(paths.stream().anyMatch(path -> path.contains("/managed/") || path.contains("\\managed\\")));
            assertFalse(paths.stream().anyMatch(path -> path.endsWith("SpawnerPlaceListener.java")));
            assertFalse(paths.stream().anyMatch(path -> path.endsWith("SpawnerChunkListener.java")));
            assertFalse(paths.stream().anyMatch(path -> path.endsWith("PhysicalFallbackBackend.java")));
            assertFalse(paths.stream().anyMatch(path -> path.endsWith("WildStackerCompat.java")));
        }
    }
}
