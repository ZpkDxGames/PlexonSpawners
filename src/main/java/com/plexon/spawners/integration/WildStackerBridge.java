package com.plexon.spawners.integration;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.objects.StackedSpawner;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Read/representation bridge only. WildStacker owns every stack mutation.
 */
public final class WildStackerBridge {
    private final String version;

    public WildStackerBridge(final JavaPlugin plugin) {
        final Plugin dependency = plugin.getServer().getPluginManager().getPlugin("WildStacker");
        if (dependency == null || !dependency.isEnabled()) {
            throw new IllegalStateException("WildStacker is required but is not enabled");
        }
        if (WildStackerAPI.getWildStacker() == null) {
            throw new IllegalStateException("WildStacker public API is not initialized");
        }
        version = dependency.getPluginMeta().getVersion();
    }

    public String version() {
        return version;
    }

    public StackedSpawner resolve(final Location location) {
        if (location == null || location.getWorld() == null) return null;
        if (location.getBlock().getType() != Material.SPAWNER) return null;
        if (!(location.getBlock().getState() instanceof CreatureSpawner spawner)) return null;

        // A physical singular spawner may be represented by a valid transient/non-cached object.
        // Cache membership is therefore not a validity check.
        return WildStackerAPI.getStackedSpawner(spawner);
    }

    public ItemStack createSpawnerItem(final StackedSpawner stackedSpawner, final int amount) {
        if (stackedSpawner == null || amount < 1) return null;
        final ItemStack authoritative = stackedSpawner.getDropItem(amount);
        return authoritative == null ? null : authoritative.clone();
    }
}
