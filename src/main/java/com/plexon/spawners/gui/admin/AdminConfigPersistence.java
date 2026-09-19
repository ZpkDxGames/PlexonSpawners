package com.plexon.spawners.gui.admin;

import com.plexon.spawners.PlexonSpawners;
import com.plexon.spawners.config.ConfigBootstrap;
import com.plexon.spawners.config.ConfigRevisionService;
import com.plexon.spawners.reward.RewardItemFactory;
import com.plexon.spawners.runtime.SpawnerRuntimeSnapshot;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;

/** Two-phase admin save: filesystem work on bounded IO, runtime commit on primary thread. */
public final class AdminConfigPersistence {
    public enum Status { SAVED, STALE, INVALID, BUSY, FAILED }
    public record SaveResult(Status status, AdminSettingsDraft.ValidationResult validation, String detail) {
        public boolean saved() { return status == Status.SAVED; }
    }

    private static final DateTimeFormatter BACKUP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
    private final PlexonSpawners plugin;
    private final ConfigRevisionService revisions;
    private final AtomicBoolean saveInFlight = new AtomicBoolean();

    public AdminConfigPersistence(final PlexonSpawners plugin, final ConfigRevisionService revisions) {
        this.plugin = plugin;
        this.revisions = revisions;
    }

    public void saveAsync(final AdminSettingsSession session, final Consumer<SaveResult> callback) {
        if (session.sourceRevision() != revisions.current()
            || session.sourceGeneration() != plugin.runtimeGeneration()) {
            callback.accept(new SaveResult(Status.STALE, session.draft().validate(), "Live generation changed."));
            return;
        }
        final AdminSettingsDraft.ValidationResult validation = session.draft().validate();
        if (!validation.valid()) {
            callback.accept(new SaveResult(Status.INVALID, validation, "Draft validation failed."));
            return;
        }
        if (!session.beginSave() || !saveInFlight.compareAndSet(false, true)) {
            session.endSave();
            callback.accept(new SaveResult(Status.BUSY, validation, "Another configuration save is already pending."));
            return;
        }

        final AdminSettingsDraft immutableDraft = session.draft().copy();
        final long expectedRevision = session.sourceRevision();
        final long expectedGeneration = session.sourceGeneration();
        final String baseYaml = plugin.runtimeSnapshot().configYaml();

        plugin.coreBridge().supplyIo(() -> prepareIo(baseYaml, immutableDraft))
            .whenComplete((prepared, error) -> plugin.coreBridge().runPrimary(() -> {
                if (error != null) {
                    finish(session, callback, new SaveResult(Status.FAILED, validation, rootMessage(error)));
                    return;
                }
                if (!plugin.isEnabled()
                    || expectedRevision != revisions.current()
                    || expectedGeneration != plugin.runtimeGeneration()
                    || session.sourceRevision() != expectedRevision
                    || session.sourceGeneration() != expectedGeneration) {
                    rollbackAsync(prepared, session, callback,
                        new SaveResult(Status.STALE, validation, "Runtime changed while save was pending."));
                    return;
                }

                try {
                    final YamlConfiguration candidate = new YamlConfiguration();
                    candidate.loadFromString(prepared.candidateYaml());
                    final SpawnerRuntimeSnapshot runtimeCandidate = plugin.prepareRuntimeCandidate(
                        candidate, plugin.runtimeSnapshot().messages(), expectedGeneration + 1L);
                    plugin.commitRuntime(runtimeCandidate);
                    final long revision = revisions.bump();
                    session.markClean(revision, runtimeCandidate.generation());
                    plugin.coreBridge().markReady(
                        "Admin config committed at generation " + runtimeCandidate.generation());
                    plugin.coreBridge().runIo(() -> {
                        try {
                            pruneBackups(immutableDraft.backupsToKeep());
                        } catch (final IOException exception) {
                            plugin.getLogger().warning(
                                "Configuration saved but backup pruning failed: " + exception.getMessage());
                        }
                    });
                    finish(session, callback,
                        new SaveResult(Status.SAVED, validation,
                            "Saved runtime generation " + runtimeCandidate.generation() + "."));
                } catch (final Exception exception) {
                    rollbackAsync(prepared, session, callback,
                        new SaveResult(Status.FAILED, validation,
                            "Runtime commit rejected: " + rootMessage(exception)));
                }
            }));
    }

    private PreparedIo prepareIo(final String baseYaml, final AdminSettingsDraft draft) {
        final Path config = plugin.getDataFolder().toPath().resolve("config.yml");
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            final Path backup = Files.isRegularFile(config) ? createBackup(config) : null;
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(baseYaml);
            apply(yaml, draft);
            final String candidateYaml = yaml.saveToString();
            final Path temporary = Files.createTempFile(plugin.getDataFolder().toPath(), "config-", ".tmp");
            try {
                Files.writeString(temporary, candidateYaml, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                replaceAtomically(temporary, config);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new PreparedIo(config, backup, candidateYaml);
        } catch (final Exception exception) {
            throw new IllegalStateException("Admin config IO prepare failed", exception);
        }
    }

    private void rollbackAsync(
        final PreparedIo prepared,
        final AdminSettingsSession session,
        final Consumer<SaveResult> callback,
        final SaveResult result
    ) {
        plugin.coreBridge().runIo(() -> {
            if (prepared.backup() == null) return;
            try {
                Files.copy(prepared.backup(), prepared.config(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (final IOException exception) {
                throw new IllegalStateException("Failed to restore pre-save config backup", exception);
            }
        }).whenComplete((ignored, rollbackError) -> plugin.coreBridge().runPrimary(() -> {
            if (rollbackError != null) {
                plugin.getLogger().severe("Admin config rollback failed: " + rootMessage(rollbackError));
                plugin.coreBridge().markFailed("Admin config rollback failed");
            }
            finish(session, callback, result);
        }));
    }

    private void finish(
        final AdminSettingsSession session,
        final Consumer<SaveResult> callback,
        final SaveResult result
    ) {
        saveInFlight.set(false);
        session.endSave();
        callback.accept(result);
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
        for (int index = Math.max(1, keep); index < backups.size(); index++) {
            Files.deleteIfExists(backups.get(index));
        }
    }

    private static long modified(final Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (final IOException exception) { return Long.MIN_VALUE; }
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
        yaml.set("scope.mode", draft.scopeMode().name());
        yaml.set("scope.worlds", List.copyOf(draft.enabledWorlds()));
        yaml.set("breaking.enabled", draft.breakingEnabled());
        yaml.set("breaking.required-silk-touch-level", draft.requiredSilk());
        yaml.set("breaking.allow-silk-bypass-permission", draft.allowBypass());
        yaml.set("breaking.non-silk-reward-mode", draft.defaultMode().name());
        yaml.set("breaking.creative.recover-spawner", draft.creativeRecover());
        yaml.set("breaking.creative.award-essence", draft.creativeEssence());
        yaml.set("breaking.creative.award-custom-item", draft.creativeCustom());
        yaml.set("breaking.enabled-worlds", null);

        yaml.set("essence.enabled", draft.essenceEnabled());
        yaml.set("essence.default-amount", draft.essenceAmount());
        yaml.set("essence.default-chance", draft.essenceChance());
        yaml.set("essence.delivery", draft.essenceDelivery().name());
        new RewardItemFactory().write(yaml, "essence.item", draft.essenceItem());

        yaml.set("custom-drop.enabled", draft.customEnabled());
        yaml.set("custom-drop.default-amount", draft.customAmount());
        yaml.set("custom-drop.default-chance", draft.customChance());
        yaml.set("custom-drop.delivery", draft.customDelivery().name());
        new RewardItemFactory().write(yaml, "custom-drop.item", draft.customItem());

        yaml.set("rewards.mob-overrides", null);
        yaml.set("essence.mob-overrides", null);
        yaml.set("custom-drop.mob-overrides", null);
        for (final var entry : draft.mobOverrides().entrySet()) {
            final String key = entry.getKey().name();
            final AdminSettingsDraft.MobOverride override = entry.getValue();
            yaml.set("rewards.mob-overrides." + key + ".mode", override.mode().name());
            yaml.set("essence.mob-overrides." + key + ".amount", override.essenceAmount());
            yaml.set("essence.mob-overrides." + key + ".chance", override.essenceChance());
            yaml.set("custom-drop.mob-overrides." + key + ".amount", override.customAmount());
            yaml.set("custom-drop.mob-overrides." + key + ".chance", override.customChance());
        }

        yaml.set("gui", null);
        yaml.set("admin-gui.enabled", draft.adminGuiEnabled());
        yaml.set("admin-gui.title", draft.adminGuiTitle());
        yaml.set("admin-gui.config-backups-to-keep", draft.backupsToKeep());
        yaml.set("messages.silk-recovered", draft.silkRecoveredMessage());
        yaml.set("messages.essence-awarded", draft.essenceAwardedMessage());
        yaml.set("messages.custom-drop-awarded", draft.customAwardedMessage());
        yaml.set("messages.withdraw-success", null);
        yaml.set("messages.withdraw-failed", null);
    }

    private static String rootMessage(final Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record PreparedIo(Path config, Path backup, String candidateYaml) {}
}
