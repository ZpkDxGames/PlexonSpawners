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
    long lifetimeSpawns,
    int stackAmount,
    SpawnerMigrationState migrationState
) {
    public ManagedSpawner {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(migrationState, "migrationState");
        if (type == EntityType.UNKNOWN) {
            throw new IllegalArgumentException("managed spawner type must not be EntityType.UNKNOWN");
        }
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be >= 1");
        }
        if (lifetimeSpawns < 0L) {
            throw new IllegalArgumentException("lifetimeSpawns must be >= 0");
        }
        if (stackAmount < 1) {
            throw new IllegalArgumentException("stackAmount must be >= 1");
        }
    }

    /** Backwards-compatible constructor used by older callers/tests. */
    public ManagedSpawner(
        final UUID id,
        final UUID worldId,
        final int x,
        final int y,
        final int z,
        final EntityType type,
        final UUID ownerId,
        final int tier,
        final SpawnerAccess access,
        final long placedAtEpochMillis,
        final long lifetimeSpawns
    ) {
        this(id, worldId, x, y, z, type, ownerId, tier, access, placedAtEpochMillis,
            lifetimeSpawns, 1, SpawnerMigrationState.PENDING);
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
            UUID.randomUUID(), worldId, x, y, z, type, ownerId, tier, access,
            System.currentTimeMillis(), 0L, 1, SpawnerMigrationState.NOT_REQUIRED
        );
    }

    public ManagedSpawner withTier(final int newTier) {
        return new ManagedSpawner(id, worldId, x, y, z, type, ownerId, newTier, access,
            placedAtEpochMillis, lifetimeSpawns, stackAmount, migrationState);
    }

    public ManagedSpawner withAccess(final SpawnerAccess newAccess) {
        return new ManagedSpawner(id, worldId, x, y, z, type, ownerId, tier, newAccess,
            placedAtEpochMillis, lifetimeSpawns, stackAmount, migrationState);
    }

    public ManagedSpawner withLifetimeSpawns(final long newLifetimeSpawns) {
        return new ManagedSpawner(id, worldId, x, y, z, type, ownerId, tier, access,
            placedAtEpochMillis, newLifetimeSpawns, stackAmount, migrationState);
    }

    public ManagedSpawner withStackAmount(final int newStackAmount) {
        return new ManagedSpawner(id, worldId, x, y, z, type, ownerId, tier, access,
            placedAtEpochMillis, lifetimeSpawns, newStackAmount, migrationState);
    }

    public ManagedSpawner withMigrationState(final SpawnerMigrationState newMigrationState) {
        return new ManagedSpawner(id, worldId, x, y, z, type, ownerId, tier, access,
            placedAtEpochMillis, lifetimeSpawns, stackAmount, newMigrationState);
    }

    public ManagedSpawner withStackAndMigration(
        final int newStackAmount,
        final SpawnerMigrationState newMigrationState
    ) {
        return new ManagedSpawner(id, worldId, x, y, z, type, ownerId, tier, access,
            placedAtEpochMillis, lifetimeSpawns, newStackAmount, newMigrationState);
    }
}
