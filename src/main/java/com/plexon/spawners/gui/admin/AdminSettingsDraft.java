package com.plexon.spawners.gui.admin;

import com.plexon.spawners.breaking.NonSilkRewardMode;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.reward.RewardItemFactory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

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

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private boolean breakingEnabled;
    private int requiredSilk;
    private boolean allowBypass;
    private NonSilkRewardMode defaultMode;
    private boolean creativeRecover;
    private boolean creativeEssence;
    private boolean creativeCustom;
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

    private boolean withdrawalEnabled;
    private boolean openOnRightClick;
    private String withdrawalTitle;
    private final List<Integer> withdrawPresets = new ArrayList<>();

    private boolean adminGuiEnabled;
    private String adminGuiTitle;
    private int backupsToKeep;

    private boolean silkRecoveredMessage;
    private boolean essenceAwardedMessage;
    private boolean customAwardedMessage;
    private boolean withdrawSuccessMessage;
    private boolean withdrawFailedMessage;

    private AdminSettingsDraft() {}

    public static AdminSettingsDraft from(final FileConfiguration config) {
        final AdminSettingsDraft draft = new AdminSettingsDraft();
        draft.breakingEnabled = config.getBoolean("breaking.enabled", true);
        draft.requiredSilk = clamp(config.getInt("breaking.required-silk-touch-level", 1), 0, 255);
        draft.allowBypass = config.getBoolean("breaking.allow-silk-bypass-permission", false);
        draft.defaultMode = NonSilkRewardMode.parse(config.getString("breaking.non-silk-reward-mode"),
            config.getBoolean("essence.enabled", true) ? NonSilkRewardMode.ESSENCE : NonSilkRewardMode.NONE);
        draft.creativeRecover = config.getBoolean("breaking.creative.recover-spawner", false);
        draft.creativeEssence = config.getBoolean("breaking.creative.award-essence", false);
        draft.creativeCustom = config.getBoolean("breaking.creative.award-custom-item", false);
        for (final String world : config.getStringList("breaking.enabled-worlds")) {
            if (!world.isBlank()) draft.enabledWorlds.add(world);
        }

        final RewardItemFactory factory = new RewardItemFactory();
        draft.essenceEnabled = config.getBoolean("essence.enabled", true);
        draft.essenceAmount = clamp(config.getInt("essence.default-amount", 1), 1, 4096);
        draft.essenceChance = clampChance(config.getDouble("essence.default-chance", 35.0D));
        draft.essenceDelivery = PluginSettings.RewardDelivery.parse(config.getString("essence.delivery"),
            PluginSettings.RewardDelivery.INVENTORY);
        final ItemStack legacyEssence = config.getItemStack("essence.item");
        draft.essenceItem = legacyEssence != null && !legacyEssence.getType().isAir()
            ? factory.sanitize(legacyEssence)
            : factory.read(config.getConfigurationSection("essence.item"), Material.AMETHYST_SHARD,
                "<gradient:#56B9F2:#92E1FF><b>Spawner Essence</b></gradient>",
                List.of("<gray>A concentrated fragment of spawner energy.</gray>"), true);

        draft.customEnabled = config.getBoolean("custom-drop.enabled", false);
        draft.customAmount = clamp(config.getInt("custom-drop.default-amount", 1), 1, 4096);
        draft.customChance = clampChance(config.getDouble("custom-drop.default-chance", 15.0D));
        draft.customDelivery = PluginSettings.RewardDelivery.parse(config.getString("custom-drop.delivery"),
            PluginSettings.RewardDelivery.INVENTORY);
        draft.customItem = factory.read(config.getConfigurationSection("custom-drop.item"), Material.PRISMARINE_CRYSTALS,
            "<gradient:#7BE7FF:#4AA8FF><b>Spawner Fragment</b></gradient>",
            List.of("<gray>Dropped when a spawner is broken without Silk Touch.</gray>"), true);

        final ConfigurationSection modeOverrides = config.getConfigurationSection("rewards.mob-overrides");
        final ConfigurationSection essenceOverrides = config.getConfigurationSection("essence.mob-overrides");
        final ConfigurationSection customOverrides = config.getConfigurationSection("custom-drop.mob-overrides");
        for (final EntityType type : EntityType.values()) {
            final String key = type.name();
            final boolean hasMode = modeOverrides != null && modeOverrides.contains(key + ".mode");
            final boolean hasEssence = essenceOverrides != null && essenceOverrides.contains(key);
            final boolean hasCustom = customOverrides != null && customOverrides.contains(key);
            if (!hasMode && !hasEssence && !hasCustom) continue;
            final NonSilkRewardMode mode = hasMode
                ? NonSilkRewardMode.parse(modeOverrides.getString(key + ".mode"), draft.defaultMode)
                : draft.defaultMode;
            draft.mobOverrides.put(type, new MobOverride(
                mode,
                hasEssence ? clamp(essenceOverrides.getInt(key + ".amount", draft.essenceAmount), 1, 4096) : draft.essenceAmount,
                hasEssence ? clampChance(essenceOverrides.getDouble(key + ".chance", draft.essenceChance)) : draft.essenceChance,
                hasCustom ? clamp(customOverrides.getInt(key + ".amount", draft.customAmount), 1, 4096) : draft.customAmount,
                hasCustom ? clampChance(customOverrides.getDouble(key + ".chance", draft.customChance)) : draft.customChance));
        }

        draft.withdrawalEnabled = config.getBoolean("gui.enabled", true);
        draft.openOnRightClick = config.getBoolean("gui.open-on-right-click", true);
        draft.withdrawalTitle = config.getString("gui.title",
            "<gradient:#56B9F2:#92E1FF><b>Spawner Withdrawal</b></gradient>");
        final LinkedHashSet<Integer> presets = new LinkedHashSet<>();
        for (final int value : config.getIntegerList("gui.withdraw-presets")) if (value > 0) presets.add(Math.min(4096, value));
        draft.withdrawPresets.addAll(presets.isEmpty() ? List.of(1, 8, 16, 32, 64) : presets);

        draft.adminGuiEnabled = config.getBoolean("admin-gui.enabled", true);
        draft.adminGuiTitle = config.getString("admin-gui.title",
            "<gradient:#56B9F2:#92E1FF><b>PlexonSpawners Admin</b></gradient>");
        draft.backupsToKeep = clamp(config.getInt("admin-gui.config-backups-to-keep", 10), 1, 50);
        draft.silkRecoveredMessage = config.getBoolean("messages.silk-recovered", true);
        draft.essenceAwardedMessage = config.getBoolean("messages.essence-awarded", true);
        draft.customAwardedMessage = config.getBoolean("messages.custom-drop-awarded", true);
        draft.withdrawSuccessMessage = config.getBoolean("messages.withdraw-success", true);
        draft.withdrawFailedMessage = config.getBoolean("messages.withdraw-failed", true);
        return draft;
    }

    public MobOverride ensureMobOverride(final EntityType type) {
        return mobOverrides.computeIfAbsent(type, ignored -> new MobOverride(
            defaultMode, essenceAmount, essenceChance, customAmount, customChance));
    }

    public void setMobOverride(final EntityType type, final MobOverride override) {
        mobOverrides.put(type, override);
    }

    public void resetMobOverride(final EntityType type) {
        mobOverrides.remove(type);
    }

    public ValidationResult validate() {
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        if (requiredSilk < 0 || requiredSilk > 255) errors.add("Silk Touch level must be between 0 and 255.");
        validateReward("Essence", essenceEnabled, essenceAmount, essenceChance, essenceItem, errors, warnings);
        validateReward("Custom drop", customEnabled, customAmount, customChance, customItem, errors, warnings);
        if ((defaultMode == NonSilkRewardMode.CUSTOM_ITEM || defaultMode == NonSilkRewardMode.ESSENCE_AND_CUSTOM_ITEM)
            && !customEnabled) warnings.add("Default reward mode references custom drops while custom drops are disabled.");
        if ((defaultMode == NonSilkRewardMode.ESSENCE || defaultMode == NonSilkRewardMode.ESSENCE_AND_CUSTOM_ITEM)
            && !essenceEnabled) warnings.add("Default reward mode references Essence while Essence is disabled.");
        if (withdrawPresets.isEmpty()) errors.add("At least one withdrawal preset is required.");
        if (new LinkedHashSet<>(withdrawPresets).size() != withdrawPresets.size()) errors.add("Withdrawal presets must be unique.");
        for (final int preset : withdrawPresets) if (preset < 1 || preset > 4096) errors.add("Withdrawal presets must be 1..4096.");
        if (withdrawalTitle == null || withdrawalTitle.isBlank()) errors.add("Withdrawal title cannot be blank.");
        if (adminGuiTitle == null || adminGuiTitle.isBlank()) errors.add("Admin GUI title cannot be blank.");
        validateMiniMessage(withdrawalTitle, "Withdrawal title", errors);
        validateMiniMessage(adminGuiTitle, "Admin GUI title", errors);
        for (final Map.Entry<EntityType, MobOverride> entry : mobOverrides.entrySet()) {
            final MobOverride override = entry.getValue();
            if (override.essenceAmount() < 1 || override.essenceAmount() > 4096) errors.add(entry.getKey() + " Essence amount is invalid.");
            if (!finiteChance(override.essenceChance())) errors.add(entry.getKey() + " Essence chance is invalid.");
            if (override.customAmount() < 1 || override.customAmount() > 4096) errors.add(entry.getKey() + " custom amount is invalid.");
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
        if (item == null || !item.valid()) errors.add(name + " item cannot be AIR or invalid.");
        if (enabled && chance == 0.0D) warnings.add(name + " is enabled with a 0% chance.");
        if (item != null) {
            validateMiniMessage(item.name(), name + " item name", errors);
            for (final String line : item.lore()) validateMiniMessage(line, name + " lore", errors);
        }
    }

    private static void validateMiniMessage(final String value, final String label, final List<String> errors) {
        if (value == null) return;
        try {
            MiniMessage.miniMessage().deserialize(value);
        } catch (final RuntimeException exception) {
            errors.add(label + " contains invalid MiniMessage formatting.");
        }
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
    public boolean withdrawalEnabled() { return withdrawalEnabled; }
    public void withdrawalEnabled(final boolean value) { withdrawalEnabled = value; }
    public boolean openOnRightClick() { return openOnRightClick; }
    public void openOnRightClick(final boolean value) { openOnRightClick = value; }
    public String withdrawalTitle() { return withdrawalTitle; }
    public List<Integer> withdrawPresets() { return withdrawPresets; }
    public boolean adminGuiEnabled() { return adminGuiEnabled; }
    public String adminGuiTitle() { return adminGuiTitle; }
    public int backupsToKeep() { return backupsToKeep; }
    public boolean silkRecoveredMessage() { return silkRecoveredMessage; }
    public void silkRecoveredMessage(final boolean value) { silkRecoveredMessage = value; }
    public boolean essenceAwardedMessage() { return essenceAwardedMessage; }
    public void essenceAwardedMessage(final boolean value) { essenceAwardedMessage = value; }
    public boolean customAwardedMessage() { return customAwardedMessage; }
    public void customAwardedMessage(final boolean value) { customAwardedMessage = value; }
    public boolean withdrawSuccessMessage() { return withdrawSuccessMessage; }
    public void withdrawSuccessMessage(final boolean value) { withdrawSuccessMessage = value; }
    public boolean withdrawFailedMessage() { return withdrawFailedMessage; }
    public void withdrawFailedMessage(final boolean value) { withdrawFailedMessage = value; }
}
