package com.plexon.spawners.config;

import com.plexon.spawners.breaking.NonSilkRewardMode;
import com.plexon.spawners.reward.RewardItemFactory;
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

/** Immutable-generation configuration facade. */
public final class PluginSettings {
    public enum RewardDelivery {
        INVENTORY,
        GROUND;

        public static RewardDelivery parse(final String value) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Reward delivery is blank");
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid reward delivery: " + value, exception);
            }
        }
    }

    public enum ScopeMode {
        ALL,
        ALLOWLIST;

        public static ScopeMode parse(final String value) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("scope.mode is blank");
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid scope.mode: " + value, exception);
            }
        }
    }

    public record RewardRule(int amount, double chance) {}

    public record Snapshot(
        int configSchema,
        boolean breakingEnabled,
        int requiredSilkTouchLevel,
        boolean silkBypassPermissionEnabled,
        NonSilkRewardMode defaultNonSilkRewardMode,
        Map<EntityType, NonSilkRewardMode> mobRewardModes,
        boolean creativeRecoverSpawner,
        boolean creativeAwardEssence,
        boolean creativeAwardCustomItem,
        ScopeMode scopeMode,
        Set<String> scopeWorlds,
        boolean essenceEnabled,
        int defaultEssenceAmount,
        double defaultEssenceChance,
        RewardDelivery essenceDelivery,
        Map<EntityType, RewardRule> essenceRules,
        RewardItemFactory.ItemDefinition essenceItem,
        boolean customDropEnabled,
        int defaultCustomDropAmount,
        double defaultCustomDropChance,
        RewardDelivery customDropDelivery,
        Map<EntityType, RewardRule> customDropRules,
        RewardItemFactory.ItemDefinition customItem,
        boolean adminGuiEnabled,
        String adminGuiTitle,
        int configBackupsToKeep,
        boolean silkRecoveredMessage,
        boolean essenceAwardedMessage,
        boolean customDropAwardedMessage,
        List<String> validationWarnings
    ) {
        public Snapshot {
            mobRewardModes = Collections.unmodifiableMap(new EnumMap<>(mobRewardModes));
            essenceRules = Collections.unmodifiableMap(new EnumMap<>(essenceRules));
            customDropRules = Collections.unmodifiableMap(new EnumMap<>(customDropRules));
            scopeWorlds = Collections.unmodifiableSet(new LinkedHashSet<>(scopeWorlds));
            validationWarnings = List.copyOf(validationWarnings);
        }
    }

    private volatile Snapshot runtime;

    public Snapshot prepare(final FileConfiguration config) {
        final int schema = config.getInt("config-version", -1);
        if (schema != ConfigBootstrap.CONFIG_VERSION) {
            throw new IllegalArgumentException("Expected config-version " + ConfigBootstrap.CONFIG_VERSION + ", got " + schema);
        }

        final int requiredSilk = bounded(config.getInt("breaking.required-silk-touch-level", 1), 0, 255,
            "breaking.required-silk-touch-level");
        final NonSilkRewardMode defaultMode = parseMode(config.getString("breaking.non-silk-reward-mode"),
            "breaking.non-silk-reward-mode");
        final ScopeMode scopeMode = ScopeMode.parse(config.getString("scope.mode", "ALLOWLIST"));
        final LinkedHashSet<String> scopeWorlds = new LinkedHashSet<>();
        for (final String world : config.getStringList("scope.worlds")) {
            final String normalized = normalizeScopeToken(world);
            if (!normalized.isBlank()) scopeWorlds.add(normalized);
        }
        if (scopeMode == ScopeMode.ALLOWLIST && scopeWorlds.isEmpty()) {
            throw new IllegalArgumentException("scope.worlds cannot be empty when scope.mode=ALLOWLIST");
        }

        final int essenceAmount = bounded(config.getInt("essence.default-amount", 1), 1, 4096,
            "essence.default-amount");
        final double essenceChance = chance(config.getDouble("essence.default-chance", 35.0D),
            "essence.default-chance");
        final RewardDelivery essenceDelivery = RewardDelivery.parse(config.getString("essence.delivery", "INVENTORY"));

        final int customAmount = bounded(config.getInt("custom-drop.default-amount", 1), 1, 4096,
            "custom-drop.default-amount");
        final double customChance = chance(config.getDouble("custom-drop.default-chance", 15.0D),
            "custom-drop.default-chance");
        final RewardDelivery customDelivery = RewardDelivery.parse(config.getString("custom-drop.delivery", "INVENTORY"));

        final EnumMap<EntityType, RewardRule> essenceRules = rules(
            config.getConfigurationSection("essence.mob-overrides"), essenceAmount, essenceChance, "essence.mob-overrides");
        final EnumMap<EntityType, RewardRule> customRules = rules(
            config.getConfigurationSection("custom-drop.mob-overrides"), customAmount, customChance, "custom-drop.mob-overrides");
        final EnumMap<EntityType, NonSilkRewardMode> rewardModes = modes(
            config.getConfigurationSection("rewards.mob-overrides"));

        final RewardItemFactory items = new RewardItemFactory();
        final RewardItemFactory.ItemDefinition essenceItem = items.read(config, "essence.item",
            org.bukkit.Material.AMETHYST_SHARD,
            "<gradient:#56B9F2:#92E1FF><b>Spawner Essence</b></gradient>",
            List.of("<gray>A concentrated fragment of spawner energy.</gray>"), true);
        final RewardItemFactory.ItemDefinition customItem = items.read(config, "custom-drop.item",
            org.bukkit.Material.PRISMARINE_CRYSTALS,
            "<gradient:#7BE7FF:#4AA8FF><b>Spawner Fragment</b></gradient>",
            List.of("<gray>Dropped when a spawner is broken without Silk Touch.</gray>"), true);
        if (essenceItem == null || !essenceItem.valid()) throw new IllegalArgumentException("Invalid Essence item template");
        if (customItem == null || !customItem.valid()) throw new IllegalArgumentException("Invalid custom item template");

        final String adminTitle = config.getString("admin-gui.title",
            "<gradient:#56B9F2:#92E1FF><b>PlexonSpawners Admin</b></gradient>");
        validateMiniMessage(adminTitle, "admin-gui.title");

        final List<String> warnings = new ArrayList<>();
        if (scopeMode == ScopeMode.ALL) {
            warnings.add("Scope is explicitly ALL; PlexonCraft production should normally use an explicit Survival allowlist.");
        }

        return new Snapshot(
            schema,
            config.getBoolean("breaking.enabled", true),
            requiredSilk,
            config.getBoolean("breaking.allow-silk-bypass-permission", false),
            defaultMode,
            rewardModes,
            config.getBoolean("breaking.creative.recover-spawner", true),
            config.getBoolean("breaking.creative.award-essence", true),
            config.getBoolean("breaking.creative.award-custom-item", false),
            scopeMode,
            scopeWorlds,
            config.getBoolean("essence.enabled", true),
            essenceAmount,
            essenceChance,
            essenceDelivery,
            essenceRules,
            essenceItem,
            config.getBoolean("custom-drop.enabled", false),
            customAmount,
            customChance,
            customDelivery,
            customRules,
            customItem,
            config.getBoolean("admin-gui.enabled", true),
            adminTitle,
            bounded(config.getInt("admin-gui.config-backups-to-keep", 10), 1, 50,
                "admin-gui.config-backups-to-keep"),
            config.getBoolean("messages.silk-recovered", true),
            config.getBoolean("messages.essence-awarded", true),
            config.getBoolean("messages.custom-drop-awarded", true),
            warnings);
    }

    public void commit(final Snapshot prepared) {
        runtime = java.util.Objects.requireNonNull(prepared, "prepared");
    }

    public Snapshot snapshot() { return runtime; }

    public boolean isWorldEnabled(final World world) {
        if (world == null) return false;
        final Snapshot current = runtime;
        if (current.scopeMode() == ScopeMode.ALL) return true;
        final String name = world.getName().toLowerCase(Locale.ROOT);
        final String uuid = world.getUID().toString().toLowerCase(Locale.ROOT);
        return current.scopeWorlds().contains(name) || current.scopeWorlds().contains(uuid);
    }

    public RewardRule essenceRule(final EntityType type) {
        final Snapshot current = runtime;
        return current.essenceRules().getOrDefault(type,
            new RewardRule(current.defaultEssenceAmount(), current.defaultEssenceChance()));
    }

    public RewardRule customDropRule(final EntityType type) {
        final Snapshot current = runtime;
        return current.customDropRules().getOrDefault(type,
            new RewardRule(current.defaultCustomDropAmount(), current.defaultCustomDropChance()));
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
    public ScopeMode scopeMode() { return runtime.scopeMode(); }
    public Set<String> enabledWorlds() { return runtime.scopeWorlds(); }
    public boolean essenceEnabled() { return runtime.essenceEnabled(); }
    public int defaultEssenceAmount() { return runtime.defaultEssenceAmount(); }
    public double defaultEssenceChance() { return runtime.defaultEssenceChance(); }
    public RewardDelivery essenceDelivery() { return runtime.essenceDelivery(); }
    public RewardItemFactory.ItemDefinition essenceItem() { return runtime.essenceItem(); }
    public boolean customDropEnabled() { return runtime.customDropEnabled(); }
    public int defaultCustomDropAmount() { return runtime.defaultCustomDropAmount(); }
    public double defaultCustomDropChance() { return runtime.defaultCustomDropChance(); }
    public RewardDelivery customDropDelivery() { return runtime.customDropDelivery(); }
    public RewardItemFactory.ItemDefinition customItem() { return runtime.customItem(); }
    public boolean adminGuiEnabled() { return runtime.adminGuiEnabled(); }
    public String adminGuiTitle() { return runtime.adminGuiTitle(); }
    public int configBackupsToKeep() { return runtime.configBackupsToKeep(); }
    public boolean silkRecoveredMessage() { return runtime.silkRecoveredMessage(); }
    public boolean essenceAwardedMessage() { return runtime.essenceAwardedMessage(); }
    public boolean customDropAwardedMessage() { return runtime.customDropAwardedMessage(); }
    public List<String> validationWarnings() { return runtime.validationWarnings(); }

    public static EntityType parseEntityType(final String input) {
        if (input == null || input.isBlank()) return null;
        final String normalized = input.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            final EntityType type = EntityType.valueOf(normalized);
            return type == EntityType.UNKNOWN ? null : type;
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private static EnumMap<EntityType, RewardRule> rules(
        final ConfigurationSection overrides,
        final int defaultAmount,
        final double defaultChance,
        final String path
    ) {
        final EnumMap<EntityType, RewardRule> result = new EnumMap<>(EntityType.class);
        if (overrides == null) return result;
        for (final String key : overrides.getKeys(false)) {
            final EntityType type = parseEntityType(key);
            if (type == null) throw new IllegalArgumentException("Unknown entity type at " + path + "." + key);
            final ConfigurationSection section = overrides.getConfigurationSection(key);
            if (section == null) throw new IllegalArgumentException("Expected section at " + path + "." + key);
            result.put(type, new RewardRule(
                bounded(section.getInt("amount", defaultAmount), 1, 4096, path + "." + key + ".amount"),
                chance(section.getDouble("chance", defaultChance), path + "." + key + ".chance")));
        }
        return result;
    }

    private static EnumMap<EntityType, NonSilkRewardMode> modes(final ConfigurationSection overrides) {
        final EnumMap<EntityType, NonSilkRewardMode> result = new EnumMap<>(EntityType.class);
        if (overrides == null) return result;
        for (final String key : overrides.getKeys(false)) {
            final EntityType type = parseEntityType(key);
            if (type == null) throw new IllegalArgumentException("Unknown entity type at rewards.mob-overrides." + key);
            final String value = overrides.getString(key + ".mode");
            if (value != null) result.put(type, parseMode(value, "rewards.mob-overrides." + key + ".mode"));
        }
        return result;
    }

    private static NonSilkRewardMode parseMode(final String raw, final String path) {
        final NonSilkRewardMode mode = NonSilkRewardMode.parse(raw, null);
        if (mode == null) throw new IllegalArgumentException("Invalid reward mode at " + path + ": " + raw);
        return mode;
    }

    private static int bounded(final int value, final int min, final int max, final String path) {
        if (value < min || value > max) throw new IllegalArgumentException(path + " must be " + min + ".." + max);
        return value;
    }

    private static double chance(final double value, final String path) {
        if (!Double.isFinite(value) || value < 0.0D || value > 100.0D) {
            throw new IllegalArgumentException(path + " must be finite and 0..100");
        }
        return value;
    }

    private static String normalizeScopeToken(final String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateMiniMessage(final String value, final String path) {
        if (value == null) throw new IllegalArgumentException(path + " cannot be null");
        try {
            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(value);
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException("Invalid MiniMessage at " + path, exception);
        }
    }
}
