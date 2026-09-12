package com.plexon.spawners.config;

import org.bukkit.configuration.file.FileConfiguration;

/** Compiled runtime settings for the managed-spawner nearby logical stack cap. */
public final class NearbyStackCapSettings {
    public static final double DEFAULT_RADIUS = 8.0D;
    public static final int DEFAULT_MAXIMUM = 99;
    private static final double MIN_RADIUS = 1.0D;
    private static final double MAX_RADIUS = 32.0D;
    private static final int MIN_MAXIMUM = 1;
    private static final int MAX_MAXIMUM = 1_000_000;

    private volatile Snapshot runtime = Snapshot.defaults();

    public void reload(final FileConfiguration config) {
        final double configuredRadius = config.getDouble("managed.nearby-stack-cap.radius", DEFAULT_RADIUS);
        final double radius = Double.isFinite(configuredRadius)
            ? clamp(configuredRadius, MIN_RADIUS, MAX_RADIUS)
            : DEFAULT_RADIUS;
        final int maximum = clamp(
            config.getInt("managed.nearby-stack-cap.maximum-amount", DEFAULT_MAXIMUM),
            MIN_MAXIMUM,
            MAX_MAXIMUM
        );
        runtime = new Snapshot(
            config.getBoolean("managed.nearby-stack-cap.enabled", true),
            radius,
            maximum,
            config.getBoolean("managed.nearby-stack-cap.same-type-only", true)
        );
    }

    public boolean enabled() {
        return runtime.enabled();
    }

    public double radius() {
        return runtime.radius();
    }

    public int maximumAmount() {
        return runtime.maximumAmount();
    }

    public boolean sameTypeOnly() {
        return runtime.sameTypeOnly();
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Snapshot(boolean enabled, double radius, int maximumAmount, boolean sameTypeOnly) {
        private static Snapshot defaults() {
            return new Snapshot(true, DEFAULT_RADIUS, DEFAULT_MAXIMUM, true);
        }
    }
}
