package com.plexon.spawners.config;

import com.plexon.spawners.breaking.NonSilkRewardMode;
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
    public enum RewardDelivery {
        INVENTORY,
        GROUND;

        public static RewardDelivery parse(final String value, final RewardDelivery fallback) {
            if (value == null || value.isBlank()) return fallback;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException exception) {
                return fallback;
            }
        }
    }

    public record RewardRule(int amount, double chance) {}

    private volatile Snapshot runtime = Snapshot.defaults();

    public void reload(final FileConfiguration config) {
        final List<String> warnings = new ArrayList<>();
        final Set<String> enabledWorlds = new LinkedHashSet<>();
        for (final String world : config.getStringList("breaking.enabled-worlds")) {
            if (!world.isBlank()) enabledWorlds.add(world.toLowerCase(Locale.ROOT));
        }

        final int requiredSilk = clamp(config.getInt("breaking.required-silk-touch-level", 1), 0, 255);
        final NonSilkRewardMode defaultMode = parseMode(
            config.getString("breaking.non-silk-reward-mode", "ESSENCE"), NonSilkRewardMode.ESSENCE, warnings,
            "breaking.non-silk-reward-mode");

        final int defaultEssenceAmount = clamp(config.getInt("essence.default-amount", 1), 1, 4096);
        final double defaultEssenceChance = clampChance(config.getDouble("essence.default-chance", 35.0));
        final RewardDelivery essenceDelivery = parseDelivery(config.getString("essence.delivery", "INVENTORY"), warnings,
            "essence.delivery");
        final EnumMap<EntityType, RewardRule> essenceRules = rules(
            config.getConfigurationSection("essence.mob-overrides"), defaultEssenceAmount, defaultEssenceChance, warnings,
            "essence.mob-overrides");

        final int defaultCustomAmount = clamp(config.getInt("custom-drop.default-amount", 1), 1, 4096);
        final double defaultCustomChance = clampChance(config.getDouble("custom-drop.default-chance", 15.0));
        final RewardDelivery customDelivery = parseDelivery(config.getString("custom-drop.delivery", "INVENTORY"), warnings,
            "custom-drop.delivery");
        final EnumMap<EntityType, RewardRule> customRules = rules(
            config.getConfigurationSection("custom-drop.mob-overrides"), defaultCustomAmount, defaultCustomChance, warnings,
            "custom-drop.mob-overrides");

        final EnumMap<EntityType, NonSilkRewardMode> mobModes = new EnumMap<>(EntityType.class);
        final ConfigurationSection rewardOverrides = config.getConfigurationSection("rewards.mob-overrides");
        if (rewardOverrides != null) {
            for (final String key : rewardOverrides.getKeys(false)) {
                final EntityType type = parseEntityType(key);
                if (type == null) {
                    warnings.add("Unknown rewards.mob-overrides entity type: " + key);
                    continue;
                }
                final String raw = rewardOverrides.getString(key + ".mode");
                if (raw != null) {
                    mobModes.put(type, parseMode(raw, defaultMode, warnings, "rewards.mob-overrides." + key + ".mode"));
                }
            }
        }

        final LinkedHashSet<Integer> presetSet = new LinkedHashSet<>();
        for (final int preset : config.getIntegerList("gui.withdraw-presets")) {
            if (preset > 0) presetSet.add(Math.min(4096, preset));
        }
        final List<Integer> presets = presetSet.isEmpty() ? List.of(1, 8, 16, 32, 64) : List.copyOf(presetSet);

        runtime = new Snapshot(
            config.getInt("config-version", ConfigBootstrap.CONFIG_VERSION),
            config.getBoolean("breaking.enabled", true),
            requiredSilk,
            config.getBoolean("breaking.allow-silk-bypass-permission", false),
            defaultMode,
            Collections.unmodifiableMap(mobModes),
            config.getBoolean("breaking.creative.recover-spawner", false),
            config.getBoolean("breaking.creative.award-essence", false),
            config.getBoolean("breaking.creative.award-custom-item", false),
            Collections.unmodifiableSet(enabledWorlds),
            config.getBoolean("essence.enabled", true),
            defaultEssenceAmount,
            defaultEssenceChance,
            essenceDelivery,
            Collections.unmodifiableMap(essenceRules),
            config.getBoolean("custom-drop.enabled", false),
            defaultCustomAmount,
            defaultCustomChance,
            customDelivery,
            Collections.unmodifiableMap(customRules),
            config.getBoolean("gui.enabled", true),
            config.getBoolean("gui.open-on-right-click", true),
            config.getString("gui.title", "<gradient:#56B9F2:#92E1FF><b>Spawner Withdrawal</b></gradient>"),
            presets,
            config.getBoolean("admin-gui.enabled", true),
            config.getString("admin-gui.title", "<gradient:#56B9F2:#92E1FF><b>PlexonSpawners Admin</b></gradient>"),
            clamp(config.getInt("admin-gui.config-backups-to-keep", 10), 1, 50),
            config.getBoolean("messages.silk-recovered", true),
            config.getBoolean("messages.essence-awarded", true),
            config.getBoolean("messages.custom-drop-awarded", true),
            config.getBoolean("messages.withdraw-success", true),
            config.getBoolean("messages.withdraw-failed", true),
            List.copyOf(warnings));
    }

    private static EnumMap<EntityType, RewardRule> rules(
        final ConfigurationSection overrides,
        final int defaultAmount,
        final double defaultChance,
        final List<String> warnings,
        final String path
    ) {
        final EnumMap<EntityType, RewardRule> rules = new EnumMap<>(EntityType.class);
        for (final EntityType type : EntityType.values()) rules.put(type, new RewardRule(defaultAmount, defaultChance));
        if (overrides == null) return rules;
        for (final String key : overrides.getKeys(false)) {
            final EntityType type = parseEntityType(key);
            if (type == null) {
                warnings.add("Unknown " + path + " entity type: " + key);
                continue;
            }
            final ConfigurationSection mob = overrides.getConfigurationSection(key);
            if (mob != null) {
                rules.put(type, new RewardRule(
                    clamp(mob.getInt("amount", defaultAmount), 1, 4096),
                    clampChance(mob.getDouble("chance", defaultChance))));
            } else if (overrides.isInt(key)) {
                rules.put(type, new RewardRule(clamp(overrides.getInt(key), 1, 4096), defaultChance));
            }
        }
        return rules;
    }

    private static NonSilkRewardMode parseMode(
        final String raw,
        final NonSilkRewardMode fallback,
        final List<String> warnings,
        final String path
    ) {
        final NonSilkRewardMode parsed = NonSilkRewardMode.parse(raw, null);
        if (parsed != null) return parsed;
        warnings.add("Invalid " + path + "; using " + fallback + ".");
        return fallback;
    }

    private static RewardDelivery parseDelivery(final String raw, final List<String> warnings, final String path) {
        final RewardDelivery parsed = RewardDelivery.parse(raw, null);
        if (parsed != null) return parsed;
        warnings.add("Invalid " + path + "; using INVENTORY.");
        return RewardDelivery.INVENTORY;
    }

    public boolean isWorldEnabled(final World world) {
        final Set<String> worlds = runtime.enabledWorlds();
        return worlds.isEmpty() || worlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public RewardRule essenceRule(final EntityType type) {
        return runtime.essenceRules().getOrDefault(type,
            new RewardRule(runtime.defaultEssenceAmount(), runtime.defaultEssenceChance()));
    }

    public RewardRule customDropRule(final EntityType type) {
        return runtime.customDropRules().getOrDefault(type,
            new RewardRule(runtime.defaultCustomDropAmount(), runtime.defaultCustomDropChance()));
    }

    public NonSilkRewardMode nonSilkRewardMode(final EntityType type) {
        return runtime.mobRewardModes().getOrDefault(type, runtime.defaultNonSilkRewardMode());
    }

    public int configSchema() { return runtime.configSchema(); }
    public boolean breakingEnabled() { return runtime.breakingEnabled(); }
    public int requiredSilkTouchLevel() { return runtime.requiredSilkTouchLevel(); }
    public boolean silkBypassPermissionEnabled() { return runtime.silkBypassPermissionEnabled(); }
    public NonSilkRewardMode defaultNonSilkRewardMode() { return runtime.defaultNonSilkRewardMode(); }
    public boolean creativeRecoverSpawner() { return runtime.creativeRecoverSpawner(); }
    public boolean creativeAwardEssence() { return runtime.creativeAwardEssence(); }
    public boolean creativeAwardCustomItem() { return runtime.creativeAwardCustomItem(); }
    public Set<String> enabledWorlds() { return runtime.enabledWorlds(); }
    public boolean essenceEnabled() { return runtime.essenceEnabled(); }
    public int defaultEssenceAmount() { return runtime.defaultEssenceAmount(); }
    public double defaultEssenceChance() { return runtime.defaultEssenceChance(); }
    public RewardDelivery essenceDelivery() { return runtime.essenceDelivery(); }
    public boolean customDropEnabled() { return runtime.customDropEnabled(); }
    public int defaultCustomDropAmount() { return runtime.defaultCustomDropAmount(); }
    public double defaultCustomDropChance() { return runtime.defaultCustomDropChance(); }
    public RewardDelivery customDropDelivery() { return runtime.customDropDelivery(); }
    public boolean guiEnabled() { return runtime.guiEnabled(); }
    public boolean guiOpenOnRightClick() { return runtime.guiOpenOnRightClick(); }
    public String guiTitle() { return runtime.guiTitle(); }
    public List<Integer> withdrawPresets() { return runtime.withdrawPresets(); }
    public boolean adminGuiEnabled() { return runtime.adminGuiEnabled(); }
    public String adminGuiTitle() { return runtime.adminGuiTitle(); }
    public int configBackupsToKeep() { return runtime.configBackupsToKeep(); }
    public boolean silkRecoveredMessage() { return runtime.silkRecoveredMessage(); }
    public boolean essenceAwardedMessage() { return runtime.essenceAwardedMessage(); }
    public boolean customDropAwardedMessage() { return runtime.customDropAwardedMessage(); }
    public boolean withdrawSuccessMessage() { return runtime.withdrawSuccessMessage(); }
    public boolean withdrawFailedMessage() { return runtime.withdrawFailedMessage(); }
    public List<String> validationWarnings() { return runtime.validationWarnings(); }

    public static EntityType parseEntityType(final String input) {
        if (input == null) return null;
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
        int configSchema,
        boolean breakingEnabled,
        int requiredSilkTouchLevel,
        boolean silkBypassPermissionEnabled,
        NonSilkRewardMode defaultNonSilkRewardMode,
        Map<EntityType, NonSilkRewardMode> mobRewardModes,
        boolean creativeRecoverSpawner,
        boolean creativeAwardEssence,
        boolean creativeAwardCustomItem,
        Set<String> enabledWorlds,
        boolean essenceEnabled,
        int defaultEssenceAmount,
        double defaultEssenceChance,
        RewardDelivery essenceDelivery,
        Map<EntityType, RewardRule> essenceRules,
        boolean customDropEnabled,
        int defaultCustomDropAmount,
        double defaultCustomDropChance,
        RewardDelivery customDropDelivery,
        Map<EntityType, RewardRule> customDropRules,
        boolean guiEnabled,
        boolean guiOpenOnRightClick,
        String guiTitle,
        List<Integer> withdrawPresets,
        boolean adminGuiEnabled,
        String adminGuiTitle,
        int configBackupsToKeep,
        boolean silkRecoveredMessage,
        boolean essenceAwardedMessage,
        boolean customDropAwardedMessage,
        boolean withdrawSuccessMessage,
        boolean withdrawFailedMessage,
        List<String> validationWarnings
    ) {
        private static Snapshot defaults() {
            return new Snapshot(11, true, 1, false, NonSilkRewardMode.ESSENCE, Map.of(), false, false, false,
                Set.of(), true, 1, 35.0D, RewardDelivery.INVENTORY, Map.of(), false, 1, 15.0D,
                RewardDelivery.INVENTORY, Map.of(), true, true,
                "<gradient:#56B9F2:#92E1FF><b>Spawner Withdrawal</b></gradient>", List.of(1, 8, 16, 32, 64),
                true, "<gradient:#56B9F2:#92E1FF><b>PlexonSpawners Admin</b></gradient>", 10,
                true, true, true, true, true, List.of());
        }
    }
}
