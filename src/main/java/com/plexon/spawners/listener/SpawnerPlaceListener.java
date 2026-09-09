package com.plexon.spawners.listener;

import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.event.PlexonSpawnerPlacedEvent;
import com.plexon.spawners.item.SpawnerItemService;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class SpawnerPlaceListener implements Listener {
    private final SpawnerItemService spawnerItemService;
    private final PerformanceCounters counters;

    public SpawnerPlaceListener(
        final SpawnerItemService spawnerItemService,
        final PerformanceCounters counters
    ) {
        this.spawnerItemService = spawnerItemService;
        this.counters = counters;
    }

    /**
     * Apply the managed mob type after normal protection listeners have had a
     * chance to reject placement. This handler mutates the block; MONITOR does not.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void applyManagedSpawnerType(final BlockPlaceEvent event) {
        counters.blockPlaceSeen();
        if (event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }

        final EntityType type = spawnerItemService.readSpawnerType(event.getItemInHand());
        if (type == null) {
            counters.vanillaSpawnerPlacementReject();
            return;
        }
        if (!(event.getBlockPlaced().getState() instanceof CreatureSpawner spawner)) {
            return;
        }

        spawner.setSpawnedType(type);
        spawner.update(true, false);
    }

    /**
     * Publish success only after every mutable event priority has completed and
     * the original placement is still accepted. This handler is read-only.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void observeManagedSpawnerPlacement(final BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }

        final EntityType type = spawnerItemService.readSpawnerType(event.getItemInHand());
        if (type == null) {
            return;
        }
        if (!(event.getBlockPlaced().getState() instanceof CreatureSpawner spawner)) {
            return;
        }
        if (spawner.getSpawnedType() != type) {
            return;
        }

        counters.managedPlacementSuccess();
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("PlexonSpawners placement events must fire on the primary server thread");
        }

        final String transactionId = UUID.randomUUID().toString();
        Bukkit.getPluginManager().callEvent(new PlexonSpawnerPlacedEvent(
            event.getPlayer(),
            type,
            event.getBlockPlaced().getLocation(),
            event.getItemInHand(),
            transactionId + ":placed",
            transactionId
        ));
    }
}
