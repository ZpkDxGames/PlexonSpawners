package com.plexon.spawners.config;

import com.plexon.spawners.reward.RewardItemFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigBootstrap {
    public static final int CONFIG_VERSION = 12;

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
        final int version = old.getInt("config-version", 0);
        if (version > CONFIG_VERSION) {
            throw new IllegalStateException("Unsupported future PlexonSpawners config schema " + version
                + "; running schema is " + CONFIG_VERSION + ". Refusing to rewrite it.");
        }
        if (version == CONFIG_VERSION) {
            plugin.reloadConfig();
            return;
        }
        if (version == 11) {
            migrate11To12(plugin, configFile, old);
            plugin.reloadConfig();
            return;
        }
        if (version == 10) {
            migrate10To12(plugin, configFile, old);
            plugin.reloadConfig();
            return;
        }

        resetLegacyPre4(plugin, configFile, old);
        plugin.reloadConfig();
    }

    private static void migrate11To12(
        final JavaPlugin plugin,
        final File configFile,
        final YamlConfiguration config
    ) {
        final File backup = new File(plugin.getDataFolder(), "config-v11-before-v12.yml");
        try {
            if (!backup.exists()) Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            ConfigV12Migration.apply(config);
            writeAtomic(configFile, config.saveToString());
            plugin.getLogger().info("Migrated PlexonSpawners config schema 11 -> 12; retired dead withdrawal settings and encoded exact reward items.");
        } catch (final IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not migrate PlexonSpawners config schema 11 -> 12", exception);
        }
    }

    private static void migrate10To12(
        final JavaPlugin plugin,
        final File configFile,
        final YamlConfiguration config
    ) {
        final File backup = new File(plugin.getDataFolder(), "config-v10-before-v12.yml");
        try {
            if (!backup.exists()) Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            ConfigV11Migration.apply(config);
            ConfigV12Migration.apply(config);
            writeAtomic(configFile, config.saveToString());
            plugin.getLogger().info("Migrated PlexonSpawners config schema 10 -> 12 through the supported 10 -> 11 -> 12 path.");
        } catch (final IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not migrate PlexonSpawners config schema 10 -> 12", exception);
        }
    }

    private static void resetLegacyPre4(
        final JavaPlugin plugin,
        final File configFile,
        final YamlConfiguration old
    ) {
        final File backup = new File(plugin.getDataFolder(), "config-pre-4.0-backup.yml");
        if (!backup.exists()) {
            try {
                Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            } catch (final IOException exception) {
                throw new IllegalStateException("Refusing 4.0 config reset because the legacy config backup failed", exception);
            }
        }

        final RetainedSettings retained = RetainedSettings.capture(old);
        plugin.saveResource("config.yml", true);
        plugin.reloadConfig();
        retained.apply(plugin);
        plugin.saveConfig();
        plugin.getLogger().warning(
            "Reset legacy PlexonSpawners configuration to clean schema 12. Backup: plugins/PlexonSpawners/config-pre-4.0-backup.yml");
    }

    private static void writeAtomic(final File file, final String content) throws IOException {
        final var target = file.toPath();
        final var temporary = Files.createTempFile(target.getParent(), "config-migration-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
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
                for (final String path : section.getKeys(true)) {
                    if (!section.isConfigurationSection(path)) overrides.set(path, section.get(path));
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
                overrides);
        }

        private void apply(final JavaPlugin plugin) {
            if (breakingEnabled != null) plugin.getConfig().set("breaking.enabled", breakingEnabled);
            if (requiredSilk != null) plugin.getConfig().set("breaking.required-silk-touch-level", requiredSilk);
            if (allowBypass != null) plugin.getConfig().set("breaking.allow-silk-bypass-permission", allowBypass);

            // Preserve old scope semantics explicitly. Empty legacy list meant ALL; non-empty meant allowlist.
            plugin.getConfig().set("scope.mode", enabledWorlds.isEmpty() ? "ALL" : "ALLOWLIST");
            plugin.getConfig().set("scope.worlds", enabledWorlds);

            if (essenceEnabled != null) plugin.getConfig().set("essence.enabled", essenceEnabled);
            if (essenceAmount != null) plugin.getConfig().set("essence.default-amount", essenceAmount);
            if (essenceChance != null) plugin.getConfig().set("essence.default-chance", essenceChance);
            if (essenceDelivery != null) plugin.getConfig().set("essence.delivery", essenceDelivery);

            final RewardItemFactory factory = new RewardItemFactory();
            if (essenceItem != null && !essenceItem.getType().isAir()) {
                factory.write(plugin.getConfig(), "essence.item", factory.sanitize(essenceItem));
            }
            for (final String path : essenceOverrides.getKeys(true)) {
                if (!essenceOverrides.isConfigurationSection(path)) {
                    plugin.getConfig().set("essence.mob-overrides." + path, essenceOverrides.get(path));
                }
            }
        }
    }
}
