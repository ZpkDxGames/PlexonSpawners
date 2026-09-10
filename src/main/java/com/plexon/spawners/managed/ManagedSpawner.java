package com.plexon.spawners.managed;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.EntityType;

public record ManagedSpawner(
    UUID id,
    UUID worldId,
    int x,
    int y,
    int z,
    EntityType type,
    UUID ownerId,
    int tier,
    SpawnerAccess access,
    long placedAtEpochMillis,
    long lifetimeSpawns
) {
    public ManagedSpawner {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(access, "access");
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be >= 1");
        }
        if (lifetimeSpawns < 0L) {
            throw new IllegalArgumentException("lifetimeSpawns must be >= 0");
        }
    }

    public static ManagedSpawner placed(
        final UUID worldId,
        final int x,
        final int y,
        final int z,
        final EntityType type,
        final UUID ownerId,
        final int tier,
        final SpawnerAccess access
    ) {
        return new ManagedSpawner(
            UUID.randomUUID(),
            worldId,
            x,
            y,
            z,
            type,
            ownerId,
            tier,
            access,
            System.currentTimeMillis(),
            0L
        );
    }

    public ManagedSpawner withTier(final int newTier) {
        return new ManagedSpawner(
            id, worldId, x, y, z, type, ownerId, newTier, access, placedAtEpochMillis, lifetimeSpawns
        );
    }

    public ManagedSpawner withAccess(final SpawnerAccess newAccess) {
        return new ManagedSpawner(
            id, worldId, x, y, z, type, ownerId, tier, newAccess, placedAtEpochMillis, lifetimeSpawns
        );
    }

    public ManagedSpawner withLifetimeSpawns(final long newLifetimeSpawns) {
        return new ManagedSpawner(
            id, worldId, x, y, z, type, ownerId, tier, access, placedAtEpochMillis, newLifetimeSpawns
        );
    }
}
