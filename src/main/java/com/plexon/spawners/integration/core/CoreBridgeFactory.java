package com.plexon.spawners.integration.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Resolves the required PlexonCore bridge.
 *
 * <p>PlexonSpawners is a PlexonCore module. A present-but-unusable Core must not
 * be silently converted into a legacy/standalone runtime, because that leaves
 * the gameplay plugin enabled while the ecosystem health view reports a false
 * legacy state. Startup therefore fails fast with a precise reason whenever
 * the required Core runtime cannot be bound.</p>
 */
public final class CoreBridgeFactory {
    private static final String CORE_PLUGIN = "PlexonCore";

    private CoreBridgeFactory() {}

    public static CoreBridge resolve(JavaPlugin plugin) {
        final Plugin corePlugin = Bukkit.getPluginManager().getPlugin(CORE_PLUGIN);
        if (corePlugin == null) {
            throw new IllegalStateException("Required dependency PlexonCore is not installed");
        }
        if (!corePlugin.isEnabled()) {
            throw new IllegalStateException(
                "Required dependency PlexonCore " + corePlugin.getPluginMeta().getVersion() + " is not enabled"
            );
        }

        final CoreBridge bridge = new PlexonCoreBridge(plugin);
        if (!bridge.available() || !bridge.compatible()) {
            throw new IllegalStateException(
                "PlexonCore API " + bridge.apiVersion() + " is outside supported range "
                    + CoreBridge.SUPPORTED_API_RANGE
            );
        }
        return bridge;
    }
}
