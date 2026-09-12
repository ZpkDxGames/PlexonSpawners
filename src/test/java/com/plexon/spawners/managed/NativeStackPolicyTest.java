package com.plexon.spawners.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

final class NativeStackPolicyTest {
    @Test
    void mergesBasicUnits() {
        assertEquals(new NativeStackPolicy.MergeResult(2, 0, 1), NativeStackPolicy.merge(1, 1, 64));
        assertEquals(new NativeStackPolicy.MergeResult(64, 0, 1), NativeStackPolicy.merge(63, 1, 64));
    }

    @Test
    void preservesOverflowRemainder() {
        assertEquals(new NativeStackPolicy.MergeResult(64, 4, 4), NativeStackPolicy.merge(60, 8, 64));
    }

    @Test
    void fullTargetConsumesNothing() {
        assertEquals(new NativeStackPolicy.MergeResult(64, 8, 0), NativeStackPolicy.merge(64, 8, 64));
    }

    @Test
    void rejectsInvalidAmounts() {
        assertThrows(IllegalArgumentException.class, () -> NativeStackPolicy.merge(0, 1, 64));
        assertThrows(IllegalArgumentException.class, () -> NativeStackPolicy.merge(1, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> NativeStackPolicy.merge(1, 1, 0));
    }

    @Test
    void scalesAndBoundsSpawnOutput() {
        assertEquals(50, NativeStackPolicy.requestedLogicalOutput(5, 10, 64, true));
        assertEquals(64, NativeStackPolicy.requestedLogicalOutput(5, 100, 64, true));
        assertEquals(5, NativeStackPolicy.requestedLogicalOutput(5, 100, 64, false));
    }

    @Test
    void nearbyCapWinsAfterStackScaling() {
        final int requested = NativeStackPolicy.requestedLogicalOutput(5, 10, 64, true);
        assertEquals(2, NativeStackPolicy.allowedLogicalOutput(requested, 97, 99, true));
        assertEquals(19, NativeStackPolicy.allowedLogicalOutput(requested, 80, 99, true));
    }

    @Test
    void upgradePricingUsesLogicalStackAmount() {
        assertEquals(540_000L, NativeStackPolicy.upgradeCost(135_000, 4));
    }

    @Test
    void compatibilityRequiresConfiguredDimensions() {
        final UUID world = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();
        final ManagedSpawner left = record(world, owner, EntityType.ZOMBIE, 3, SpawnerAccess.OWNER_ONLY);
        final ManagedSpawner same = record(world, owner, EntityType.ZOMBIE, 3, SpawnerAccess.OWNER_ONLY);
        final ManagedSpawner otherOwner = record(world, UUID.randomUUID(), EntityType.ZOMBIE, 3, SpawnerAccess.OWNER_ONLY);
        final ManagedSpawner otherTier = record(world, owner, EntityType.ZOMBIE, 2, SpawnerAccess.OWNER_ONLY);
        final ManagedSpawner otherType = record(world, owner, EntityType.SKELETON, 3, SpawnerAccess.OWNER_ONLY);
        final ManagedSpawner otherAccess = record(world, owner, EntityType.ZOMBIE, 3, SpawnerAccess.PUBLIC);

        assertTrue(NativeStackPolicy.compatible(left, same, true, true, true, true));
        assertFalse(NativeStackPolicy.compatible(left, otherOwner, true, true, true, true));
        assertFalse(NativeStackPolicy.compatible(left, otherTier, true, true, true, true));
        assertFalse(NativeStackPolicy.compatible(left, otherType, true, true, true, true));
        assertFalse(NativeStackPolicy.compatible(left, otherAccess, true, true, true, true));
        assertTrue(NativeStackPolicy.compatible(left, otherOwner, true, false, true, true));
    }

    @Test
    void migrationStatesFailClosedUntilResolved() {
        assertTrue(SpawnerMigrationState.PENDING.blocksMutation());
        assertTrue(SpawnerMigrationState.MIGRATING.blocksMutation());
        assertTrue(SpawnerMigrationState.CONFLICT.blocksMutation());
        assertFalse(SpawnerMigrationState.MIGRATED.blocksMutation());
        assertFalse(SpawnerMigrationState.NOT_REQUIRED.blocksMutation());
    }

    private static ManagedSpawner record(
        final UUID world,
        final UUID owner,
        final EntityType type,
        final int tier,
        final SpawnerAccess access
    ) {
        return new ManagedSpawner(
            UUID.randomUUID(), world, 1, 64, 1, type, owner, tier, access,
            1L, 0L, 1, SpawnerMigrationState.NOT_REQUIRED);
    }
}
