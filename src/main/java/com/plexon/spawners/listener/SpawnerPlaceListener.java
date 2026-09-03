package com.plexon.spawners.listener;

import com.plexon.spawners.item.SpawnerItemService;
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
        if (event.getBlockPlaced().getState() instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(type);
            spawner.update(true, false);
        }
    }
}
