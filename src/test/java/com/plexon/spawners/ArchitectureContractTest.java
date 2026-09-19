package com.plexon.spawners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArchitectureContractTest {
    @Test
    void wildStackerIsHardAuthorityAndCoreIsThinOptionalDependency() throws IOException {
        final String pluginYml = read("src/main/resources/plugin.yml");
        final String build = read("build.gradle.kts");
        assertTrue(pluginYml.contains("depend:\n  - WildStacker"));
        assertTrue(pluginYml.contains("softdepend:\n  - PlexonCore"));
        assertTrue(build.contains("com.bgsoftware:WildStackerAPI:2026.2"));
        assertTrue(build.contains("val coreVersion = \"2.1.0\""));
        assertTrue(build.contains("7ee823ded87d5be9c62426b04571c0d0d6b11c138575ca2c91838586c9f7576c"));
        assertTrue(build.contains("compileOnly(files(coreJar))"));
        assertTrue(build.contains("PlexonCore runtime classes must not be shaded"));
    }

    @Test
    void noDuplicateStackOrPlacedSpawnerInteractionEngineExists() throws IOException {
        final Path sourceRoot = Path.of("src/main/java/com/plexon/spawners");
        assertFalse(Files.exists(sourceRoot.resolve("managed")));
        assertFalse(Files.exists(sourceRoot.resolve("compat")));
        try (var stream = Files.walk(sourceRoot)) {
            for (final Path path : stream.filter(Files::isRegularFile).toList()) {
                final String source = Files.readString(path);
                assertFalse(source.contains("ManagedSpawnerRegistry"), path.toString());
                assertFalse(source.contains("PlayerInteractEvent"), path.toString());
                assertFalse(source.contains("RIGHT_CLICK_BLOCK"), path.toString());
                assertFalse(source.contains("getNearbyEntities"), path.toString());
                assertFalse(source.contains("runUnstack("), path.toString());
            }
        }
    }

    @Test
    void singularBreakRemediationPreservesTransientWildStackerObjects() throws IOException {
        final String source = read("src/main/java/com/plexon/spawners/listener/SpawnerBreakListener.java");
        assertTrue(source.contains("BlockBreakEvent"));
        assertTrue(source.contains("EventPriority.LOWEST"));
        assertTrue(source.contains("SpawnerUnstackEvent"));
        assertTrue(source.contains("SpawnerDropEvent"));
        assertTrue(source.contains("event.getAmount()"));
        assertTrue(source.contains("snapshotRecovery(stacked, 1)"));
        assertTrue(source.contains("BreakReconciliation.confirmedRemovedAmount"));
        assertTrue(source.contains("BreakCompletionGate"));
        assertTrue(source.contains("wildStacker.resolve(context.location())"));
        assertTrue(source.contains("coreBridge.schedulePrimary"));
        assertFalse(source.contains("stacked.isCached()"));
        assertFalse(source.contains("PersistentDataContainer"));
    }

    @Test
    void schema12RetiresWithdrawalAndDefaultsToExplicitSurvivalScope() throws IOException {
        final String config = read("src/main/resources/config.yml");
        final String messages = read("src/main/resources/messages.yml");
        assertTrue(config.contains("config-version: 12"));
        assertTrue(config.contains("mode: ALLOWLIST"));
        assertTrue(config.contains("- Survival_World"));
        assertTrue(config.contains("- Survival_World_nether"));
        assertTrue(config.contains("- Survival_World_the_end"));
        assertFalse(config.contains("withdraw-presets"));
        assertFalse(config.contains("open-on-right-click"));
        assertFalse(messages.contains("withdraw-success"));
        assertFalse(messages.contains("withdraw-failed"));
    }

    @Test
    void permissionAndGiveAuthorityFailClosed() throws IOException {
        final String pluginYml = read("src/main/resources/plugin.yml");
        final String command = read("src/main/java/com/plexon/spawners/command/SpawnersCommand.java");
        assertTrue(pluginYml.contains("plexonspawners.bypass.silk:"));
        assertTrue(pluginYml.contains("default: false"));
        assertTrue(command.contains("wildstacker:stacker give -s "));
        assertFalse(command.contains("new ItemStack(Material.SPAWNER)"));
        assertTrue(command.contains("minecraft:"));
        assertTrue(command.contains("type != EntityType.UNKNOWN"));
    }

    @Test
    void policyApiAndEventsContainNoStackMutationAuthority() throws IOException {
        final String api = read("src/main/java/com/plexon/spawners/api/PlexonSpawnersApi.java");
        assertTrue(api.contains("boolean isWorldEnabled"));
        assertTrue(api.contains("BreakPolicyView policy"));
        assertTrue(api.contains("IntegrationStatus integration"));
        assertFalse(api.contains("setStack"));
        assertFalse(api.contains("merge("));
        assertFalse(api.contains("unstack("));
        assertFalse(api.contains("place("));
        assertTrue(Files.exists(Path.of("src/main/java/com/plexon/spawners/event/PlexonSpawnerRecoveredEvent.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/plexon/spawners/event/PlexonSpawnerRewardFinalizedEvent.java")));
        assertTrue(Files.exists(Path.of("src/main/java/com/plexon/spawners/event/PlexonSpawnerBreakFinalizedEvent.java")));
    }

    private static String read(final String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
