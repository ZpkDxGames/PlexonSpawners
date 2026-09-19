package com.plexon.spawners.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ApiAndCoreContractTest {
    @Test
    void apiIsRegisteredThroughServicesManager() throws IOException {
        final String plugin = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/PlexonSpawners.java"));
        assertTrue(plugin.contains("getServicesManager().register("));
        assertTrue(plugin.contains("PlexonSpawnersApi.class"));
        assertTrue(plugin.contains("getServicesManager().unregisterAll(this)"));
    }

    @Test
    void coreBridgeAdvertisesOnlyPolicyIntegrationCapabilities() throws IOException {
        final String core = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/integration/core/PlexonCoreBridge.java"));
        assertTrue(core.contains("\"wildstacker-authoritative\""));
        assertTrue(core.contains("\"spawner-break-policy\""));
        assertTrue(core.contains("\"spawner-reward-policy\""));
        assertTrue(core.contains("\"exact-reward-items\""));
        assertFalse(core.contains("\"spawner-engine\""));
        assertFalse(core.contains("\"managed-spawner-items\""));
        assertTrue(core.contains("supplyIo(plugin"));
        assertTrue(core.contains("schedulePrimary(plugin"));
        assertTrue(core.contains("unregisterOwnedBy(plugin)"));
    }

    @Test
    void standaloneBridgeIsBoundedAndOwnerLifecycleAware() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/integration/core/StandaloneCoreBridge.java"));
        assertTrue(source.contains("new ArrayBlockingQueue<>(64)"));
        assertTrue(source.contains("new ThreadPoolExecutor.AbortPolicy()"));
        assertTrue(source.contains("if (!plugin.isEnabled())"));
        assertTrue(source.contains("io.shutdownNow()"));
    }
}
