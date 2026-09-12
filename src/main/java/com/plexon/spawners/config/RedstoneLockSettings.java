package com.plexon.spawners.config;

import org.bukkit.configuration.file.FileConfiguration;

/** Runtime snapshot for managed-spawner redstone locking. */
public final class RedstoneLockSettings {
    public static final long DEFAULT_POLL_INTERVAL_TICKS = 20L;

    private volatile Snapshot snapshot = new Snapshot(true, DEFAULT_POLL_INTERVAL_TICKS);

    public void reload(final FileConfiguration config) {
        final boolean enabled = config.getBoolean("managed.redstone-lock.enabled", true);
        final long configuredInterval = config.getLong(
            "managed.redstone-lock.poll-interval-ticks",
            DEFAULT_POLL_INTERVAL_TICKS
        );
        final long interval = Math.max(1L, Math.min(200L, configuredInterval));
        snapshot = new Snapshot(enabled, interval);
    }

    public boolean enabled() {
        return snapshot.enabled();
    }

    public long pollIntervalTicks() {
        return snapshot.pollIntervalTicks();
    }

    private record Snapshot(boolean enabled, long pollIntervalTicks) {}
}
