package com.plexon.spawners.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

public final class PluginSettings {
    public enum EssenceDelivery {
        GROUND,
        INVENTORY
    }

    public record EssenceRule(int amount, double chance) {}

    private volatile Snapshot runtime = Snapshot.defaults();

    public void reload(final FileConfiguration config) {
        final boolean breakingEnabled = config.getBoolean("breaking.enabled", true);
        final boolean takeOwnership = config.getBoolean("breaking.take-ownership", true);
        final int requiredSilkTouchLevel = clamp(config.getInt("breaking.required-silk-touch-level", 3), 0, 255);
        final boolean silkBypassPermissionEnabled = config.getBoolean("breaking.allow-silk-bypass-permission", false);
        final boolean dropSpawnerWhenQualified = config.getBoolean("breaking.drop-spawner-when-qualified", true);
        final boolean dropExperience = config.getBoolean("breaking.drop-experience", false);
        final boolean creativeDrops = config.getBoolean("breaking.creative-drops", false);

        final Set<String> worldNames = new HashSet<>();
        final Set<UUID> worldIds = new HashSet<>();
        for (final String configuredWorld : config.getStringList("breaking.enabled-worlds")) {
            if (configuredWorld.isBlank()) {
                continue;
            }
            worldNames.add(configuredWorld.toLowerCase(Locale.ROOT));
            final World loadedWorld = Bukkit.getWorld(configuredWorld);
            if (loadedWorld != null) {
                worldIds.add(loadedWorld.getUID());
            }
        }

        final List<String> warnings = new ArrayList<>();
        final boolean essenceEnabled = config.getBoolean("essence.enabled", true);
        final int defaultEssenceAmount = clamp(config.getInt("essence.default-amount", 1), 1, 4096);
        final double defaultEssenceChance = clampChance(config.getDouble("essence.default-chance", 35.0));
        final EssenceDelivery essenceDelivery = parseDelivery(config.getString("essence.delivery", "GROUND"), warnings);

        final EnumMap<EntityType, Integer> amountOverrides = new EnumMap<>(EntityType.class);
        final EnumMap<EntityType, Double> chanceOverrides = new EnumMap<>(EntityType.class);
        final ConfigurationSection section = config.getConfigurationSection("essence.mob-overrides");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final EntityType type = parseEntityType(key);
                if (type == null) {
                    warnings.add("Unknown essence.mob-overrides entity type: " + key);
                    continue;
                }

                final ConfigurationSection mobSection = section.getConfigurationSection(key);
                if (mobSection != null) {
                    if (mobSection.contains("amount")) {
                        amountOverrides.put(type, clamp(mobSection.getInt("amount", defaultEssenceAmount), 1, 4096));
                    }
                    if (mobSection.contains("chance")) {
                        chanceOverrides.put(type, clampChance(mobSection.getDouble("chance", defaultEssenceChance)));
                    }
                    continue;
                }

                // 1.x compatibility: `BLAZE: 3` is treated as an amount-only override.
                if (section.isInt(key)) {
                    amountOverrides.put(type, clamp(section.getInt(key, defaultEssenceAmount), 1, 4096));
                }
            }
        }

        final EnumMap<EntityType, EssenceRule> essenceRules = new EnumMap<>(EntityType.class);
        int maximumEssenceAmount = defaultEssenceAmount;
        for (final EntityType type : EntityType.values()) {
            final int amount = amountOverrides.getOrDefault(type, defaultEssenceAmount);
            final double chance = chanceOverrides.getOrDefault(type, defaultEssenceChance);
            essenceRules.put(type, new EssenceRule(amount, chance));
            maximumEssenceAmount = Math.max(maximumEssenceAmount, amount);
        }

        if (essenceEnabled && essenceDelivery == EssenceDelivery.GROUND) {
            final int physicalStacks = (maximumEssenceAmount + 63) / 64;
            if (physicalStacks >= 16) {
                warnings.add(
                    "Ground Essence configuration can create up to " + physicalStacks
                        + " item entities from one break (logical amount " + maximumEssenceAmount + ")."
                );
            }
        }

        runtime = new Snapshot(
            breakingEnabled,
            takeOwnership,
            requiredSilkTouchLevel,
            silkBypassPermissionEnabled,
            dropSpawnerWhenQualified,
            dropExperience,
            creativeDrops,
            Collections.unmodifiableSet(worldNames),
            Collections.unmodifiableSet(worldIds),
            essenceEnabled,
            defaultEssenceAmount,
            defaultEssenceChance,
            essenceDelivery,
            Collections.unmodifiableMap(amountOverrides),
            Collections.unmodifiableMap(chanceOverrides),
            Collections.unmodifiableMap(essenceRules),
            config.getBoolean("messages.break-success-enabled", false),
            config.getBoolean("messages.break-failed-enabled", false),
            List.copyOf(warnings)
        );
    }

    public boolean isWorldEnabled(final World world) {
        final Snapshot snapshot = runtime;
        if (snapshot.enabledWorldNames().isEmpty()) {
            return true;
        }
        if (snapshot.enabledWorldIds().contains(world.getUID())) {
            return true;
        }
        return snapshot.enabledWorldNames().contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public boolean isWorldEnabled(final String worldName) {
        final Snapshot snapshot = runtime;
        return snapshot.enabledWorldNames().isEmpty()
            || snapshot.enabledWorldNames().contains(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean shouldHandleCreative(final GameMode gameMode) {
        return gameMode != GameMode.CREATIVE || runtime.creativeDrops();
    }

    public EssenceRule essenceRule(final EntityType type) {
        final EssenceRule rule = runtime.essenceRules().get(type);
        return rule == null
            ? new EssenceRule(runtime.defaultEssenceAmount(), runtime.defaultEssenceChance())
            : rule;
    }

    public int essenceAmount(final EntityType type) {
        return essenceRule(type).amount();
    }

    public double essenceChance(final EntityType type) {
        return essenceRule(type).chance();
    }

    public boolean hasEssenceOverride(final EntityType type) {
        final Snapshot snapshot = runtime;
        return snapshot.essenceAmountOverrides().containsKey(type)
            || snapshot.essenceChanceOverrides().containsKey(type);
    }

    public int enabledWorldCount() {
        return runtime.enabledWorldNames().size();
    }

    public int essenceOverrideCount() {
        final Set<EntityType> combined = new HashSet<>(runtime.essenceAmountOverrides().keySet());
        combined.addAll(runtime.essenceChanceOverrides().keySet());
        return combined.size();
    }

    public List<String> validationWarnings() {
        return runtime.validationWarnings();
    }

    public boolean breakingEnabled() {
        return runtime.breakingEnabled();
    }

    public boolean takeOwnership() {
        return runtime.takeOwnership();
    }

    public int requiredSilkTouchLevel() {
        return runtime.requiredSilkTouchLevel();
    }

    public boolean silkBypassPermissionEnabled() {
        return runtime.silkBypassPermissionEnabled();
    }

    public boolean dropSpawnerWhenQualified() {
        return runtime.dropSpawnerWhenQualified();
    }

    public boolean dropExperience() {
        return runtime.dropExperience();
    }

    public boolean creativeDrops() {
        return runtime.creativeDrops();
    }

    public boolean essenceEnabled() {
        return runtime.essenceEnabled();
    }

    public int defaultEssenceAmount() {
        return runtime.defaultEssenceAmount();
    }

    public double defaultEssenceChance() {
        return runtime.defaultEssenceChance();
    }

    public EssenceDelivery essenceDelivery() {
        return runtime.essenceDelivery();
    }

    public boolean breakSuccessMessages() {
        return runtime.breakSuccessMessages();
    }

    public boolean breakFailedMessages() {
        return runtime.breakFailedMessages();
    }

    private static EssenceDelivery parseDelivery(final String input, final List<String> warnings) {
        try {
            return EssenceDelivery.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            warnings.add("Invalid essence.delivery '" + input + "'; using GROUND.");
            return EssenceDelivery.GROUND;
        }
    }

    public static EntityType parseEntityType(final String input) {
        try {
            return EntityType.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampChance(final double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }

    private record Snapshot(
        boolean breakingEnabled,
        boolean takeOwnership,
        int requiredSilkTouchLevel,
        boolean silkBypassPermissionEnabled,
        boolean dropSpawnerWhenQualified,
        boolean dropExperience,
        boolean creativeDrops,
        Set<String> enabledWorldNames,
        Set<UUID> enabledWorldIds,
        boolean essenceEnabled,
        int defaultEssenceAmount,
        double defaultEssenceChance,
        EssenceDelivery essenceDelivery,
        Map<EntityType, Integer> essenceAmountOverrides,
        Map<EntityType, Double> essenceChanceOverrides,
        Map<EntityType, EssenceRule> essenceRules,
        boolean breakSuccessMessages,
        boolean breakFailedMessages,
        List<String> validationWarnings
    ) {
        private static Snapshot defaults() {
            final EnumMap<EntityType, EssenceRule> defaultRules = new EnumMap<>(EntityType.class);
            for (final EntityType type : EntityType.values()) {
                defaultRules.put(type, new EssenceRule(1, 35.0));
            }
            return new Snapshot(
                true,
                true,
                3,
                false,
                true,
                false,
                false,
                Set.of(),
                Set.of(),
                true,
                1,
                35.0,
                EssenceDelivery.GROUND,
                Map.of(),
                Map.of(),
                Collections.unmodifiableMap(defaultRules),
                false,
                false,
                List.of()
            );
        }
    }
}
