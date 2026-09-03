package com.plexon.spawners;

import com.plexon.spawners.api.PlexonSpawnersApi;
import com.plexon.spawners.command.PlexonSpawnersCommand;
import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.gui.AdminGui;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.listener.SpawnerBreakListener;
import com.plexon.spawners.listener.SpawnerPlaceListener;
import com.plexon.spawners.message.MessageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

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

    private final PluginSettings settings = new PluginSettings();

    private MessageService messages;
    private EssenceService essenceService;
    private SpawnerItemService spawnerItemService;
    private PlexonSpawnersApi api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();

        messages = new MessageService(this);
        settings.reload(getConfig());
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
        final WildStackerCompat wildStackerCompat = new WildStackerCompat(this);

        final PluginCommand pluginCommand = getCommand("pspawners");
        if (pluginCommand == null) {
            throw new IllegalStateException("Command 'pspawners' is missing from plugin.yml");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        getServer().getPluginManager().registerEvents(adminGui, this);
        getServer().getPluginManager().registerEvents(
            new SpawnerBreakListener(settings, essenceService, spawnerItemService, messages, wildStackerCompat),
            this
        );
        getServer().getPluginManager().registerEvents(new SpawnerPlaceListener(spawnerItemService), this);

        getLogger().info("PlexonSpawners " + getPluginMeta().getVersion() + " enabled for Paper 26.2.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }

    public void reloadPlugin() {
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
    }

    public PluginSettings settings() {
        return settings;
    }

    public PlexonSpawnersApi api() {
        return api;
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

        if (changed) {
            saveConfig();
            getLogger().info("Updated configuration defaults for PlexonSpawners 2.1.");
        }
    }
}
