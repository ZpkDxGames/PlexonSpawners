package com.plexon.spawners.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class ConfigV11MigrationTest {
    @Test
    void essenceEnabledPreservesEssenceBehaviorAndExistingValues() {
        final YamlConfiguration config = base(true);
        ConfigV11Migration.apply(config);
        assertEquals(11, config.getInt("config-version"));
        assertEquals("ESSENCE", config.getString("breaking.non-silk-reward-mode"));
        assertEquals(47.5D, config.getDouble("essence.default-chance"));
        assertEquals(List.of("Survival_World"), config.getStringList("breaking.enabled-worlds"));
        assertEquals(List.of(1, 8, 32), config.getIntegerList("gui.withdraw-presets"));
        assertEquals(2, config.getInt("essence.mob-overrides.BLAZE.amount"));
        assertFalse(config.getBoolean("custom-drop.enabled"));
        assertTrue(config.getBoolean("admin-gui.enabled"));
    }

    @Test
    void essenceDisabledPreservesNoRewardBehavior() {
        final YamlConfiguration config = base(false);
        ConfigV11Migration.apply(config);
        assertEquals("NONE", config.getString("breaking.non-silk-reward-mode"));
    }

    @Test
    void existingModeAndUnknownKeysAreNotOverwritten() {
        final YamlConfiguration config = base(true);
        config.set("breaking.non-silk-reward-mode", "CUSTOM_ITEM");
        config.set("future.unknown-setting", "keep-me");
        ConfigV11Migration.apply(config);
        assertEquals("CUSTOM_ITEM", config.getString("breaking.non-silk-reward-mode"));
        assertEquals("keep-me", config.getString("future.unknown-setting"));
    }

    private static YamlConfiguration base(final boolean essenceEnabled) {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("config-version", 10);
        config.set("breaking.enabled", true);
        config.set("breaking.enabled-worlds", List.of("Survival_World"));
        config.set("essence.enabled", essenceEnabled);
        config.set("essence.default-chance", 47.5D);
        config.set("essence.mob-overrides.BLAZE.amount", 2);
        config.set("gui.withdraw-presets", List.of(1, 8, 32));
        return config;
    }
}
