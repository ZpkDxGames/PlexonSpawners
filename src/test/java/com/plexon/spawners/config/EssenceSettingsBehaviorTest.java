package com.plexon.spawners.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

final class EssenceSettingsBehaviorTest {
    @Test
    void defaultsAreExplicitAndGroundDelivered() {
        final PluginSettings settings = new PluginSettings();
        settings.reload(new YamlConfiguration());

        assertTrue(settings.essenceEnabled());
        assertEquals(1, settings.defaultEssenceAmount());
        assertEquals(35.0D, settings.defaultEssenceChance());
        assertEquals(PluginSettings.EssenceDelivery.GROUND, settings.essenceDelivery());
        assertEquals(new PluginSettings.EssenceRule(1, 35.0D), settings.essenceRule(EntityType.ZOMBIE));
    }

    @Test
    void requiredProductionMobOverridesResolveExactly() {
        final YamlConfiguration config = new YamlConfiguration();
        config.set("essence.default-amount", 1);
        config.set("essence.default-chance", 35.0D);
        setOverride(config, "BLAZE", 3, 30.0D);
        setOverride(config, "CREEPER", 2, 35.0D);
        setOverride(config, "ENDERMAN", 3, 30.0D);
        setOverride(config, "WITHER_SKELETON", 4, 25.0D);
        setOverride(config, "IRON_GOLEM", 6, 20.0D);

        final PluginSettings settings = new PluginSettings();
        settings.reload(config);

        assertEquals(new PluginSettings.EssenceRule(3, 30.0D), settings.essenceRule(EntityType.BLAZE));
        assertEquals(new PluginSettings.EssenceRule(2, 35.0D), settings.essenceRule(EntityType.CREEPER));
        assertEquals(new PluginSettings.EssenceRule(3, 30.0D), settings.essenceRule(EntityType.ENDERMAN));
        assertEquals(new PluginSettings.EssenceRule(4, 25.0D), settings.essenceRule(EntityType.WITHER_SKELETON));
        assertEquals(new PluginSettings.EssenceRule(6, 20.0D), settings.essenceRule(EntityType.IRON_GOLEM));
        assertEquals(5, settings.essenceOverrideCount());
    }

    @Test
    void inventoryDeliveryAndInvalidDeliveryAreDeterministic() {
        final YamlConfiguration inventory = new YamlConfiguration();
        inventory.set("essence.delivery", "INVENTORY");
        final PluginSettings inventorySettings = new PluginSettings();
        inventorySettings.reload(inventory);
        assertEquals(PluginSettings.EssenceDelivery.INVENTORY, inventorySettings.essenceDelivery());

        final YamlConfiguration invalid = new YamlConfiguration();
        invalid.set("essence.delivery", "NOPE");
        final PluginSettings invalidSettings = new PluginSettings();
        invalidSettings.reload(invalid);
        assertEquals(PluginSettings.EssenceDelivery.GROUND, invalidSettings.essenceDelivery());
        assertFalse(invalidSettings.validationWarnings().isEmpty());
    }

    @Test
    void creativeRewardPolicyRemainsExplicit() {
        final PluginSettings defaults = new PluginSettings();
        defaults.reload(new YamlConfiguration());
        assertFalse(defaults.shouldHandleCreative(GameMode.CREATIVE));
        assertTrue(defaults.shouldHandleCreative(GameMode.SURVIVAL));

        final YamlConfiguration enabled = new YamlConfiguration();
        enabled.set("breaking.creative-drops", true);
        final PluginSettings configured = new PluginSettings();
        configured.reload(enabled);
        assertTrue(configured.shouldHandleCreative(GameMode.CREATIVE));
    }

    private static void setOverride(
        final YamlConfiguration config,
        final String mob,
        final int amount,
        final double chance
    ) {
        config.set("essence.mob-overrides." + mob + ".amount", amount);
        config.set("essence.mob-overrides." + mob + ".chance", chance);
    }
}
