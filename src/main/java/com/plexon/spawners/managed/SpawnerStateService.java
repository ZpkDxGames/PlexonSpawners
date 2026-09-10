package com.plexon.spawners.managed;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnerStateService {
    private static final int STATE_SCHEMA = 1;

    private final NamespacedKey markerKey;
    private final NamespacedKey schemaKey;
    private final NamespacedKey idKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey accessKey;
    private final NamespacedKey placedAtKey;

    public SpawnerStateService(final JavaPlugin plugin) {
        markerKey = new NamespacedKey(plugin, "managed_instance");
        schemaKey = new NamespacedKey(plugin, "managed_state_schema");
        idKey = new NamespacedKey(plugin, "managed_id");
        ownerKey = new NamespacedKey(plugin, "managed_owner");
        tierKey = new NamespacedKey(plugin, "managed_tier");
        accessKey = new NamespacedKey(plugin, "managed_access");
        placedAtKey = new NamespacedKey(plugin, "managed_placed_at");
    }

    public boolean apply(final CreatureSpawner spawner, final ManagedSpawner record, final SpawnerTier tier) {
        spawner.setSpawnedType(record.type());
        spawner.setMinSpawnDelay(tier.minSpawnDelay());
        spawner.setMaxSpawnDelay(tier.maxSpawnDelay());
        spawner.setSpawnCount(tier.spawnCount());
        spawner.setMaxNearbyEntities(tier.maxNearbyEntities());
        spawner.setRequiredPlayerRange(tier.requiredPlayerRange());
        spawner.setSpawnRange(tier.spawnRange());
        if (spawner.getDelay() < tier.minSpawnDelay() || spawner.getDelay() > tier.maxSpawnDelay()) {
            spawner.setDelay(tier.minSpawnDelay());
        }

        final PersistentDataContainer pdc = spawner.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.INTEGER, 1);
        pdc.set(schemaKey, PersistentDataType.INTEGER, STATE_SCHEMA);
        pdc.set(idKey, PersistentDataType.STRING, record.id().toString());
        pdc.set(ownerKey, PersistentDataType.STRING, record.ownerId().toString());
        pdc.set(tierKey, PersistentDataType.INTEGER, record.tier());
        pdc.set(accessKey, PersistentDataType.STRING, record.access().name());
        pdc.set(placedAtKey, PersistentDataType.LONG, record.placedAtEpochMillis());
        return spawner.update(true, false);
    }

    public boolean applyAt(final Location location, final ManagedSpawner record, final SpawnerTier tier) {
        if (!(location.getBlock().getState() instanceof CreatureSpawner spawner)) {
            return false;
        }
        return apply(spawner, record, tier);
    }

    public boolean isManaged(final CreatureSpawner spawner) {
        final Integer marker = spawner.getPersistentDataContainer().get(markerKey, PersistentDataType.INTEGER);
        return marker != null && marker == 1;
    }

    public ManagedSpawner recover(final CreatureSpawner spawner) {
        final PersistentDataContainer pdc = spawner.getPersistentDataContainer();
        final Integer marker = pdc.get(markerKey, PersistentDataType.INTEGER);
        final Integer schema = pdc.get(schemaKey, PersistentDataType.INTEGER);
        if (marker == null || marker != 1 || schema == null || schema != STATE_SCHEMA) {
            return null;
        }

        final String rawId = pdc.get(idKey, PersistentDataType.STRING);
        final String rawOwner = pdc.get(ownerKey, PersistentDataType.STRING);
        final Integer tier = pdc.get(tierKey, PersistentDataType.INTEGER);
        final String rawAccess = pdc.get(accessKey, PersistentDataType.STRING);
        final Long placedAt = pdc.get(placedAtKey, PersistentDataType.LONG);
        final EntityType type = spawner.getSpawnedType();
        if (rawId == null || rawOwner == null || tier == null || rawAccess == null || type == null) {
            return null;
        }

        try {
            return new ManagedSpawner(
                UUID.fromString(rawId),
                spawner.getWorld().getUID(),
                spawner.getX(),
                spawner.getY(),
                spawner.getZ(),
                type,
                UUID.fromString(rawOwner),
                tier,
                SpawnerAccess.valueOf(rawAccess.toUpperCase(Locale.ROOT)),
                placedAt == null ? System.currentTimeMillis() : placedAt,
                0L
            );
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }
}
