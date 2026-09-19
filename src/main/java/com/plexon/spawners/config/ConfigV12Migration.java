package com.plexon.spawners.config;

import com.plexon.spawners.reward.RewardItemFactory;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

/** Targeted 4.0 schema-11 -> schema-12 migration. */
public final class ConfigV12Migration {
    private ConfigV12Migration() {}

    public static void apply(final YamlConfiguration config) {
        final int version = config.getInt("config-version", 0);
        if (version == ConfigBootstrap.CONFIG_VERSION) return;
        if (version != 11) throw new IllegalArgumentException("ConfigV12Migration requires schema 11, got " + version);

        final List<String> legacyWorlds = List.copyOf(config.getStringList("breaking.enabled-worlds"));
        if (!config.contains("scope.mode")) {
            config.set("scope.mode", legacyWorlds.isEmpty() ? "ALL" : "ALLOWLIST");
        }
        if (!config.contains("scope.worlds")) config.set("scope.worlds", legacyWorlds);

        final RewardItemFactory items = new RewardItemFactory();
        items.write(config, "essence.item", items.read(config, "essence.item",
            Material.AMETHYST_SHARD,
            "<gradient:#56B9F2:#92E1FF><b>Spawner Essence</b></gradient>",
            List.of("<gray>A concentrated fragment of spawner energy.</gray>"), true));
        items.write(config, "custom-drop.item", items.read(config, "custom-drop.item",
            Material.PRISMARINE_CRYSTALS,
            "<gradient:#7BE7FF:#4AA8FF><b>Spawner Fragment</b></gradient>",
            List.of("<gray>Dropped when a spawner is broken without Silk Touch.</gray>"), true));

        config.set("breaking.enabled-worlds", null);
        config.set("gui", null);
        config.set("messages.withdraw-success", null);
        config.set("messages.withdraw-failed", null);
        config.set("config-version", ConfigBootstrap.CONFIG_VERSION);
    }
}
