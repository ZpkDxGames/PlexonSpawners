package com.plexon.spawners.config;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigV11Migration {
    private ConfigV11Migration() {}

    public static void apply(final YamlConfiguration config) {
        config.set("config-version", 11);
        if (!config.contains("breaking.non-silk-reward-mode")) {
            config.set("breaking.non-silk-reward-mode",
                config.getBoolean("essence.enabled", true) ? "ESSENCE" : "NONE");
        }
        setDefault(config, "breaking.creative.award-custom-item", false);
        setDefault(config, "custom-drop.enabled", false);
        setDefault(config, "custom-drop.default-amount", 1);
        setDefault(config, "custom-drop.default-chance", 15.0D);
        setDefault(config, "custom-drop.delivery", "INVENTORY");
        setDefault(config, "custom-drop.item.material", "PRISMARINE_CRYSTALS");
        setDefault(config, "custom-drop.item.name", "<gradient:#7BE7FF:#4AA8FF><b>Spawner Fragment</b></gradient>");
        if (!config.contains("custom-drop.item.lore")) {
            config.set("custom-drop.item.lore", List.of("<gray>Dropped when a spawner is broken without Silk Touch.</gray>"));
        }
        setDefault(config, "custom-drop.item.glow", true);
        setDefault(config, "admin-gui.enabled", true);
        setDefault(config, "admin-gui.title", "<gradient:#56B9F2:#92E1FF><b>PlexonSpawners Admin</b></gradient>");
        setDefault(config, "admin-gui.config-backups-to-keep", 10);
        setDefault(config, "messages.custom-drop-awarded", true);
    }

    private static void setDefault(final YamlConfiguration config, final String path, final Object value) {
        if (!config.contains(path)) config.set(path, value);
    }
}
