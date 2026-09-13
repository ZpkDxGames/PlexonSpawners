package com.plexon.spawners.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigBootstrap {
    public static final int CONFIG_VERSION = 10;

    private ConfigBootstrap() {}

    public static void ensureV4Config(final JavaPlugin plugin) {
        final File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create plugin data directory: " + dataFolder);
        }

        final File configFile = new File(dataFolder, "config.yml");
        if (!configFile.isFile()) {
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            return;
        }

        final YamlConfiguration old = YamlConfiguration.loadConfiguration(configFile);
        if (old.getInt("config-version", 0) >= CONFIG_VERSION) {
            plugin.reloadConfig();
            return;
        }

        final File backup = new File(dataFolder, "config-pre-4.0-backup.yml");
        if (!backup.exists()) {
            try {
                Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            } catch (final IOException exception) {
                throw new IllegalStateException("Refusing 4.0 config reset because the 3.x config backup failed", exception);
            }
        }

        final RetainedSettings retained = RetainedSettings.capture(old);
        plugin.saveResource("config.yml", true);
        plugin.reloadConfig();
        retained.apply(plugin);
        plugin.saveConfig();
        plugin.reloadConfig();
        plugin.getLogger().warning(
            "Reset legacy PlexonSpawners configuration to the clean 4.0 schema. "
                + "Backup: plugins/PlexonSpawners/config-pre-4.0-backup.yml");
    }

    private record RetainedSettings(
        Boolean breakingEnabled,
        Integer requiredSilk,
        Boolean allowBypass,
        List<String> enabledWorlds,
        Boolean essenceEnabled,
        Integer essenceAmount,
        Double essenceChance,
        String essenceDelivery,
        ItemStack essenceItem,
        YamlConfiguration essenceOverrides
    ) {
        private static RetainedSettings capture(final YamlConfiguration old) {
            final YamlConfiguration overrides = new YamlConfiguration();
            final ConfigurationSection section = old.getConfigurationSection("essence.mob-overrides");
            if (section != null) {
                for (final String key : section.getKeys(false)) {
                    final Object value = section.get(key);
                    if (value instanceof ConfigurationSection nested) {
                        for (final String nestedKey : nested.getKeys(false)) {
                            overrides.set(key + "." + nestedKey, nested.get(nestedKey));
                        }
                    } else {
                        overrides.set(key, value);
                    }
                }
            }
            return new RetainedSettings(
                old.contains("breaking.enabled") ? old.getBoolean("breaking.enabled") : null,
                old.contains("breaking.required-silk-touch-level") ? old.getInt("breaking.required-silk-touch-level") : null,
                old.contains("breaking.allow-silk-bypass-permission") ? old.getBoolean("breaking.allow-silk-bypass-permission") : null,
                old.contains("breaking.enabled-worlds") ? List.copyOf(old.getStringList("breaking.enabled-worlds")) : List.of(),
                old.contains("essence.enabled") ? old.getBoolean("essence.enabled") : null,
                old.contains("essence.default-amount") ? old.getInt("essence.default-amount") : null,
                old.contains("essence.default-chance") ? old.getDouble("essence.default-chance") : null,
                old.contains("essence.delivery") ? old.getString("essence.delivery") : null,
                old.getItemStack("essence.item"),
                overrides
            );
        }

        private void apply(final JavaPlugin plugin) {
            if (breakingEnabled != null) plugin.getConfig().set("breaking.enabled", breakingEnabled);
            if (requiredSilk != null) plugin.getConfig().set("breaking.required-silk-touch-level", requiredSilk);
            if (allowBypass != null) plugin.getConfig().set("breaking.allow-silk-bypass-permission", allowBypass);
            if (!enabledWorlds.isEmpty()) plugin.getConfig().set("breaking.enabled-worlds", enabledWorlds);
            if (essenceEnabled != null) plugin.getConfig().set("essence.enabled", essenceEnabled);
            if (essenceAmount != null) plugin.getConfig().set("essence.default-amount", essenceAmount);
            if (essenceChance != null) plugin.getConfig().set("essence.default-chance", essenceChance);
            if (essenceDelivery != null) plugin.getConfig().set("essence.delivery", essenceDelivery);
            if (essenceItem != null) plugin.getConfig().set("essence.item", essenceItem);
            for (final String path : essenceOverrides.getKeys(true)) {
                if (!essenceOverrides.isConfigurationSection(path)) {
                    plugin.getConfig().set("essence.mob-overrides." + path, essenceOverrides.get(path));
                }
            }
        }
    }
}
