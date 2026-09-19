package com.plexon.spawners;

import com.plexon.spawners.api.DefaultPlexonSpawnersApi;
import com.plexon.spawners.api.PlexonSpawnersApi;
import com.plexon.spawners.command.SpawnersCommand;
import com.plexon.spawners.config.ConfigBootstrap;
import com.plexon.spawners.config.ConfigRevisionService;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.essence.EssenceService;
import com.plexon.spawners.gui.admin.AdminGuiService;
import com.plexon.spawners.integration.WildStackerBridge;
import com.plexon.spawners.integration.core.CoreBridge;
import com.plexon.spawners.integration.core.CoreBridgeFactory;
import com.plexon.spawners.listener.SpawnerBreakListener;
import com.plexon.spawners.message.MessageService;
import com.plexon.spawners.reward.CustomDropService;
import com.plexon.spawners.runtime.SpawnerRuntimeSnapshot;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlexonSpawners extends JavaPlugin {
    private final PluginSettings settings = new PluginSettings();
    private final ConfigRevisionService revisions = new ConfigRevisionService();
    private final AtomicLong generation = new AtomicLong();

    private MessageService messages;
    private EssenceService essenceService;
    private CustomDropService customDropService;
    private WildStackerBridge wildStacker;
    private CoreBridge coreBridge;
    private AdminGuiService adminGui;
    private SpawnerBreakListener breakListener;
    private volatile SpawnerRuntimeSnapshot runtime;

    @Override
    public void onEnable() {
        try {
            ConfigBootstrap.ensureV4Config(this);
            wildStacker = new WildStackerBridge(this);
            coreBridge = CoreBridgeFactory.resolve(this);
            coreBridge.registerStarting();

            messages = new MessageService(this);
            essenceService = new EssenceService(this, settings);
            customDropService = new CustomDropService(this, settings);

            final SpawnerRuntimeSnapshot initial = prepareRuntimeCandidate(
                getConfig(), messages.prepareFromDisk(), generation.incrementAndGet());
            commitRuntime(initial);

            breakListener = new SpawnerBreakListener(
                this, settings, essenceService, customDropService, messages, wildStacker, coreBridge);
            adminGui = new AdminGuiService(this, settings, messages, essenceService, customDropService, revisions);
            getServer().getPluginManager().registerEvents(breakListener, this);
            getServer().getPluginManager().registerEvents(adminGui, this);

            final SpawnersCommand command = new SpawnersCommand(this, messages, adminGui);
            final PluginCommand pluginCommand = getCommand("pspawners");
            if (pluginCommand == null) throw new IllegalStateException("Command 'pspawners' is missing from plugin.yml");
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);

            getServer().getServicesManager().register(
                PlexonSpawnersApi.class, new DefaultPlexonSpawnersApi(this), this, ServicePriority.Normal);

            coreBridge.markReady("WildStacker-authoritative policy layer ready; runtime generation " + runtimeGeneration());
            getLogger().info("PlexonSpawners " + getPluginMeta().getVersion()
                + " enabled. WildStacker " + wildStacker.version()
                + " owns all stack/item/upgrade state; Core mode=" + coreBridge.mode()
                + "; config schema=" + settings.configSchema()
                + "; generation=" + runtimeGeneration() + ".");
        } catch (final RuntimeException | LinkageError exception) {
            if (coreBridge != null) coreBridge.markFailed("Startup failed: " + exception.getClass().getSimpleName());
            throw exception;
        }
    }

    @Override
    public void onDisable() {
        if (breakListener != null) breakListener.shutdown();
        if (getServer() != null) getServer().getServicesManager().unregisterAll(this);
        if (coreBridge != null) coreBridge.close();
    }

    public SpawnerRuntimeSnapshot prepareRuntimeCandidate(
        final FileConfiguration candidate,
        final MessageService.Snapshot messageSnapshot,
        final long candidateGeneration
    ) {
        final PluginSettings.Snapshot preparedSettings = settings.prepare(candidate);
        final ItemStack essenceTemplate = essenceService.prepare(preparedSettings);
        final ItemStack customTemplate = customDropService.prepare(preparedSettings);
        return new SpawnerRuntimeSnapshot(
            candidateGeneration,
            preparedSettings,
            messageSnapshot,
            candidate.saveToString(),
            essenceTemplate,
            customTemplate,
            wildStacker.version(),
            coreBridge.mode(),
            coreBridge.registrationState());
    }

    /** Commit contains no filesystem work and is called only on the primary thread. */
    public void commitRuntime(final SpawnerRuntimeSnapshot prepared) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Runtime commit must occur on the primary thread");
        settings.commit(prepared.settings());
        messages.commit(prepared.messages());
        essenceService.commit(prepared.essenceTemplate());
        customDropService.commit(prepared.customTemplate());
        runtime = prepared;
        generation.set(Math.max(generation.get(), prepared.generation()));
    }

    public CompletableFuture<Long> reloadPluginAsync() {
        final CompletableFuture<Long> result = new CompletableFuture<>();
        coreBridge.supplyIo(() -> {
            final File configFile = new File(getDataFolder(), "config.yml");
            final YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            final MessageService.Snapshot catalog = messages.prepareFromDisk();
            return new ReloadPayload(config, catalog);
        }).whenComplete((payload, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            coreBridge.runPrimary(() -> {
                try {
                    final long nextGeneration = generation.get() + 1L;
                    final SpawnerRuntimeSnapshot prepared = prepareRuntimeCandidate(
                        payload.config(), payload.messages(), nextGeneration);
                    commitRuntime(prepared);
                    final long revision = revisions.bump();
                    if (adminGui != null) adminGui.invalidateSessions();
                    coreBridge.markReady("Runtime reload committed at generation " + nextGeneration);
                    result.complete(revision);
                } catch (final RuntimeException exception) {
                    coreBridge.markDegraded("Runtime reload rejected: " + exception.getMessage());
                    result.completeExceptionally(exception);
                }
            });
        });
        return result;
    }

    public YamlConfiguration liveConfigCopy() {
        final SpawnerRuntimeSnapshot current = runtime;
        if (current == null) throw new IllegalStateException("Runtime is not initialized");
        final YamlConfiguration copy = new YamlConfiguration();
        try {
            copy.loadFromString(current.configYaml());
        } catch (final org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalStateException("Committed runtime YAML is invalid", exception);
        }
        return copy;
    }

    public long runtimeGeneration() {
        final SpawnerRuntimeSnapshot current = runtime;
        return current == null ? 0L : current.generation();
    }

    public int pendingBreakTransactions() {
        return breakListener == null ? 0 : breakListener.pendingCount();
    }

    public PluginSettings settings() { return settings; }
    public WildStackerBridge wildStacker() { return wildStacker; }
    public CoreBridge coreBridge() { return coreBridge; }
    public ConfigRevisionService revisions() { return revisions; }
    public EssenceService essenceService() { return essenceService; }
    public CustomDropService customDropService() { return customDropService; }
    public SpawnerRuntimeSnapshot runtimeSnapshot() { return runtime; }

    private record ReloadPayload(YamlConfiguration config, MessageService.Snapshot messages) {}
}
