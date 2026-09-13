package com.plexon.spawners.gui.admin;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.config.ConfigBootstrap;
import com.plexon.spawners.config.ConfigRevisionService;
import com.plexon.spawners.reward.RewardItemFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

public final class AdminConfigPersistence {
    public enum Status {
        SAVED,
        STALE,
        INVALID,
        FAILED
    }

    public record SaveResult(Status status, AdminSettingsDraft.ValidationResult validation, String detail) {
        public boolean saved() {
            return status == Status.SAVED;
        }
    }

    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

    private final PlexonSpawners plugin;
    private final ConfigRevisionService revisions;

    public AdminConfigPersistence(final PlexonSpawners plugin, final ConfigRevisionService revisions) {
        this.plugin = plugin;
        this.revisions = revisions;
    }

    public SaveResult save(final AdminSettingsSession session) {
        if (session.sourceRevision() != revisions.current()) {
            return new SaveResult(Status.STALE, session.draft().validate(),
                "The live configuration changed after this GUI was opened.");
        }
        final AdminSettingsDraft.ValidationResult validation = session.draft().validate();
        if (!validation.valid()) return new SaveResult(Status.INVALID, validation, "Draft validation failed.");

        final Path config = plugin.getDataFolder().toPath().resolve("config.yml");
        Path backup = null;
        Path temporary = null;
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            if (Files.isRegularFile(config)) backup = createBackup(config);

            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config.toFile());
            apply(yaml, session.draft());
            temporary = Files.createTempFile(plugin.getDataFolder().toPath(), "config-", ".tmp");
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            replaceAtomically(temporary, config);
            temporary = null;

            try {
                plugin.reloadRuntime();
            } catch (final RuntimeException reloadFailure) {
                if (backup != null) {
                    Files.copy(backup, config, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    try {
                        plugin.reloadRuntime();
                    } catch (final RuntimeException rollbackFailure) {
                        reloadFailure.addSuppressed(rollbackFailure);
                    }
                }
                throw new IOException(
                    "Runtime reload failed after config replacement; previous config was restored where possible.", reloadFailure);
            }

            try {
                pruneBackups(session.draft().backupsToKeep());
            } catch (final IOException cleanupFailure) {
                plugin.getLogger().warning("Configuration was saved, but old backup cleanup failed: " + cleanupFailure.getMessage());
            }
            final long revision = revisions.bump();
            session.markClean(revision);
            return new SaveResult(Status.SAVED, validation, "Saved configuration revision " + revision + ".");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Admin GUI configuration save failed: " + exception.getMessage());
            return new SaveResult(Status.FAILED, validation,
                exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (final IOException ignored) {
                    // Best-effort cleanup only.
                }
            }
        }
    }

    private Path createBackup(final Path config) throws IOException {
        final Path directory = plugin.getDataFolder().toPath().resolve("config-backups");
        Files.createDirectories(directory);
        final Path backup = directory.resolve("config-" + BACKUP_FORMAT.format(LocalDateTime.now()) + ".yml");
        Files.copy(config, backup, StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }

    private void pruneBackups(final int keep) throws IOException {
        final Path directory = plugin.getDataFolder().toPath().resolve("config-backups");
        if (!Files.isDirectory(directory)) return;
        final List<Path> backups;
        try (Stream<Path> stream = Files.list(directory)) {
            backups = stream.filter(path -> path.getFileName().toString().startsWith("config-")
                    && path.getFileName().toString().endsWith(".yml"))
                .sorted(Comparator.comparingLong(AdminConfigPersistence::modified).reversed())
                .toList();
        }
        for (int index = Math.max(1, keep); index < backups.size(); index++) Files.deleteIfExists(backups.get(index));
    }

    private static long modified(final Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (final IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private static void replaceAtomically(final Path temporary, final Path config) throws IOException {
        try {
            Files.move(temporary, config, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException exception) {
            Files.move(temporary, config, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void apply(final YamlConfiguration yaml, final AdminSettingsDraft draft) {
        yaml.set("config-version", ConfigBootstrap.CONFIG_VERSION);
        yaml.set("breaking.enabled", draft.breakingEnabled());
        yaml.set("breaking.required-silk-touch-level", draft.requiredSilk());
        yaml.set("breaking.allow-silk-bypass-permission", draft.allowBypass());
        yaml.set("breaking.non-silk-reward-mode", draft.defaultMode().name());
        yaml.set("breaking.creative.recover-spawner", draft.creativeRecover());
        yaml.set("breaking.creative.award-essence", draft.creativeEssence());
        yaml.set("breaking.creative.award-custom-item", draft.creativeCustom());
        yaml.set("breaking.enabled-worlds", List.copyOf(draft.enabledWorlds()));

        yaml.set("essence.enabled", draft.essenceEnabled());
        yaml.set("essence.default-amount", draft.essenceAmount());
        yaml.set("essence.default-chance", draft.essenceChance());
        yaml.set("essence.delivery", draft.essenceDelivery().name());
        writeItem(yaml, "essence.item", draft.essenceItem());

        yaml.set("custom-drop.enabled", draft.customEnabled());
        yaml.set("custom-drop.default-amount", draft.customAmount());
        yaml.set("custom-drop.default-chance", draft.customChance());
        yaml.set("custom-drop.delivery", draft.customDelivery().name());
        writeItem(yaml, "custom-drop.item", draft.customItem());

        for (final EntityType type : EntityType.values()) {
            final String key = type.name();
            yaml.set("rewards.mob-overrides." + key, null);
            yaml.set("essence.mob-overrides." + key, null);
            yaml.set("custom-drop.mob-overrides." + key, null);
        }
        for (final var entry : draft.mobOverrides().entrySet()) {
            final String key = entry.getKey().name();
            final AdminSettingsDraft.MobOverride override = entry.getValue();
            yaml.set("rewards.mob-overrides." + key + ".mode", override.mode().name());
            yaml.set("essence.mob-overrides." + key + ".amount", override.essenceAmount());
            yaml.set("essence.mob-overrides." + key + ".chance", override.essenceChance());
            yaml.set("custom-drop.mob-overrides." + key + ".amount", override.customAmount());
            yaml.set("custom-drop.mob-overrides." + key + ".chance", override.customChance());
        }

        yaml.set("gui.enabled", draft.withdrawalEnabled());
        yaml.set("gui.open-on-right-click", draft.openOnRightClick());
        yaml.set("gui.title", draft.withdrawalTitle());
        yaml.set("gui.withdraw-presets", List.copyOf(draft.withdrawPresets()));
        yaml.set("admin-gui.enabled", draft.adminGuiEnabled());
        yaml.set("admin-gui.title", draft.adminGuiTitle());
        yaml.set("admin-gui.config-backups-to-keep", draft.backupsToKeep());

        yaml.set("messages.silk-recovered", draft.silkRecoveredMessage());
        yaml.set("messages.essence-awarded", draft.essenceAwardedMessage());
        yaml.set("messages.custom-drop-awarded", draft.customAwardedMessage());
        yaml.set("messages.withdraw-success", draft.withdrawSuccessMessage());
        yaml.set("messages.withdraw-failed", draft.withdrawFailedMessage());
    }

    private static void writeItem(final YamlConfiguration yaml, final String path, final RewardItemFactory.ItemDefinition item) {
        yaml.set(path, null);
        yaml.set(path + ".material", item.material().name());
        yaml.set(path + ".name", item.name());
        yaml.set(path + ".lore", item.lore());
        yaml.set(path + ".glow", item.glow());
        if (item.itemModel() != null && !item.itemModel().isBlank()) yaml.set(path + ".item-model", item.itemModel());
    }
}
