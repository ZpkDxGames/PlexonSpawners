package com.plexon.spawners.api;

import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.SpawnerOriginService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

public final class PlexonSpawnersApi {
    private final EssenceService essenceService;
    private final SpawnerItemService spawnerItemService;
    private final ManagedSpawnerRegistry registry;
    private final SpawnerOriginService originService;

    public PlexonSpawnersApi(final EssenceService essenceService, final SpawnerItemService spawnerItemService) {
        this(essenceService, spawnerItemService, null, null);
    }

    public PlexonSpawnersApi(
        final EssenceService essenceService,
        final SpawnerItemService spawnerItemService,
        final ManagedSpawnerRegistry registry,
        final SpawnerOriginService originService
    ) {
        this.essenceService = essenceService;
        this.spawnerItemService = spawnerItemService;
        this.registry = registry;
        this.originService = originService;
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

    public int getSpawnerTier(final ItemStack item) {
        return spawnerItemService.readSpawnerTier(item);
    }

    public ItemStack createSpawner(final EntityType type, final int amount) {
        return spawnerItemService.createSpawner(type, amount);
    }

    public ItemStack createSpawner(final EntityType type, final int amount, final int tier) {
        return spawnerItemService.createSpawner(type, amount, tier);
    }

    public Optional<ManagedSpawner> getManagedSpawner(final Location location) {
        return registry == null ? Optional.empty() : Optional.ofNullable(registry.find(location));
    }

    public Optional<ManagedSpawner> getManagedSpawner(final UUID id) {
        return registry == null ? Optional.empty() : Optional.ofNullable(registry.find(id));
    }

    public List<ManagedSpawner> getManagedSpawnersSnapshot() {
        return registry == null ? List.of() : registry.snapshot();
    }

    public boolean isSpawnerOrigin(final Entity entity) {
        return originService != null && originService.isSpawnerOrigin(entity);
    }

    public Optional<UUID> getOriginSpawnerId(final Entity entity) {
        return originService == null ? Optional.empty() : Optional.ofNullable(originService.sourceSpawnerId(entity));
    }
}
