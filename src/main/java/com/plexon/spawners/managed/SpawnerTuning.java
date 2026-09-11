package com.plexon.spawners.managed;

import java.util.Collections;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.bukkit.configuration.file.FileConfiguration;

public final class SpawnerTuning {
    private static final int[] DEFAULT_MIN_DELAY = {0, 200, 180, 160, 140, 120};
    private static final int[] DEFAULT_MAX_DELAY = {0, 800, 700, 600, 520, 440};
    private static final int[] DEFAULT_SPAWN_COUNT = {0, 4, 4, 5, 6, 7};
    private static final int[] DEFAULT_NEARBY = {0, 6, 8, 10, 12, 14};
    private static final int[] DEFAULT_PLAYER_RANGE = {0, 16, 18, 20, 22, 24};
    private static final int[] DEFAULT_SPAWN_RANGE = {0, 4, 4, 5, 5, 6};
    private static final int[] DEFAULT_UPGRADE_COST = {0, 0, 16, 32, 48, 64};
    private static final String[] DEFAULT_LABEL = {"", "Initiate", "Awakened", "Empowered", "Ascendant", "Masterwork"};

    private volatile Snapshot runtime = Snapshot.defaults();

    public void reload(final FileConfiguration config) {
        final boolean enabled = config.getBoolean("managed.enabled", true);
        final int persistenceIntervalTicks = clamp(config.getInt("managed.persistence-interval-ticks", 100), 20, 12_000);
        final int provenanceSearchRadius = clamp(config.getInt("managed.provenance-search-radius", 8), 2, 32);
        final int maxManagedPerChunk = clamp(config.getInt("managed.max-managed-per-chunk", 64), 1, 512);
        final int configuredMaxTier = clamp(config.getInt("managed.max-tier", 5), 1, 5);
        final SpawnerAccess defaultAccess = parseAccess(config.getString("managed.default-access", "OWNER_ONLY"));

        final TreeMap<Integer, SpawnerTier> tiers = new TreeMap<>();
        int maximumSpawnRange = 1;
        for (int level = 1; level <= configuredMaxTier; level++) {
            final String path = "managed.tiers." + level + ".";
            final int minDelay = clamp(config.getInt(path + "min-delay", DEFAULT_MIN_DELAY[level]), 20, 72_000);
            final int maxDelay = clamp(config.getInt(path + "max-delay", DEFAULT_MAX_DELAY[level]), minDelay, 72_000);
            final SpawnerTier tier = new SpawnerTier(
                level,
                config.getString(path + "label", DEFAULT_LABEL[level]),
                clamp(config.getInt(path + "upgrade-cost-essence", DEFAULT_UPGRADE_COST[level]), 0, 100_000),
                minDelay,
                maxDelay,
                clamp(config.getInt(path + "spawn-count", DEFAULT_SPAWN_COUNT[level]), 1, 64),
                clamp(config.getInt(path + "max-nearby-entities", DEFAULT_NEARBY[level]), 1, 256),
                clamp(config.getInt(path + "required-player-range", DEFAULT_PLAYER_RANGE[level]), 1, 128),
                clamp(config.getInt(path + "spawn-range", DEFAULT_SPAWN_RANGE[level]), 1, 32)
            );
            tiers.put(level, tier);
            maximumSpawnRange = Math.max(maximumSpawnRange, tier.spawnRange());
        }

        runtime = new Snapshot(
            enabled,
            persistenceIntervalTicks,
            Math.max(provenanceSearchRadius, maximumSpawnRange + 2),
            maxManagedPerChunk,
            defaultAccess,
            Collections.unmodifiableNavigableMap(tiers)
        );
    }

    public boolean enabled() {
        return runtime.enabled();
    }

    public int persistenceIntervalTicks() {
        return runtime.persistenceIntervalTicks();
    }

    public int provenanceSearchRadius() {
        return runtime.provenanceSearchRadius();
    }

    public int maxManagedPerChunk() {
        return runtime.maxManagedPerChunk();
    }

    public SpawnerAccess defaultAccess() {
        return runtime.defaultAccess();
    }

    public int maxTier() {
        return runtime.tiers().lastKey();
    }

    public SpawnerTier tier(final int requestedLevel) {
        final NavigableMap<Integer, SpawnerTier> tiers = runtime.tiers();
        final int bounded = Math.max(tiers.firstKey(), Math.min(tiers.lastKey(), requestedLevel));
        return tiers.get(bounded);
    }

    public SpawnerTier nextTier(final int currentLevel) {
        final var entry = runtime.tiers().higherEntry(currentLevel);
        return entry == null ? null : entry.getValue();
    }

    public int maximumSpawnRange() {
        return runtime.tiers().values().stream().mapToInt(SpawnerTier::spawnRange).max().orElse(4);
    }

    private static SpawnerAccess parseAccess(final String raw) {
        try {
            return SpawnerAccess.valueOf(raw == null ? "OWNER_ONLY" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return SpawnerAccess.OWNER_ONLY;
        }
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Snapshot(
        boolean enabled,
        int persistenceIntervalTicks,
        int provenanceSearchRadius,
        int maxManagedPerChunk,
        SpawnerAccess defaultAccess,
        NavigableMap<Integer, SpawnerTier> tiers
    ) {
        private static Snapshot defaults() {
            final TreeMap<Integer, SpawnerTier> tiers = new TreeMap<>();
            for (int level = 1; level <= 5; level++) {
                tiers.put(level, new SpawnerTier(
                    level,
                    DEFAULT_LABEL[level],
                    DEFAULT_UPGRADE_COST[level],
                    DEFAULT_MIN_DELAY[level],
                    DEFAULT_MAX_DELAY[level],
                    DEFAULT_SPAWN_COUNT[level],
                    DEFAULT_NEARBY[level],
                    DEFAULT_PLAYER_RANGE[level],
                    DEFAULT_SPAWN_RANGE[level]
                ));
            }
            return new Snapshot(true, 100, 8, 64, SpawnerAccess.OWNER_ONLY, Collections.unmodifiableNavigableMap(tiers));
        }
    }
}
