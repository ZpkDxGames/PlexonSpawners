package com.plexon.spawners.integration;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.enums.UnstackResult;
import com.bgsoftware.wildstacker.api.objects.StackedSpawner;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class WildStackerBridge {
    private final String version;
    private final ThreadLocal<Integer> withdrawalDepth = ThreadLocal.withInitial(() -> 0);

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
        final StackedSpawner stacked = WildStackerAPI.getStackedSpawner(spawner);
        return stacked != null && stacked.isCached() ? stacked : null;
    }

    public ItemStack createSpawnerItem(final StackedSpawner stackedSpawner, final int amount) {
        if (stackedSpawner == null || amount < 1) return null;
        return stackedSpawner.getDropItem(amount);
    }

    public UnstackResult withdraw(final StackedSpawner stackedSpawner, final int amount, final Player player) {
        final int depth = withdrawalDepth.get();
        withdrawalDepth.set(depth + 1);
        try {
            return stackedSpawner.runUnstack(amount, player);
        } finally {
            if (depth == 0) withdrawalDepth.remove();
            else withdrawalDepth.set(depth);
        }
    }

    public boolean isWithdrawalInProgress() {
        return withdrawalDepth.get() > 0;
    }
}
