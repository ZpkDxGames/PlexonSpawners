package com.plexon.spawners.integration.core;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoreBridgeFactory {
    private CoreBridgeFactory() {}

    public static CoreBridge resolve(final JavaPlugin plugin) {
        final Plugin corePlugin = plugin.getServer().getPluginManager().getPlugin("PlexonCore");
        if (corePlugin == null) {
            plugin.getLogger().info("PlexonCore not installed; using bounded standalone lifecycle/scheduling bridge.");
            return new StandaloneCoreBridge(plugin, false, "-", "PlexonCore is not installed");
        }
        if (!corePlugin.isEnabled()) {
            plugin.getLogger().warning("PlexonCore is installed but disabled; using bounded standalone bridge.");
            return new StandaloneCoreBridge(plugin, true, corePlugin.getPluginMeta().getVersion(),
                "PlexonCore is installed but disabled");
        }
        try {
            return new PlexonCoreBridge(plugin);
        } catch (final LinkageError | RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                "PlexonCore 2.1 API could not be linked safely; continuing standalone.", exception);
            return new StandaloneCoreBridge(plugin, true, corePlugin.getPluginMeta().getVersion(),
                "PlexonCore API service/linkage is unavailable");
        }
    }
}
