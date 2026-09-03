package com.plexon.spawners;

import com.plexon.spawners.api.PlexonSpawnersApi;
import com.plexon.spawners.command.PlexonSpawnersCommand;
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

public final class PlexonSpawners extends JavaPlugin {
    private final PluginSettings settings = new PluginSettings();

    private MessageService messages;
    private EssenceService essenceService;
    private SpawnerItemService spawnerItemService;
    private PlexonSpawnersApi api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data directory.");
        }

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

        final PluginCommand pluginCommand = getCommand("pspawners");
        if (pluginCommand == null) {
            throw new IllegalStateException("Command 'pspawners' is missing from plugin.yml");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        getServer().getPluginManager().registerEvents(adminGui, this);
        getServer().getPluginManager().registerEvents(
            new SpawnerBreakListener(settings, essenceService, spawnerItemService, messages),
            this
        );
        getServer().getPluginManager().registerEvents(new SpawnerPlaceListener(spawnerItemService), this);

        if (getServer().getPluginManager().getPlugin("SilkSpawners") != null) {
            getLogger().warning("SilkSpawners is installed. PlexonSpawners is standalone; running both may create conflicting spawner behavior.");
        }

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
}
