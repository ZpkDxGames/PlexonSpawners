package com.plexon.spawners.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

public final class PluginSettings {
    public enum EssenceDelivery {
        INVENTORY,
        GROUND
    }

    public record EssenceRule(int amount, double chance) {}

    private volatile Snapshot runtime = Snapshot.defaults();

    public void reload(final FileConfiguration config) {
        final Set<String> enabledWorlds = new LinkedHashSet<>();
        for (final String world : config.getStringList("breaking.enabled-worlds")) {
            if (!world.isBlank()) enabledWorlds.add(world.toLowerCase(Locale.ROOT));
        }

        final int requiredSilk = clamp(config.getInt("breaking.required-silk-touch-level", 1), 0, 255);
        final int defaultAmount = clamp(config.getInt("essence.default-amount", 1), 1, 4096);
        final double defaultChance = clampChance(config.getDouble("essence.default-chance", 35.0));
        final List<String> warnings = new ArrayList<>();

        EssenceDelivery delivery;
        try {
            delivery = EssenceDelivery.valueOf(config.getString("essence.delivery", "INVENTORY").toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            delivery = EssenceDelivery.INVENTORY;
            warnings.add("Invalid essence.delivery; using INVENTORY.");
        }

        final EnumMap<EntityType, EssenceRule> rules = new EnumMap<>(EntityType.class);
        for (final EntityType type : EntityType.values()) rules.put(type, new EssenceRule(defaultAmount, defaultChance));
        final ConfigurationSection overrides = config.getConfigurationSection("essence.mob-overrides");
        if (overrides != null) {
            for (final String key : overrides.getKeys(false)) {
                final EntityType type = parseEntityType(key);
                if (type == null) {
                    warnings.add("Unknown essence.mob-overrides entity type: " + key);
                    continue;
                }
                final ConfigurationSection mob = overrides.getConfigurationSection(key);
                if (mob != null) {
                    rules.put(type, new EssenceRule(
                        clamp(mob.getInt("amount", defaultAmount), 1, 4096),
                        clampChance(mob.getDouble("chance", defaultChance))));
                } else if (overrides.isInt(key)) {
                    rules.put(type, new EssenceRule(clamp(overrides.getInt(key), 1, 4096), defaultChance));
                }
            }
        }

        final LinkedHashSet<Integer> presetSet = new LinkedHashSet<>();
        for (final int preset : config.getIntegerList("gui.withdraw-presets")) {
            if (preset > 0) presetSet.add(Math.min(4096, preset));
        }
        final List<Integer> presets = presetSet.isEmpty() ? List.of(1, 8, 16, 32, 64) : List.copyOf(presetSet);

        runtime = new Snapshot(
            config.getBoolean("breaking.enabled", true),
            requiredSilk,
            config.getBoolean("breaking.allow-silk-bypass-permission", false),
            config.getBoolean("breaking.creative.recover-spawner", false),
            config.getBoolean("breaking.creative.award-essence", false),
            Collections.unmodifiableSet(enabledWorlds),
            config.getBoolean("essence.enabled", true),
            defaultAmount,
            defaultChance,
            delivery,
            Collections.unmodifiableMap(rules),
            config.getBoolean("gui.enabled", true),
            config.getBoolean("gui.open-on-right-click", true),
            config.getString("gui.title", "<gradient:#56B9F2:#92E1FF><b>Spawner Withdrawal</b></gradient>"),
            presets,
            config.getBoolean("messages.silk-recovered", true),
            config.getBoolean("messages.essence-awarded", true),
            config.getBoolean("messages.withdraw-success", true),
            config.getBoolean("messages.withdraw-failed", true),
            List.copyOf(warnings));
    }

    public boolean isWorldEnabled(final World world) {
        final Set<String> worlds = runtime.enabledWorlds();
        return worlds.isEmpty() || worlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public EssenceRule essenceRule(final EntityType type) {
        return runtime.essenceRules().getOrDefault(type,
            new EssenceRule(runtime.defaultEssenceAmount(), runtime.defaultEssenceChance()));
    }

    public boolean breakingEnabled() { return runtime.breakingEnabled(); }
    public int requiredSilkTouchLevel() { return runtime.requiredSilkTouchLevel(); }
    public boolean silkBypassPermissionEnabled() { return runtime.silkBypassPermissionEnabled(); }
    public boolean creativeRecoverSpawner() { return runtime.creativeRecoverSpawner(); }
    public boolean creativeAwardEssence() { return runtime.creativeAwardEssence(); }
    public boolean essenceEnabled() { return runtime.essenceEnabled(); }
    public EssenceDelivery essenceDelivery() { return runtime.essenceDelivery(); }
    public boolean guiEnabled() { return runtime.guiEnabled(); }
    public boolean guiOpenOnRightClick() { return runtime.guiOpenOnRightClick(); }
    public String guiTitle() { return runtime.guiTitle(); }
    public List<Integer> withdrawPresets() { return runtime.withdrawPresets(); }
    public boolean silkRecoveredMessage() { return runtime.silkRecoveredMessage(); }
    public boolean essenceAwardedMessage() { return runtime.essenceAwardedMessage(); }
    public boolean withdrawSuccessMessage() { return runtime.withdrawSuccessMessage(); }
    public boolean withdrawFailedMessage() { return runtime.withdrawFailedMessage(); }
    public List<String> validationWarnings() { return runtime.validationWarnings(); }

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
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    private record Snapshot(
        boolean breakingEnabled,
        int requiredSilkTouchLevel,
        boolean silkBypassPermissionEnabled,
        boolean creativeRecoverSpawner,
        boolean creativeAwardEssence,
        Set<String> enabledWorlds,
        boolean essenceEnabled,
        int defaultEssenceAmount,
        double defaultEssenceChance,
        EssenceDelivery essenceDelivery,
        Map<EntityType, EssenceRule> essenceRules,
        boolean guiEnabled,
        boolean guiOpenOnRightClick,
        String guiTitle,
        List<Integer> withdrawPresets,
        boolean silkRecoveredMessage,
        boolean essenceAwardedMessage,
        boolean withdrawSuccessMessage,
        boolean withdrawFailedMessage,
        List<String> validationWarnings
    ) {
        private static Snapshot defaults() {
            return new Snapshot(true, 1, false, false, false, Set.of(), true, 1, 35.0D,
                EssenceDelivery.INVENTORY, Map.of(), true, true,
                "<gradient:#56B9F2:#92E1FF><b>Spawner Withdrawal</b></gradient>",
                List.of(1, 8, 16, 32, 64), true, true, true, true, List.of());
        }
    }
}
