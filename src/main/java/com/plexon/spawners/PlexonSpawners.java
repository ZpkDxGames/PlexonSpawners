package com.plexon.spawners;

import com.plexon.spawners.command.SpawnersCommand;
import com.plexon.spawners.config.ConfigBootstrap;
import com.plexon.spawners.config.ConfigRevisionService;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.essence.EssenceService;
import com.plexon.spawners.gui.SpawnerWithdrawGui;
import com.plexon.spawners.gui.admin.AdminGuiService;
import com.plexon.spawners.integration.WildStackerBridge;
import com.plexon.spawners.listener.SpawnerBreakListener;
import com.plexon.spawners.message.MessageService;
import com.plexon.spawners.reward.CustomDropService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlexonSpawners extends JavaPlugin {
    private final PluginSettings settings = new PluginSettings();
    private final ConfigRevisionService revisions = new ConfigRevisionService();

    private MessageService messages;
    private EssenceService essenceService;
    private CustomDropService customDropService;
    private WildStackerBridge wildStacker;
    private SpawnerWithdrawGui withdrawGui;
    private AdminGuiService adminGui;

    @Override
    public void onEnable() {
        ConfigBootstrap.ensureV4Config(this);
        settings.reload(getConfig());
        messages = new MessageService(this);
        essenceService = new EssenceService(this, settings);
        customDropService = new CustomDropService(this, settings);
        wildStacker = new WildStackerBridge(this);
        withdrawGui = new SpawnerWithdrawGui(this, settings, messages, wildStacker);
        adminGui = new AdminGuiService(this, settings, messages, essenceService, customDropService, revisions);

        getServer().getPluginManager().registerEvents(
            new SpawnerBreakListener(this, settings, essenceService, customDropService, messages, wildStacker), this);
        getServer().getPluginManager().registerEvents(withdrawGui, this);
        getServer().getPluginManager().registerEvents(adminGui, this);

        final SpawnersCommand command = new SpawnersCommand(this, messages, adminGui);
        final PluginCommand pluginCommand = getCommand("pspawners");
        if (pluginCommand == null) {
            throw new IllegalStateException("Command 'pspawners' is missing from plugin.yml");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        getLogger().info("PlexonSpawners " + getPluginMeta().getVersion()
            + " enabled. WildStacker " + wildStacker.version()
            + " is authoritative for spawner, entity and item stacking.");
    }

    public void reloadRuntime() {
        reloadConfig();
        settings.reload(getConfig());
        messages.reload();
        essenceService.reload();
        customDropService.reload();
    }

    public void reloadPlugin() {
        reloadRuntime();
        revisions.bump();
    }

    public PluginSettings settings() {
        return settings;
    }

    public WildStackerBridge wildStacker() {
        return wildStacker;
    }

    public ConfigRevisionService revisions() {
        return revisions;
    }
}
