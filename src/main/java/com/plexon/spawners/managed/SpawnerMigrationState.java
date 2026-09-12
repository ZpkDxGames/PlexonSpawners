package com.plexon.spawners.managed;

/** Persisted one-time ownership transition from WildStacker spawner stacks to Plexon. */
public enum SpawnerMigrationState {
    PENDING,
    MIGRATING,
    MIGRATED,
    NOT_REQUIRED,
    CONFLICT;

    public boolean blocksMutation() {
        return this == PENDING || this == MIGRATING || this == CONFLICT;
    }
}
