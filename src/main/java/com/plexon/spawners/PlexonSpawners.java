package com.plexon.spawners;

import com.plexon.spawners.api.PlexonSpawnersApi;
import com.plexon.spawners.command.PlexonSpawnersCommand;
import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.gui.AdminGui;
import com.plexon.spawners.integration.core.CoreBridge;
import com.plexon.spawners.integration.core.CoreBridgeFactory;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.listener.SpawnerBreakListener;
import com.plexon.spawners.listener.SpawnerPlaceListener;
import com.plexon.spawners.message.MessageService;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlexonSpawners extends JavaPlugin implements Listener {
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

    private final PluginSettings settings = new PluginSettings();
    private final PerformanceCounters performanceCounters = new PerformanceCounters();

    private MessageService messages;
    private EssenceService essenceService;
    private SpawnerItemService spawnerItemService;
    private PlexonSpawnersApi api;
    private CoreBridge coreBridge;
    private WildStackerCompat wildStackerCompat;

    @Override
    public void onEnable() {
        try {
            coreBridge = CoreBridgeFactory.resolve(this);
            coreBridge.registerStarting();
            if (!"CORE".equals(coreBridge.mode())) {
                throw new IllegalStateException(
                    "PlexonCore module registration did not reach CORE mode: " + coreBridge.registrationState()
                );
            }

            saveDefaultConfig();
            migrateConfig();

            messages = new MessageService(this, coreBridge);
            settings.reload(getConfig());
            reportConfigurationWarnings();
            essenceService = new EssenceService(this);
            spawnerItemService = new SpawnerItemService(this);
            api = new PlexonSpawnersApi(essenceService, spawnerItemService);
            getServer().getServicesManager().register(PlexonSpawnersApi.class, api, this, ServicePriority.Normal);

            final AdminGui adminGui = new AdminGui(this, essenceService, messages);
            final PlexonSpawnersCommand command = new PlexonSpawnersCommand(
                this,
                adminGui,
                essenceService,
                spawnerItemService,
                messages
            );
            wildStackerCompat = new WildStackerCompat(this, detail -> coreBridge.markDegraded(detail));

            final PluginCommand pluginCommand = getCommand("pspawners");
            if (pluginCommand == null) {
                throw new IllegalStateException("Command 'pspawners' is missing from plugin.yml");
            }
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);

            getServer().getPluginManager().registerEvents(this, this);
            getServer().getPluginManager().registerEvents(adminGui, this);
            getServer().getPluginManager().registerEvents(wildStackerCompat, this);
            getServer().getPluginManager().registerEvents(
                new SpawnerBreakListener(
                    settings,
                    essenceService,
                    spawnerItemService,
                    messages,
                    wildStackerCompat,
                    performanceCounters
                ),
                this
            );
            getServer().getPluginManager().registerEvents(
                new SpawnerPlaceListener(spawnerItemService, performanceCounters),
                this
            );

            coreBridge.markReady(
                "Core-native spawner engine, shared text, item runtime, public API/events, integrations and diagnostics ready"
            );
            getLogger().info("PlexonSpawners " + getPluginMeta().getVersion()
                + " enabled for Paper 26.2 in " + coreBridge.mode()
                + " mode with PlexonCore API " + coreBridge.apiVersion() + ".");
        } catch (RuntimeException | LinkageError exception) {
            getServer().getServicesManager().unregisterAll(this);
            if (coreBridge != null) {
                try {
                    coreBridge.markFailed("Critical startup failure: " + exception.getClass().getSimpleName());
                } catch (RuntimeException | LinkageError coreFailure) {
                    exception.addSuppressed(coreFailure);
                }
            }
            getLogger().log(Level.SEVERE,
                "PlexonSpawners failed to initialize as a PlexonCore module; standalone fallback is disabled.",
                exception
            );
            throw exception;
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (coreBridge != null) {
            coreBridge.unregister();
        }
    }

    @EventHandler
    public void onRequiredCoreDisable(final PluginDisableEvent event) {
        if (!event.getPlugin().getName().equalsIgnoreCase("PlexonCore") || !isEnabled()) {
            return;
        }
        getLogger().severe("PlexonCore was disabled while PlexonSpawners was active; disabling PlexonSpawners safely.");
        getServer().getPluginManager().disablePlugin(this);
    }

    public void reloadPlugin() {
        try {
            reloadConfig();
            settings.reload(getConfig());
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
            reportConfigurationWarnings();
            if (coreBridge != null) {
                coreBridge.markReady("Configuration and cached runtime services reloaded successfully");
            }
        } catch (RuntimeException | LinkageError exception) {
            if (coreBridge != null) {
                coreBridge.markDegraded("Reload failed: " + exception.getClass().getSimpleName());
            }
            throw exception;
        }
    }

    public PluginSettings settings() {
        return settings;
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

    public PerformanceCounters performanceCounters() {
        return performanceCounters;
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

        if (changed) {
            saveConfig();
            getLogger().info("Updated configuration defaults for PlexonSpawners 2.3 compatibility.");
        }
    }
}
