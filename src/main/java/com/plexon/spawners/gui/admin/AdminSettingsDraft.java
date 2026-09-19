package com.plexon.spawners.gui.admin;

import com.plexon.spawners.breaking.NonSilkRewardMode;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.reward.RewardItemFactory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

public final class AdminSettingsDraft {
    public record MobOverride(
        NonSilkRewardMode mode,
        int essenceAmount,
        double essenceChance,
        int customAmount,
        double customChance
    ) {}

    public record ValidationResult(List<String> errors, List<String> warnings) {
        public ValidationResult {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
        public boolean valid() { return errors.isEmpty(); }
    }

    private boolean breakingEnabled;
    private int requiredSilk;
    private boolean allowBypass;
    private NonSilkRewardMode defaultMode;
    private boolean creativeRecover;
    private boolean creativeEssence;
    private boolean creativeCustom;
    private PluginSettings.ScopeMode scopeMode;
    private final LinkedHashSet<String> enabledWorlds = new LinkedHashSet<>();

    private boolean essenceEnabled;
    private int essenceAmount;
    private double essenceChance;
    private PluginSettings.RewardDelivery essenceDelivery;
    private RewardItemFactory.ItemDefinition essenceItem;

    private boolean customEnabled;
    private int customAmount;
    private double customChance;
    private PluginSettings.RewardDelivery customDelivery;
    private RewardItemFactory.ItemDefinition customItem;

    private final EnumMap<EntityType, MobOverride> mobOverrides = new EnumMap<>(EntityType.class);
    private boolean adminGuiEnabled;
    private String adminGuiTitle;
    private int backupsToKeep;
    private boolean silkRecoveredMessage;
    private boolean essenceAwardedMessage;
    private boolean customAwardedMessage;

    private AdminSettingsDraft() {}

    public static AdminSettingsDraft from(final FileConfiguration config) {
        final AdminSettingsDraft draft = new AdminSettingsDraft();
        draft.breakingEnabled = config.getBoolean("breaking.enabled", true);
        draft.requiredSilk = clamp(config.getInt("breaking.required-silk-touch-level", 1), 0, 255);
        draft.allowBypass = config.getBoolean("breaking.allow-silk-bypass-permission", false);
        draft.defaultMode = NonSilkRewardMode.parse(config.getString("breaking.non-silk-reward-mode"), NonSilkRewardMode.ESSENCE);
        draft.creativeRecover = config.getBoolean("breaking.creative.recover-spawner", true);
        draft.creativeEssence = config.getBoolean("breaking.creative.award-essence", true);
        draft.creativeCustom = config.getBoolean("breaking.creative.award-custom-item", false);
        try {
            draft.scopeMode = PluginSettings.ScopeMode.valueOf(
                config.getString("scope.mode", "ALLOWLIST").toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            draft.scopeMode = PluginSettings.ScopeMode.ALLOWLIST;
        }
        for (final String world : config.getStringList("scope.worlds")) {
            if (!world.isBlank()) draft.enabledWorlds.add(world);
        }

        final RewardItemFactory factory = new RewardItemFactory();
        draft.essenceEnabled = config.getBoolean("essence.enabled", true);
        draft.essenceAmount = clamp(config.getInt("essence.default-amount", 1), 1, 4096);
        draft.essenceChance = clampChance(config.getDouble("essence.default-chance", 35.0D));
        draft.essenceDelivery = parseDelivery(config.getString("essence.delivery", "INVENTORY"));
        draft.essenceItem = factory.read(config, "essence.item", Material.AMETHYST_SHARD,
            "<gradient:#56B9F2:#92E1FF><b>Spawner Essence</b></gradient>",
            List.of("<gray>A concentrated fragment of spawner energy.</gray>"), true);

        draft.customEnabled = config.getBoolean("custom-drop.enabled", false);
        draft.customAmount = clamp(config.getInt("custom-drop.default-amount", 1), 1, 4096);
        draft.customChance = clampChance(config.getDouble("custom-drop.default-chance", 15.0D));
        draft.customDelivery = parseDelivery(config.getString("custom-drop.delivery", "INVENTORY"));
        draft.customItem = factory.read(config, "custom-drop.item", Material.PRISMARINE_CRYSTALS,
            "<gradient:#7BE7FF:#4AA8FF><b>Spawner Fragment</b></gradient>",
            List.of("<gray>Dropped when a spawner is broken without Silk Touch.</gray>"), true);

        final ConfigurationSection modeOverrides = config.getConfigurationSection("rewards.mob-overrides");
        final ConfigurationSection essenceOverrides = config.getConfigurationSection("essence.mob-overrides");
        final ConfigurationSection customOverrides = config.getConfigurationSection("custom-drop.mob-overrides");
        final LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (modeOverrides != null) keys.addAll(modeOverrides.getKeys(false));
        if (essenceOverrides != null) keys.addAll(essenceOverrides.getKeys(false));
        if (customOverrides != null) keys.addAll(customOverrides.getKeys(false));
        for (final String key : keys) {
            final EntityType type = PluginSettings.parseEntityType(key);
            if (type == null) continue;
            final NonSilkRewardMode mode = modeOverrides != null && modeOverrides.contains(key + ".mode")
                ? NonSilkRewardMode.parse(modeOverrides.getString(key + ".mode"), draft.defaultMode)
                : draft.defaultMode;
            draft.mobOverrides.put(type, new MobOverride(
                mode,
                essenceOverrides != null
                    ? clamp(essenceOverrides.getInt(key + ".amount", draft.essenceAmount), 1, 4096)
                    : draft.essenceAmount,
                essenceOverrides != null
                    ? clampChance(essenceOverrides.getDouble(key + ".chance", draft.essenceChance))
                    : draft.essenceChance,
                customOverrides != null
                    ? clamp(customOverrides.getInt(key + ".amount", draft.customAmount), 1, 4096)
                    : draft.customAmount,
                customOverrides != null
                    ? clampChance(customOverrides.getDouble(key + ".chance", draft.customChance))
                    : draft.customChance));
        }

        draft.adminGuiEnabled = config.getBoolean("admin-gui.enabled", true);
        draft.adminGuiTitle = config.getString("admin-gui.title",
            "<gradient:#56B9F2:#92E1FF><b>PlexonSpawners Admin</b></gradient>");
        draft.backupsToKeep = clamp(config.getInt("admin-gui.config-backups-to-keep", 10), 1, 50);
        draft.silkRecoveredMessage = config.getBoolean("messages.silk-recovered", true);
        draft.essenceAwardedMessage = config.getBoolean("messages.essence-awarded", true);
        draft.customAwardedMessage = config.getBoolean("messages.custom-drop-awarded", true);
        return draft;
    }

    public AdminSettingsDraft copy() {
        final AdminSettingsDraft copy = new AdminSettingsDraft();
        copy.breakingEnabled = breakingEnabled;
        copy.requiredSilk = requiredSilk;
        copy.allowBypass = allowBypass;
        copy.defaultMode = defaultMode;
        copy.creativeRecover = creativeRecover;
        copy.creativeEssence = creativeEssence;
        copy.creativeCustom = creativeCustom;
        copy.scopeMode = scopeMode;
        copy.enabledWorlds.addAll(enabledWorlds);
        copy.essenceEnabled = essenceEnabled;
        copy.essenceAmount = essenceAmount;
        copy.essenceChance = essenceChance;
        copy.essenceDelivery = essenceDelivery;
        copy.essenceItem = essenceItem;
        copy.customEnabled = customEnabled;
        copy.customAmount = customAmount;
        copy.customChance = customChance;
        copy.customDelivery = customDelivery;
        copy.customItem = customItem;
        copy.mobOverrides.putAll(mobOverrides);
        copy.adminGuiEnabled = adminGuiEnabled;
        copy.adminGuiTitle = adminGuiTitle;
        copy.backupsToKeep = backupsToKeep;
        copy.silkRecoveredMessage = silkRecoveredMessage;
        copy.essenceAwardedMessage = essenceAwardedMessage;
        copy.customAwardedMessage = customAwardedMessage;
        return copy;
    }

    public MobOverride ensureMobOverride(final EntityType type) {
        return mobOverrides.computeIfAbsent(type, ignored -> new MobOverride(
            defaultMode, essenceAmount, essenceChance, customAmount, customChance));
    }
    public void setMobOverride(final EntityType type, final MobOverride override) { mobOverrides.put(type, override); }
    public void resetMobOverride(final EntityType type) { mobOverrides.remove(type); }

    public ValidationResult validate() {
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        if (requiredSilk < 0 || requiredSilk > 255) errors.add("Silk Touch level must be 0..255.");
        if (defaultMode == null) errors.add("Non-Silk reward mode is invalid.");
        if (scopeMode == null) errors.add("World scope mode is invalid.");
        if (scopeMode == PluginSettings.ScopeMode.ALLOWLIST && enabledWorlds.isEmpty()) {
            errors.add("ALLOWLIST scope requires at least one world name or UUID.");
        }
        if (scopeMode == PluginSettings.ScopeMode.ALL) warnings.add("ALL scope enables Plexon policy in every world.");
        for (final String world : enabledWorlds) if (world.isBlank()) errors.add("World scope contains a blank value.");
        validateReward("Essence", essenceEnabled, essenceAmount, essenceChance, essenceItem, errors, warnings);
        validateReward("Custom reward", customEnabled, customAmount, customChance, customItem, errors, warnings);
        validateMiniMessage(adminGuiTitle, "Admin GUI title", errors);
        for (final Map.Entry<EntityType, MobOverride> entry : mobOverrides.entrySet()) {
            final MobOverride override = entry.getValue();
            if (override.mode() == null) errors.add(entry.getKey() + " reward mode is invalid.");
            if (override.essenceAmount() < 1 || override.essenceAmount() > 4096) {
                errors.add(entry.getKey() + " Essence amount is invalid.");
            }
            if (!finiteChance(override.essenceChance())) errors.add(entry.getKey() + " Essence chance is invalid.");
            if (override.customAmount() < 1 || override.customAmount() > 4096) {
                errors.add(entry.getKey() + " custom amount is invalid.");
            }
            if (!finiteChance(override.customChance())) errors.add(entry.getKey() + " custom chance is invalid.");
        }
        return new ValidationResult(errors, warnings);
    }

    private static void validateReward(
        final String name,
        final boolean enabled,
        final int amount,
        final double chance,
        final RewardItemFactory.ItemDefinition item,
        final List<String> errors,
        final List<String> warnings
    ) {
        if (amount < 1 || amount > 4096) errors.add(name + " amount must be 1..4096.");
        if (!finiteChance(chance)) errors.add(name + " chance must be finite and 0..100.");
        if (item == null || !item.valid()) errors.add(name + " exact item payload is invalid.");
        if (enabled && chance == 0.0D) warnings.add(name + " is enabled with a 0% chance.");
    }

    private static void validateMiniMessage(final String value, final String label, final List<String> errors) {
        if (value == null) { errors.add(label + " cannot be null."); return; }
        try { MiniMessage.miniMessage().deserialize(value); }
        catch (final RuntimeException exception) { errors.add(label + " contains invalid MiniMessage formatting."); }
    }

    private static PluginSettings.RewardDelivery parseDelivery(final String raw) {
        try { return PluginSettings.RewardDelivery.valueOf(raw.toUpperCase(java.util.Locale.ROOT)); }
        catch (final RuntimeException exception) { return PluginSettings.RewardDelivery.INVENTORY; }
    }
    private static boolean finiteChance(final double value) {
        return Double.isFinite(value) && value >= 0.0D && value <= 100.0D;
    }
    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
    private static double clampChance(final double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(100.0D, value)) : 0.0D;
    }

    public boolean breakingEnabled() { return breakingEnabled; }
    public void breakingEnabled(final boolean value) { breakingEnabled = value; }
    public int requiredSilk() { return requiredSilk; }
    public void requiredSilk(final int value) { requiredSilk = clamp(value, 0, 255); }
    public boolean allowBypass() { return allowBypass; }
    public void allowBypass(final boolean value) { allowBypass = value; }
    public NonSilkRewardMode defaultMode() { return defaultMode; }
    public void defaultMode(final NonSilkRewardMode value) { defaultMode = value; }
    public boolean creativeRecover() { return creativeRecover; }
    public void creativeRecover(final boolean value) { creativeRecover = value; }
    public boolean creativeEssence() { return creativeEssence; }
    public void creativeEssence(final boolean value) { creativeEssence = value; }
    public boolean creativeCustom() { return creativeCustom; }
    public void creativeCustom(final boolean value) { creativeCustom = value; }
    public PluginSettings.ScopeMode scopeMode() { return scopeMode; }
    public void scopeMode(final PluginSettings.ScopeMode value) { scopeMode = value; }
    public Set<String> enabledWorlds() { return enabledWorlds; }
    public boolean essenceEnabled() { return essenceEnabled; }
    public void essenceEnabled(final boolean value) { essenceEnabled = value; }
    public int essenceAmount() { return essenceAmount; }
    public void essenceAmount(final int value) { essenceAmount = clamp(value, 1, 4096); }
    public double essenceChance() { return essenceChance; }
    public void essenceChance(final double value) { essenceChance = clampChance(value); }
    public PluginSettings.RewardDelivery essenceDelivery() { return essenceDelivery; }
    public void essenceDelivery(final PluginSettings.RewardDelivery value) { essenceDelivery = value; }
    public RewardItemFactory.ItemDefinition essenceItem() { return essenceItem; }
    public void essenceItem(final RewardItemFactory.ItemDefinition value) { essenceItem = value; }
    public boolean customEnabled() { return customEnabled; }
    public void customEnabled(final boolean value) { customEnabled = value; }
    public int customAmount() { return customAmount; }
    public void customAmount(final int value) { customAmount = clamp(value, 1, 4096); }
    public double customChance() { return customChance; }
    public void customChance(final double value) { customChance = clampChance(value); }
    public PluginSettings.RewardDelivery customDelivery() { return customDelivery; }
    public void customDelivery(final PluginSettings.RewardDelivery value) { customDelivery = value; }
    public RewardItemFactory.ItemDefinition customItem() { return customItem; }
    public void customItem(final RewardItemFactory.ItemDefinition value) { customItem = value; }
    public Map<EntityType, MobOverride> mobOverrides() { return mobOverrides; }
    public boolean adminGuiEnabled() { return adminGuiEnabled; }
    public String adminGuiTitle() { return adminGuiTitle; }
    public int backupsToKeep() { return backupsToKeep; }
    public boolean silkRecoveredMessage() { return silkRecoveredMessage; }
    public void silkRecoveredMessage(final boolean value) { silkRecoveredMessage = value; }
    public boolean essenceAwardedMessage() { return essenceAwardedMessage; }
    public void essenceAwardedMessage(final boolean value) { essenceAwardedMessage = value; }
    public boolean customAwardedMessage() { return customAwardedMessage; }
    public void customAwardedMessage(final boolean value) { customAwardedMessage = value; }
}
