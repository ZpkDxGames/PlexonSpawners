package com.plexon.spawners.listener;

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

    public SpawnerPlaceListener(final SpawnerItemService spawnerItemService) {
        this.spawnerItemService = spawnerItemService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerPlace(final BlockPlaceEvent event) {
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

        spawner.setSpawnedType(type);
        if (!spawner.update(true, false)) {
            return;
        }

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
