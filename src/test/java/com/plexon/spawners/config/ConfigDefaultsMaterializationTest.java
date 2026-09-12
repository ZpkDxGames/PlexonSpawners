package com.plexon.spawners.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigDefaultsMaterializationTest {
    @Test
    void bukkitDefaultsCanMaskKeysMissingFromPhysicalConfig() {
        final YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("config-version", 6);
        defaults.set("managed.nearby-stack-cap.enabled", true);
        defaults.set("managed.nearby-stack-cap.radius", 8.0D);
        defaults.set("managed.nearby-stack-cap.maximum-amount", 99);
        defaults.set("managed.nearby-stack-cap.same-type-only", true);

        final YamlConfiguration config = new YamlConfiguration();
        config.setDefaults(defaults);
        config.set("essence.default-chance", 100.0D);

        assertTrue(config.contains("managed.nearby-stack-cap.enabled"));
        assertFalse(config.contains("managed.nearby-stack-cap.enabled", true));

        config.options().copyDefaults(true);
        final String serialized = config.saveToString();

        assertTrue(serialized.contains("config-version: 6"));
        assertTrue(serialized.contains("managed:"));
        assertTrue(serialized.contains("nearby-stack-cap:"));
        assertTrue(serialized.contains("maximum-amount: 99"));
        assertEquals(100.0D, config.getDouble("essence.default-chance"));
    }

    @Test
    void essenceRuntimePersistsOnlyWhenBundledDefaultsAreMissingExplicitly() throws Exception {
        final String source = Files.readString(
            Path.of("src/main/java/com/plexon/spawners/item/EssenceService.java")
        );

        assertTrue(source.contains("defaults.getValues(true).keySet().stream()"));
        assertTrue(source.contains("!config.contains(path, true)"));
        assertTrue(source.contains("config.options().copyDefaults(true)"));
        assertTrue(source.contains("plugin.saveConfig()"));
    }
}
