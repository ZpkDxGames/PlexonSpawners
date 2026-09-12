package com.plexon.spawners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NearbyStackCapContractTest {
    private static String source(final String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/plexon/spawners").resolve(relative));
    }

    @Test
    void guardUsesBoundedNearbyQueryAndShortCircuit() throws Exception {
        final String listener = source("listener/NearbyStackCapListener.java");
        assertTrue(listener.contains("getNearbyEntities(center, radius, radius, radius)"));
        assertTrue(listener.contains("logicalAmount >= settings.maximumAmount()"));
        assertFalse(listener.contains("getEntities()"));
        assertFalse(listener.contains("runTaskTimer"));
        assertFalse(listener.contains("CompletableFuture"));
    }

    @Test
    void directWildStackerIncreasePathIsIntercepted() throws Exception {
        final String compat = source("compat/WildStackerCompat.java");
        final String listener = source("listener/NearbyStackCapListener.java");
        final String policy = source("managed/NearbyStackCapPolicy.java");
        assertTrue(compat.contains("SpawnerStackedEntitySpawnEvent"));
        assertTrue(compat.contains("EntityStackEvent"));
        assertTrue(compat.contains("setShouldBeStacked"));
        assertTrue(compat.contains("setCancelled"));
        assertTrue(listener.contains("shouldCancelEntityStack"));
        assertTrue(listener.contains("cycle.mode != NearbyStackCapPolicy.Decision.FAST_PATH"));
        assertTrue(policy.contains("return Decision.GRANULAR"));
    }

    @Test
    void reflectionDiscoveryIsLifecycleCached() throws Exception {
        final String compat = source("compat/WildStackerCompat.java");
        assertTrue(compat.contains("getMethod(\"getEntityAmount\""));
        assertTrue(compat.contains("getMethod(\"getSpawnersAmount\""));
        assertTrue(compat.contains("registerSpawnInterceptors"));
        assertTrue(compat.contains("onPluginEnable"));
        assertTrue(compat.contains("onPluginDisable"));
        assertFalse(compat.contains("Class.forName(API_CLASS, true, loader);\n            return"));
    }

    @Test
    void managedIdentityIsExactAndTierNearbyLimitIsNotRepurposed() throws Exception {
        final String listener = source("listener/NearbyStackCapListener.java");
        final String tuning = source("managed/SpawnerTuning.java");
        assertTrue(listener.contains("registry.find(spawnerLocation)"));
        assertTrue(listener.contains("registry.find(location)"));
        assertTrue(tuning.contains("max-nearby-entities"));
    }

    @Test
    void configurationMigrationIsAdditive() throws Exception {
        final String plugin = source("PlexonSpawners.java");
        assertTrue(plugin.contains("configVersion < 6"));
        assertTrue(plugin.contains("managed.nearby-stack-cap.enabled"));
        assertTrue(plugin.contains("managed.nearby-stack-cap.radius"));
        assertTrue(plugin.contains("managed.nearby-stack-cap.maximum-amount"));
        assertTrue(plugin.contains("managed.nearby-stack-cap.same-type-only"));
        assertTrue(plugin.contains("if (!getConfig().contains"));
    }
}
