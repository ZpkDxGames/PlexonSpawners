package com.plexon.spawners.listener;

import com.plexon.spawners.config.NativeStackSettings;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Protects one physical block representing many logical spawners from unsafe vanilla mutation paths. */
public final class SpawnerProtectionListener implements Listener {
    private final ManagedSpawnerRegistry registry;
    private final NativeStackSettings settings;

    public SpawnerProtectionListener(final ManagedSpawnerRegistry registry, final NativeStackSettings settings) {
        this.registry = registry;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event) {
        if (!settings.enabled() || !settings.protectManagedFromExplosions()) {
            return;
        }
        event.blockList().removeIf(this::isManagedSpawner);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent event) {
        if (!settings.enabled() || !settings.protectManagedFromExplosions()) {
            return;
        }
        event.blockList().removeIf(this::isManagedSpawner);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(final BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isManagedSpawner)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(final BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isManagedSpawner)) {
            event.setCancelled(true);
        }
    }

    private boolean isManagedSpawner(final Block block) {
        return block.getType() == Material.SPAWNER && registry.find(block.getLocation()) != null;
    }
}
