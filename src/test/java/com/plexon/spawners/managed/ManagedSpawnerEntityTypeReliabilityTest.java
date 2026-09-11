package com.plexon.spawners.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class ManagedSpawnerEntityTypeReliabilityTest {
    @Test
    void managedSpawnerRecordRejectsUnknown() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ManagedSpawner(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                64,
                1,
                EntityType.UNKNOWN,
                UUID.randomUUID(),
                1,
                SpawnerAccess.OWNER_ONLY,
                1L,
                0L
            )
        );
    }

    @Test
    void validManagedSpawnerRecordRemainsCompatible() {
        final ManagedSpawner record = new ManagedSpawner(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            64,
            1,
            EntityType.PIG,
            UUID.randomUUID(),
            3,
            SpawnerAccess.OWNER_ONLY,
            1L,
            42L
        );
        assertEquals(EntityType.PIG, record.type());
        assertEquals(3, record.tier());
        assertEquals(42L, record.lifetimeSpawns());
    }
}
