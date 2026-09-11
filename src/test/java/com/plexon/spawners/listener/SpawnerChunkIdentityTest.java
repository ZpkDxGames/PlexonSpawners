package com.plexon.spawners.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.SpawnerAccess;
import java.util.UUID;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class SpawnerChunkIdentityTest {
    @Test
    void physicalIdentityRequiresManagedUuidAndExactBlockCoordinates() {
        final UUID id = UUID.randomUUID();
        final UUID worldId = UUID.randomUUID();
        final ManagedSpawner expected = record(id, worldId, 10, 64, -20, UUID.randomUUID(), 5, SpawnerAccess.OWNER_ONLY);

        assertTrue(SpawnerChunkListener.samePhysicalIdentity(
            expected,
            record(id, worldId, 10, 64, -20, UUID.randomUUID(), 1, SpawnerAccess.PUBLIC)
        ));
        assertFalse(SpawnerChunkListener.samePhysicalIdentity(
            expected,
            record(UUID.randomUUID(), worldId, 10, 64, -20, expected.ownerId(), 5, SpawnerAccess.OWNER_ONLY)
        ));
        assertFalse(SpawnerChunkListener.samePhysicalIdentity(
            expected,
            record(id, UUID.randomUUID(), 10, 64, -20, expected.ownerId(), 5, SpawnerAccess.OWNER_ONLY)
        ));
        assertFalse(SpawnerChunkListener.samePhysicalIdentity(
            expected,
            record(id, worldId, 11, 64, -20, expected.ownerId(), 5, SpawnerAccess.OWNER_ONLY)
        ));
        assertFalse(SpawnerChunkListener.samePhysicalIdentity(expected, null));
    }

    private static ManagedSpawner record(
        final UUID id,
        final UUID worldId,
        final int x,
        final int y,
        final int z,
        final UUID ownerId,
        final int tier,
        final SpawnerAccess access
    ) {
        return new ManagedSpawner(
            id,
            worldId,
            x,
            y,
            z,
            EntityType.ZOMBIE,
            ownerId,
            tier,
            access,
            1L,
            0L
        );
    }
}
