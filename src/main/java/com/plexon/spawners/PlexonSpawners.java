package com.plexon.spawners;

import com.plexon.spawners.api.PlexonSpawnersApi;
import com.plexon.spawners.command.PlexonSpawnersCommand;
import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.NearbyStackCapSettings;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.gui.AdminGui;
import com.plexon.spawners.gui.SpawnerControlGui;
import com.plexon.spawners.integration.core.CoreBridge;
import com.plexon.spawners.integration.core.CoreBridgeFactory;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.listener.NearbyStackCapListener;
import com.plexon.spawners.listener.SpawnerBreakListener;
import com.plexon.spawners.listener.SpawnerChunkListener;
import com.plexon.spawners.listener.SpawnerPlaceListener;
import com.plexon.spawners.listener.SpawnerProvenanceListener;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.SpawnerOriginService;
import com.plexon.spawners.managed.SpawnerStateService;
import com.plexon.spawners.managed.SpawnerTuning;
import com.plexon.spawners.message.MessageService;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PlexonSpawners extends JavaPlugin {
    private static final String LEGACY_SPAWNER_NAME =
        "<gradient:#8A2BE2:#D56BFF><b>%mob% Spawner</b></gradient>";
    private static final List<String> LEGACY_SPAWNER_LORE = List.of(
        "",
        "<gray>Places a <white>%mob%</white> spawner.",
        "",
        "<dark_gray>Spawner Type:</dark_gray> <white>%mob%</white>",
        "<dark_gray>Managed by PlexonSpawners</dark_gray>"
    );
    private static final String PLEXONCRAFT_SPAWNER_NAME =
        "<!italic><gradient:#56B9F2:#92E1FF><b>✦ %mob% Spawner</b></gradient>";
    private static final List<String> PLEXONCRAFT_SPAWNER_LORE = List.of(
        "",
        "<!italic><#D8DEE9>A dormant cage bound to the</#D8DEE9>",
        "<!italic><#D8DEE9>essence of <white>%mob%</white>.</#D8DEE9>",
        "",
        "<!italic><#4B5563>› <#8B95A7>Creature</#8B95A7> <white>%mob%</white>",
        "<!italic><#4B5563>› <#8B95A7>State</#8B95A7> <#72F1B8>Ready to Place</#72F1B8>",
        "",
        "<!italic><#8B95A7>Place to awaken this spawner.</#8B95A7>",
        "<!italic><gradient:#C850C0:#FF7EB3>PlexonCraft</gradient> <dark_gray>• Spawner</dark_gray>"
    );
    private static final List<String> PHASE2_SPAWNER_LORE = List.of(
        "",
        "<!italic><#D8DEE9>A dormant cage bound to the</#D8DEE9>",
        "<!italic><#D8DEE9>essence of <white>%mob%</white>.</#D8DEE9>",
        "",
        "<!italic><#4B5563>› <#8B95A7>Creature</#8B95A7> <white>%mob%</white>",
        "<!italic><#4B5563>› <#8B95A7>Tier</#8B95A7> <#72F1B8>%tier%</#72F1B8>",
        "<!italic><#4B5563>› <#8B95A7>State</#8B95A7> <#72F1B8>Ready to Place</#72F1B8>",
        "",
        "<!italic><#8B95A7>Place to awaken this spawner.</#8B95A7>",
        "<!italic><gradient:#C850C0:#FF7EB3>PlexonCraft</gradient> <dark_gray>• Spawner</dark_gray>"
    );

    private final PluginSettings settings = new PluginSettings();
    private final SpawnerTuning tuning = new SpawnerTuning();
    private final NearbyStackCapSettings nearbyStackCapSettings = new NearbyStackCapSettings();
    private final PerformanceCounters performanceCounters = new PerformanceCounters();

    private MessageService messages;
    private EssenceService essenceService;
    private SpawnerItemService spawnerItemService;
    private PlexonSpawnersApi api;
    private CoreBridge coreBridge;
    private WildStackerCompat wildStackerCompat;
    private ManagedSpawnerRegistry managedRegistry;
    private SpawnerStateService spawnerStateService;
    private SpawnerOriginService spawnerOriginService;
    private BukkitTask persistenceTask;

    @Override
    public void onEnable() {
        coreBridge = CoreBridgeFactory.resolve(this);
        coreBridge.registerStarting();

        try {
            saveDefaultConfig();
            migrateConfig();

            messages = new MessageService(this);
            settings.reload(getConfig());
            tuning.reload(getConfig());
            nearbyStackCapSettings.reload(getConfig());
            reportConfigurationWarnings();
            essenceService = new EssenceService(this);
            spawnerItemService = new SpawnerItemService(this);
            spawnerStateService = new SpawnerStateService(this);
            spawnerOriginService = new SpawnerOriginService(this);
            managedRegistry = new ManagedSpawnerRegistry(this);
            managedRegistry.load();

            api = new PlexonSpawnersApi(
                essenceService,
                spawnerItemService,
                managedRegistry,
                spawnerOriginService
            );
            getServer().getServicesManager().register(PlexonSpawnersApi.class, api, this, ServicePriority.Normal);

            final AdminGui adminGui = new AdminGui(this, essenceService, messages);
            final SpawnerControlGui controlGui = new SpawnerControlGui(
                managedRegistry,
                spawnerStateService,
                tuning,
                essenceService
            );
            final PlexonSpawnersCommand command = new PlexonSpawnersCommand(
                this,
                adminGui,
                essenceService,
                spawnerItemService,
                messages
            );
            wildStackerCompat = new WildStackerCompat(this, detail -> coreBridge.markDegraded(detail));
            final NearbyStackCapListener nearbyStackCapListener = new NearbyStackCapListener(
                managedRegistry,
                nearbyStackCapSettings,
                wildStackerCompat,
                performanceCounters
            );
            wildStackerCompat.setSpawnGuard(nearbyStackCapListener);

            final PluginCommand pluginCommand = getCommand("pspawners");
            if (pluginCommand == null) {
                throw new IllegalStateException("Command 'pspawners' is missing from plugin.yml");
            }
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);

            final SpawnerChunkListener chunkListener = new SpawnerChunkListener(
                managedRegistry,
                spawnerStateService,
                tuning
            );
            getServer().getPluginManager().registerEvents(adminGui, this);
            getServer().getPluginManager().registerEvents(controlGui, this);
            getServer().getPluginManager().registerEvents(chunkListener, this);
            getServer().getPluginManager().registerEvents(nearbyStackCapListener, this);
            getServer().getPluginManager().registerEvents(wildStackerCompat, this);
            getServer().getPluginManager().registerEvents(
                new SpawnerBreakListener(
                    settings,
                    essenceService,
                    spawnerItemService,
                    messages,
                    wildStackerCompat,
                    performanceCounters,
                    managedRegistry,
                    spawnerStateService
                ),
                this
            );
            getServer().getPluginManager().registerEvents(
                new SpawnerPlaceListener(
                    spawnerItemService,
                    spawnerStateService,
                    managedRegistry,
                    tuning,
                    performanceCounters
                ),
                this
            );
            getServer().getPluginManager().registerEvents(
                new SpawnerProvenanceListener(managedRegistry, tuning, spawnerOriginService),
                this
            );

            chunkListener.reconcileAlreadyLoaded();
            schedulePersistenceCoordinator();

            coreBridge.markReady(
                "Managed spawner registry, tiers, nearby logical stack cap, provenance, cached item runtime, API/events and diagnostics ready"
            );
            getLogger().info("PlexonSpawners " + getPluginMeta().getVersion()
                + " enabled for Paper 26.2 in " + coreBridge.mode() + " mode with "
                + managedRegistry.size() + " managed spawners.");
        } catch (RuntimeException | LinkageError exception) {
            coreBridge.markFailed("Critical startup failure: " + exception.getClass().getSimpleName());
            getLogger().log(Level.SEVERE, "PlexonSpawners failed to initialize safely.", exception);
            throw exception;
        }
    }

    @Override
    public void onDisable() {
        if (persistenceTask != null) {
            persistenceTask.cancel();
            persistenceTask = null;
        }
        if (managedRegistry != null) {
            managedRegistry.close();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (coreBridge != null) {
            coreBridge.unregister();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        settings.reload(getConfig());
        tuning.reload(getConfig());
        nearbyStackCapSettings.reload(getConfig());
        if (messages != null) {
            messages.reload();
        }
        if (essenceService != null) {
            essenceService.reload();
        }
        if (spawnerItemService != null) {
            spawnerItemService.reload();
        }
        if (wildStackerCompat != null) {
            wildStackerCompat.refresh();
        }
        schedulePersistenceCoordinator();
        reportConfigurationWarnings();
    }

    public PluginSettings settings() {
        return settings;
    }

    public SpawnerTuning tuning() {
        return tuning;
    }

    public NearbyStackCapSettings nearbyStackCapSettings() {
        return nearbyStackCapSettings;
    }

    public PlexonSpawnersApi api() {
        return api;
    }

    public CoreBridge coreBridge() {
        return coreBridge;
    }

    public WildStackerCompat wildStackerCompat() {
        return wildStackerCompat;
    }

    public EssenceService essenceService() {
        return essenceService;
    }

    public SpawnerItemService spawnerItemService() {
        return spawnerItemService;
    }

    public ManagedSpawnerRegistry managedRegistry() {
        return managedRegistry;
    }

    public PerformanceCounters performanceCounters() {
        return performanceCounters;
    }

    private void schedulePersistenceCoordinator() {
        if (managedRegistry == null) {
            return;
        }
        if (persistenceTask != null) {
            persistenceTask.cancel();
        }
        final long interval = tuning.persistenceIntervalTicks();
        persistenceTask = getServer().getScheduler().runTaskTimer(
            this,
            managedRegistry::flushAsync,
            interval,
            interval
        );
    }

    private void reportConfigurationWarnings() {
        for (final String warning : settings.validationWarnings()) {
            getLogger().warning("Configuration: " + warning);
        }
    }

    private void migrateConfig() {
        boolean changed = false;
        if (!getConfig().contains("config-version")) {
            getConfig().set("config-version", 2);
            changed = true;
        }
        if (!getConfig().contains("breaking.take-ownership")) {
            getConfig().set("breaking.take-ownership", true);
            changed = true;
        }
        if (!getConfig().contains("breaking.allow-silk-bypass-permission")) {
            getConfig().set("breaking.allow-silk-bypass-permission", false);
            changed = true;
        }
        if (!getConfig().contains("essence.default-chance")) {
            getConfig().set("essence.default-chance", 35.0);
            changed = true;
        }

        final int configVersion = getConfig().getInt("config-version", 1);
        if (configVersion < 2) {
            getConfig().set("config-version", 2);
            changed = true;
        }

        if (configVersion < 3) {
            final String currentName = getConfig().getString("spawner-item.name", "");
            final List<String> currentLore = getConfig().getStringList("spawner-item.lore");
            if (LEGACY_SPAWNER_NAME.equals(currentName) && LEGACY_SPAWNER_LORE.equals(currentLore)) {
                getConfig().set("spawner-item.name", PLEXONCRAFT_SPAWNER_NAME);
                getConfig().set("spawner-item.lore", PLEXONCRAFT_SPAWNER_LORE);
                getLogger().info("Updated the stock recovered-spawner style to the PlexonCraft 2.1 theme.");
            }
            getConfig().set("config-version", 3);
            changed = true;
        }

        if (configVersion < 4) {
            getConfig().set("config-version", 4);
            changed = true;
        }

        if (configVersion < 5) {
            final String currentName = getConfig().getString("spawner-item.name", "");
            final List<String> currentLore = getConfig().getStringList("spawner-item.lore");
            if (PLEXONCRAFT_SPAWNER_NAME.equals(currentName) && PLEXONCRAFT_SPAWNER_LORE.equals(currentLore)) {
                getConfig().set("spawner-item.lore", PHASE2_SPAWNER_LORE);
            }
            getConfig().set("config-version", 5);
            changed = true;
        }

        if (configVersion < 6) {
            if (!getConfig().contains("managed.nearby-stack-cap.enabled")) {
                getConfig().set("managed.nearby-stack-cap.enabled", true);
            }
            if (!getConfig().contains("managed.nearby-stack-cap.radius")) {
                getConfig().set("managed.nearby-stack-cap.radius", NearbyStackCapSettings.DEFAULT_RADIUS);
            }
            if (!getConfig().contains("managed.nearby-stack-cap.maximum-amount")) {
                getConfig().set("managed.nearby-stack-cap.maximum-amount", NearbyStackCapSettings.DEFAULT_MAXIMUM);
            }
            if (!getConfig().contains("managed.nearby-stack-cap.same-type-only")) {
                getConfig().set("managed.nearby-stack-cap.same-type-only", true);
            }
            getConfig().set("config-version", 6);
            changed = true;
        }

        if (changed) {
            saveConfig();
            getLogger().info("Updated configuration defaults for PlexonSpawners 3.1 compatibility.");
        }
    }
}
