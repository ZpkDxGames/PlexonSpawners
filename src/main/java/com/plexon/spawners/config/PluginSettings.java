package com.plexon.spawners.config;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PluginSettings {
    public enum EssenceDelivery {
        GROUND,
        INVENTORY
    }

    private boolean breakingEnabled;
    private boolean takeOwnership;
    private int requiredSilkTouchLevel;
    private boolean silkBypassPermissionEnabled;
    private boolean dropSpawnerWhenQualified;
    private boolean dropExperience;
    private boolean creativeDrops;
    private Set<String> enabledWorlds = Set.of();

    private boolean essenceEnabled;
    private int defaultEssenceAmount;
    private double defaultEssenceChance;
    private EssenceDelivery essenceDelivery;
    private Map<EntityType, Integer> essenceAmountOverrides = Map.of();
    private Map<EntityType, Double> essenceChanceOverrides = Map.of();

    private boolean breakSuccessMessages;
    private boolean breakFailedMessages;

    public void reload(final FileConfiguration config) {
        breakingEnabled = config.getBoolean("breaking.enabled", true);
        takeOwnership = config.getBoolean("breaking.take-ownership", true);
        requiredSilkTouchLevel = clamp(config.getInt("breaking.required-silk-touch-level", 3), 0, 255);
        silkBypassPermissionEnabled = config.getBoolean("breaking.allow-silk-bypass-permission", false);
        dropSpawnerWhenQualified = config.getBoolean("breaking.drop-spawner-when-qualified", true);
        dropExperience = config.getBoolean("breaking.drop-experience", false);
        creativeDrops = config.getBoolean("breaking.creative-drops", false);

        final Set<String> worlds = new HashSet<>();
        for (final String world : config.getStringList("breaking.enabled-worlds")) {
            if (!world.isBlank()) {
                worlds.add(world.toLowerCase(Locale.ROOT));
            }
        }
        enabledWorlds = Collections.unmodifiableSet(worlds);

        essenceEnabled = config.getBoolean("essence.enabled", true);
        defaultEssenceAmount = clamp(config.getInt("essence.default-amount", 1), 1, 4096);
        defaultEssenceChance = clampChance(config.getDouble("essence.default-chance", 35.0));
        essenceDelivery = parseDelivery(config.getString("essence.delivery", "GROUND"));

        final EnumMap<EntityType, Integer> amountOverrides = new EnumMap<>(EntityType.class);
        final EnumMap<EntityType, Double> chanceOverrides = new EnumMap<>(EntityType.class);
        final ConfigurationSection section = config.getConfigurationSection("essence.mob-overrides");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final EntityType type = parseEntityType(key);
                if (type == null) {
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
        essenceAmountOverrides = Collections.unmodifiableMap(amountOverrides);
        essenceChanceOverrides = Collections.unmodifiableMap(chanceOverrides);

        breakSuccessMessages = config.getBoolean("messages.break-success-enabled", false);
        breakFailedMessages = config.getBoolean("messages.break-failed-enabled", false);
    }

    public boolean isWorldEnabled(final String worldName) {
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean shouldHandleCreative(final GameMode gameMode) {
        return gameMode != GameMode.CREATIVE || creativeDrops;
    }

    public int essenceAmount(final EntityType type) {
        return essenceAmountOverrides.getOrDefault(type, defaultEssenceAmount);
    }

    public double essenceChance(final EntityType type) {
        return essenceChanceOverrides.getOrDefault(type, defaultEssenceChance);
    }

    public boolean hasEssenceOverride(final EntityType type) {
        return essenceAmountOverrides.containsKey(type) || essenceChanceOverrides.containsKey(type);
    }

    public boolean breakingEnabled() {
        return breakingEnabled;
    }

    public boolean takeOwnership() {
        return takeOwnership;
    }

    public int requiredSilkTouchLevel() {
        return requiredSilkTouchLevel;
    }

    public boolean silkBypassPermissionEnabled() {
        return silkBypassPermissionEnabled;
    }

    public boolean dropSpawnerWhenQualified() {
        return dropSpawnerWhenQualified;
    }

    public boolean dropExperience() {
        return dropExperience;
    }

    public boolean creativeDrops() {
        return creativeDrops;
    }

    public boolean essenceEnabled() {
        return essenceEnabled;
    }

    public int defaultEssenceAmount() {
        return defaultEssenceAmount;
    }

    public double defaultEssenceChance() {
        return defaultEssenceChance;
    }

    public EssenceDelivery essenceDelivery() {
        return essenceDelivery;
    }

    public boolean breakSuccessMessages() {
        return breakSuccessMessages;
    }

    public boolean breakFailedMessages() {
        return breakFailedMessages;
    }

    private static EssenceDelivery parseDelivery(final String input) {
        try {
            return EssenceDelivery.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
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
}
