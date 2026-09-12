package com.plexon.spawners.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

/** Immutable, validated runtime settings for first-party Plexon spawner stacking. */
public final class NativeStackSettings {
    public enum SpawnMode {
        LINEAR,
        BOUNDED_LINEAR
    }

    public enum EntityAggregationBackend {
        AUTO,
        PHYSICAL
    }

    public static final int DEFAULT_MAX_STACK_SIZE = 64;
    public static final int HARD_MAX_STACK_SIZE = 4096;
    public static final int DEFAULT_VERTICAL_RANGE = 8;
    public static final int DEFAULT_CYCLE_OUTPUT_CAP = 64;
    public static final int DEFAULT_VANILLA_PHYSICAL_CAP = 16;
    public static final double DEFAULT_ENTITY_AGGREGATION_RADIUS = 8.0D;

    private volatile Snapshot runtime = Snapshot.defaults();

    public void reload(final FileConfiguration config) {
        final List<String> warnings = new ArrayList<>();
        final boolean enabled = config.getBoolean("managed.stacking.enabled", true);
        final int maxStackSize = boundedInt(config, "managed.stacking.max-stack-size", DEFAULT_MAX_STACK_SIZE, 1, HARD_MAX_STACK_SIZE, warnings);
        final boolean autoStackEnabled = config.getBoolean("managed.stacking.auto-stack.enabled", true);
        final boolean verticalEnabled = config.getBoolean("managed.stacking.auto-stack.vertical.enabled", true);
        final int verticalRange = boundedInt(config, "managed.stacking.auto-stack.vertical.range", DEFAULT_VERTICAL_RANGE, 1, 32, warnings);
        final boolean nearbyEnabled = config.getBoolean("managed.stacking.auto-stack.nearby.enabled", true);
        final int nearbyRadius = boundedInt(config, "managed.stacking.auto-stack.nearby.radius", 1, 1, 8, warnings);

        final boolean sameType = config.getBoolean("managed.stacking.compatibility.require-same-entity-type", true);
        final boolean sameOwner = config.getBoolean("managed.stacking.compatibility.require-same-owner", true);
        final boolean sameTier = config.getBoolean("managed.stacking.compatibility.require-same-tier", true);
        final boolean sameAccess = config.getBoolean("managed.stacking.compatibility.require-same-access-mode", true);

        final boolean displayEnabled = config.getBoolean("managed.stacking.display.enabled", true);
        final boolean hideTitle = config.getBoolean("managed.stacking.display.hide-title", false);
        final boolean hideSingle = config.getBoolean("managed.stacking.display.hide-single", true);
        final String displayFormat = nonBlank(config.getString("managed.stacking.display.format"),
            "<yellow>%entity% Spawner</yellow> <gray>x%amount%</gray>");
        final boolean showTier = config.getBoolean("managed.stacking.display.show-tier", true);
        final String tierFormat = nonBlank(config.getString("managed.stacking.display.tier-format"),
            " <dark_gray>•</dark_gray> <gray>%tier%</gray>");
        final double viewDistance = boundedDouble(config, "managed.stacking.display.view-distance", 16.0D, 1.0D, 64.0D, warnings);
        final double verticalOffset = boundedDouble(config, "managed.stacking.display.vertical-offset", 1.25D, -2.0D, 8.0D, warnings);

        final boolean normalBreakAll = "ALL".equalsIgnoreCase(config.getString("managed.stacking.breaking.normal-break.mode", "ONE"));
        final boolean sneakBreakAll = "ALL".equalsIgnoreCase(config.getString("managed.stacking.breaking.sneak-break.mode", "ALL"));
        final boolean requireOwner = config.getBoolean("managed.stacking.breaking.require-owner", true);

        final boolean withdrawEnabled = config.getBoolean("managed.stacking.withdraw.enabled", true);
        final List<Integer> withdrawPresets = new ArrayList<>();
        final List<?> configuredPresets = config.getList("managed.stacking.withdraw.presets", List.of(1, 8, 16));
        for (final Object raw : configuredPresets == null ? List.of(1, 8, 16) : configuredPresets) {
            final Integer value = parsePositiveInt(raw);
            if (value == null || value > HARD_MAX_STACK_SIZE) {
                warnings.add("Invalid managed.stacking.withdraw.presets entry '" + raw + "'; ignoring it.");
                continue;
            }
            if (!withdrawPresets.contains(value)) {
                withdrawPresets.add(value);
            }
        }
        if (withdrawPresets.isEmpty()) {
            withdrawPresets.addAll(List.of(1, 8, 16));
        }

        final boolean scaleWithStack = config.getBoolean("managed.stacking.spawning.scale-with-stack", true);
        final SpawnMode spawnMode = parseSpawnMode(config.getString("managed.stacking.spawning.mode", "BOUNDED_LINEAR"), warnings);
        final int cycleOutputCap = boundedInt(config, "managed.stacking.spawning.max-logical-output-per-cycle",
            DEFAULT_CYCLE_OUTPUT_CAP, 1, 4096, warnings);
        final boolean respectLogicalCap = config.getBoolean("managed.stacking.spawning.respect-nearby-logical-cap", true);
        final int vanillaPhysicalCap = boundedInt(config, "managed.stacking.spawning.vanilla-physical-output-cap",
            DEFAULT_VANILLA_PHYSICAL_CAP, 1, 128, warnings);

        final boolean aggregationEnabled = config.getBoolean("managed.stacking.spawning.entity-aggregation.enabled", true);
        final double aggregationRadius = boundedDouble(config,
            "managed.stacking.spawning.entity-aggregation.radius",
            DEFAULT_ENTITY_AGGREGATION_RADIUS, 1.0D, 32.0D, warnings);
        final boolean preferExistingStack = config.getBoolean(
            "managed.stacking.spawning.entity-aggregation.prefer-existing-stack", true);
        final EntityAggregationBackend aggregationBackend = parseAggregationBackend(
            config.getString("managed.stacking.spawning.entity-aggregation.backend", "AUTO"), warnings);

        final boolean showStackAmount = config.getBoolean("managed.stacking.items.show-stack-amount", true);
        final boolean hideSingleAmount = config.getBoolean("managed.stacking.items.hide-single-amount", true);
        final boolean creativeConsume = config.getBoolean("managed.stacking.creative.consume-on-place", false);
        final boolean creativeDrop = config.getBoolean("managed.stacking.creative.drop-on-break", false);
        final boolean protectExplosions = config.getBoolean("managed.stacking.explosions.protect-managed-stacks", true);

        final boolean migrationEnabled = config.getBoolean("managed.stacking.migration.wildstacker.enabled", true);
        final boolean autoImport = config.getBoolean("managed.stacking.migration.wildstacker.auto-import", true);
        final boolean warnConflict = config.getBoolean("managed.stacking.migration.wildstacker.warn-if-spawner-stacking-still-enabled", true);

        runtime = new Snapshot(
            enabled, maxStackSize,
            autoStackEnabled, verticalEnabled, verticalRange, nearbyEnabled, nearbyRadius,
            sameType, sameOwner, sameTier, sameAccess,
            displayEnabled, hideTitle, hideSingle, displayFormat, showTier, tierFormat, viewDistance, verticalOffset,
            normalBreakAll, sneakBreakAll, requireOwner,
            withdrawEnabled, List.copyOf(withdrawPresets),
            scaleWithStack, spawnMode, cycleOutputCap, respectLogicalCap, vanillaPhysicalCap,
            aggregationEnabled, aggregationRadius, preferExistingStack, aggregationBackend,
            showStackAmount, hideSingleAmount, creativeConsume, creativeDrop, protectExplosions,
            migrationEnabled, autoImport, warnConflict, List.copyOf(warnings)
        );
    }

    public boolean enabled() { return runtime.enabled(); }
    public int maxStackSize() { return runtime.maxStackSize(); }
    public boolean autoStackEnabled() { return runtime.autoStackEnabled(); }
    public boolean verticalEnabled() { return runtime.verticalEnabled(); }
    public int verticalRange() { return runtime.verticalRange(); }
    public boolean nearbyEnabled() { return runtime.nearbyEnabled(); }
    public int nearbyRadius() { return runtime.nearbyRadius(); }
    public boolean requireSameEntityType() { return runtime.requireSameEntityType(); }
    public boolean requireSameOwner() { return runtime.requireSameOwner(); }
    public boolean requireSameTier() { return runtime.requireSameTier(); }
    public boolean requireSameAccess() { return runtime.requireSameAccess(); }
    public boolean displayEnabled() { return runtime.displayEnabled(); }
    public boolean hideTitle() { return runtime.hideTitle(); }
    public boolean hideSingle() { return runtime.hideSingle(); }
    public String displayFormat() { return runtime.displayFormat(); }
    public boolean showTier() { return runtime.showTier(); }
    public String tierFormat() { return runtime.tierFormat(); }
    public double viewDistance() { return runtime.viewDistance(); }
    public double verticalOffset() { return runtime.verticalOffset(); }
    public boolean normalBreakAll() { return runtime.normalBreakAll(); }
    public boolean sneakBreakAll() { return runtime.sneakBreakAll(); }
    public boolean requireOwner() { return runtime.requireOwner(); }
    public boolean withdrawEnabled() { return runtime.withdrawEnabled(); }
    public List<Integer> withdrawPresets() { return runtime.withdrawPresets(); }
    public boolean scaleWithStack() { return runtime.scaleWithStack(); }
    public SpawnMode spawnMode() { return runtime.spawnMode(); }

    /**
     * Effective logical cycle ceiling. LINEAR intentionally has no configured
     * cycle ceiling; downstream arithmetic still saturates at Integer.MAX_VALUE
     * and the nearby logical cap remains authoritative when enabled.
     */
    public int maxLogicalOutputPerCycle() {
        return runtime.spawnMode() == SpawnMode.LINEAR
            ? Integer.MAX_VALUE
            : runtime.maxLogicalOutputPerCycle();
    }

    public int configuredMaxLogicalOutputPerCycle() { return runtime.maxLogicalOutputPerCycle(); }
    public boolean respectNearbyLogicalCap() { return runtime.respectNearbyLogicalCap(); }
    public int vanillaPhysicalOutputCap() { return runtime.vanillaPhysicalOutputCap(); }
    public boolean entityAggregationEnabled() { return runtime.entityAggregationEnabled(); }
    public double entityAggregationRadius() { return runtime.entityAggregationRadius(); }
    public boolean preferExistingEntityStack() { return runtime.preferExistingEntityStack(); }
    public EntityAggregationBackend entityAggregationBackend() { return runtime.entityAggregationBackend(); }
    public boolean showStackAmountOnItems() { return runtime.showStackAmountOnItems(); }
    public boolean hideSingleAmountOnItems() { return runtime.hideSingleAmountOnItems(); }
    public boolean creativeConsumeOnPlace() { return runtime.creativeConsumeOnPlace(); }
    public boolean creativeDropOnBreak() { return runtime.creativeDropOnBreak(); }
    public boolean protectManagedFromExplosions() { return runtime.protectManagedFromExplosions(); }
    public boolean migrationEnabled() { return runtime.migrationEnabled(); }
    public boolean migrationAutoImport() { return runtime.migrationAutoImport(); }
    public boolean warnWildStackerSpawnerStacking() { return runtime.warnWildStackerSpawnerStacking(); }
    public List<String> validationWarnings() { return runtime.validationWarnings(); }

    private static SpawnMode parseSpawnMode(final String raw, final List<String> warnings) {
        try {
            return SpawnMode.valueOf(raw == null ? "BOUNDED_LINEAR" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            warnings.add("Invalid managed.stacking.spawning.mode '" + raw + "'; using BOUNDED_LINEAR.");
            return SpawnMode.BOUNDED_LINEAR;
        }
    }

    private static EntityAggregationBackend parseAggregationBackend(final String raw, final List<String> warnings) {
        try {
            return EntityAggregationBackend.valueOf(raw == null ? "AUTO" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            warnings.add("Invalid managed.stacking.spawning.entity-aggregation.backend '" + raw + "'; using AUTO.");
            return EntityAggregationBackend.AUTO;
        }
    }

    private static int boundedInt(
        final FileConfiguration config,
        final String path,
        final int fallback,
        final int min,
        final int max,
        final List<String> warnings
    ) {
        final int raw = config.getInt(path, fallback);
        if (raw < min || raw > max) {
            warnings.add(path + "=" + raw + " is outside " + min + ".." + max + "; using "
                + Math.max(min, Math.min(max, raw)) + ".");
        }
        return Math.max(min, Math.min(max, raw));
    }

    private static double boundedDouble(
        final FileConfiguration config,
        final String path,
        final double fallback,
        final double min,
        final double max,
        final List<String> warnings
    ) {
        final double raw = config.getDouble(path, fallback);
        if (!Double.isFinite(raw) || raw < min || raw > max) {
            warnings.add(path + " has an invalid value; using a safe bounded value.");
        }
        if (!Double.isFinite(raw)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, raw));
    }

    private static Integer parsePositiveInt(final Object raw) {
        if (raw instanceof Number number) {
            final int value = number.intValue();
            return value > 0 ? value : null;
        }
        if (raw instanceof String string) {
            try {
                final int value = Integer.parseInt(string.trim());
                return value > 0 ? value : null;
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String nonBlank(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Snapshot(
        boolean enabled,
        int maxStackSize,
        boolean autoStackEnabled,
        boolean verticalEnabled,
        int verticalRange,
        boolean nearbyEnabled,
        int nearbyRadius,
        boolean requireSameEntityType,
        boolean requireSameOwner,
        boolean requireSameTier,
        boolean requireSameAccess,
        boolean displayEnabled,
        boolean hideTitle,
        boolean hideSingle,
        String displayFormat,
        boolean showTier,
        String tierFormat,
        double viewDistance,
        double verticalOffset,
        boolean normalBreakAll,
        boolean sneakBreakAll,
        boolean requireOwner,
        boolean withdrawEnabled,
        List<Integer> withdrawPresets,
        boolean scaleWithStack,
        SpawnMode spawnMode,
        int maxLogicalOutputPerCycle,
        boolean respectNearbyLogicalCap,
        int vanillaPhysicalOutputCap,
        boolean entityAggregationEnabled,
        double entityAggregationRadius,
        boolean preferExistingEntityStack,
        EntityAggregationBackend entityAggregationBackend,
        boolean showStackAmountOnItems,
        boolean hideSingleAmountOnItems,
        boolean creativeConsumeOnPlace,
        boolean creativeDropOnBreak,
        boolean protectManagedFromExplosions,
        boolean migrationEnabled,
        boolean migrationAutoImport,
        boolean warnWildStackerSpawnerStacking,
        List<String> validationWarnings
    ) {
        private static Snapshot defaults() {
            return new Snapshot(
                true, DEFAULT_MAX_STACK_SIZE,
                true, true, DEFAULT_VERTICAL_RANGE, true, 1,
                true, true, true, true,
                true, false, true,
                "<yellow>%entity% Spawner</yellow> <gray>x%amount%</gray>", true,
                " <dark_gray>•</dark_gray> <gray>%tier%</gray>", 16.0D, 1.25D,
                false, true, true,
                true, List.of(1, 8, 16),
                true, SpawnMode.BOUNDED_LINEAR, DEFAULT_CYCLE_OUTPUT_CAP, true, DEFAULT_VANILLA_PHYSICAL_CAP,
                true, DEFAULT_ENTITY_AGGREGATION_RADIUS, true, EntityAggregationBackend.AUTO,
                true, true, false, false, true,
                true, true, true, List.of()
            );
        }
    }
}
