package com.plexon.spawners.managed;

public record SpawnerTier(
    int level,
    String label,
    int upgradeCostEssence,
    int minSpawnDelay,
    int maxSpawnDelay,
    int spawnCount,
    int maxNearbyEntities,
    int requiredPlayerRange,
    int spawnRange
) {
    public SpawnerTier {
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1");
        }
        if (label == null || label.isBlank()) {
            label = "Tier " + level;
        }
        if (upgradeCostEssence < 0) {
            throw new IllegalArgumentException("upgradeCostEssence must be >= 0");
        }
        if (minSpawnDelay < 1 || maxSpawnDelay < minSpawnDelay) {
            throw new IllegalArgumentException("invalid spawn delay range");
        }
        if (spawnCount < 1 || maxNearbyEntities < 1 || requiredPlayerRange < 1 || spawnRange < 1) {
            throw new IllegalArgumentException("spawner tuning values must be positive");
        }
    }
}
