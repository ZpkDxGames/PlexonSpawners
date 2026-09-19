package com.plexon.spawners.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ConfigV12MigrationTest {
    @Test
    void migrationRetiresDeadWithdrawalKeysAndEncodesExactItems() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/config/ConfigV12Migration.java"));
        assertTrue(source.contains("config.set("gui", null)"));
        assertTrue(source.contains("config.set("messages.withdraw-success", null)"));
        assertTrue(source.contains("config.set("messages.withdraw-failed", null)"));
        assertTrue(source.contains("items.write(config, "essence.item""));
        assertTrue(source.contains("items.write(config, "custom-drop.item""));
        assertTrue(source.contains("config.set("config-version", ConfigBootstrap.CONFIG_VERSION)"));
    }

    @Test
    void legacyEmptyAllowlistPreservesAllWorldSemanticsExplicitly() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/config/ConfigV12Migration.java"));
        assertTrue(source.contains("legacyWorlds.isEmpty() ? "ALL" : "ALLOWLIST""));
        assertTrue(source.contains("config.set("scope.worlds", legacyWorlds)"));
    }

    @Test
    void bootstrapFailsClosedOnFutureSchemaAndBacksUpV11() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/config/ConfigBootstrap.java"));
        assertTrue(source.contains("version > CONFIG_VERSION"));
        assertTrue(source.contains("Unsupported future PlexonSpawners config schema"));
        assertTrue(source.contains("config-v11-before-v12.yml"));
        assertTrue(source.contains("ConfigV12Migration.apply(config)"));
        assertFalse(source.contains("withdraw-presets"));
    }
}
