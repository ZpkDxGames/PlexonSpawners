package com.plexon.spawners.api;

import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

public final class PlexonSpawnersApi {
    private final EssenceService essenceService;
    private final SpawnerItemService spawnerItemService;

    public PlexonSpawnersApi(final EssenceService essenceService, final SpawnerItemService spawnerItemService) {
        this.essenceService = essenceService;
        this.spawnerItemService = spawnerItemService;
    }

    public boolean isSpawnerEssence(final ItemStack item) {
        return essenceService.isEssence(item);
    }

    public ItemStack createSpawnerEssence(final int amount) {
        return essenceService.create(amount);
    }

    public boolean isManagedSpawner(final ItemStack item) {
        return spawnerItemService.isManagedSpawner(item);
    }

    public EntityType getSpawnerType(final ItemStack item) {
        return spawnerItemService.readSpawnerType(item);
    }

    public ItemStack createSpawner(final EntityType type, final int amount) {
        return spawnerItemService.createSpawner(type, amount);
    }
}
