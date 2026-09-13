package com.plexon.spawners.reward;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

final class RewardItemAndDeliveryContractTest {
    @Test
    void itemDefinitionIsImmutableAndRejectsAirMaterials() {
        final ArrayList<String> lore = new ArrayList<>(List.of("<gray>one</gray>"));
        final RewardItemFactory.ItemDefinition definition = new RewardItemFactory.ItemDefinition(
            Material.PRISMARINE_CRYSTALS,
            "<aqua>Fragment</aqua>",
            lore,
            true,
            "plexon:spawner_fragment");

        lore.add("<red>mutated</red>");
        assertTrue(definition.valid());
        assertTrue(definition.lore().size() == 1);
        assertThrows(UnsupportedOperationException.class, () -> definition.lore().add("x"));
        assertFalse(new RewardItemFactory.ItemDefinition(Material.AIR, "x", List.of(), false, null).valid());
        assertFalse(new RewardItemFactory.ItemDefinition(Material.CAVE_AIR, "x", List.of(), false, null).valid());
        assertFalse(new RewardItemFactory.ItemDefinition(Material.VOID_AIR, "x", List.of(), false, null).valid());
    }

    @Test
    void rewardItemFactoryPreservesOnlySupportedVisualFieldsAndOwnIdentity() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/reward/RewardItemFactory.java"));

        assertTrue(source.contains("meta.displayName(miniMessage.deserialize(definition.name()))"));
        assertTrue(source.contains("meta.lore(lore)"));
        assertTrue(source.contains("meta.setEnchantmentGlintOverride(definition.glow())"));
        assertTrue(source.contains("NamespacedKey.fromString(definition.itemModel())"));
        assertTrue(source.contains("meta.setItemModel(model)"));
        assertTrue(source.contains("meta.getPersistentDataContainer().set(identityKey, PersistentDataType.INTEGER, 1)"));

        assertTrue(source.contains("sourceMeta.displayName()"));
        assertTrue(source.contains("sourceMeta.lore()"));
        assertTrue(source.contains("sourceMeta.getEnchantmentGlintOverride()"));
        assertTrue(source.contains("sourceMeta.getItemModel()"));
        assertFalse(source.contains("sourceMeta.getPersistentDataContainer()"),
            "Held-item templates must not copy arbitrary foreign PDC into reward definitions");
    }

    @Test
    void deliveryContractFallsBackToGroundForInventoryOverflow() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/com/plexon/spawners/reward/RewardDelivery.java"));

        assertTrue(source.contains("player.getInventory().addItem(stack)"));
        assertTrue(source.contains("for (final ItemStack extra : overflow.values())"));
        assertTrue(source.contains("fallbackLocation.getWorld().dropItemNaturally(fallbackLocation, extra)"));
        assertTrue(source.contains("mode == PluginSettings.RewardDelivery.GROUND"));
        assertTrue(source.contains("fallbackLocation.getWorld().dropItemNaturally(fallbackLocation, stack)"));
        assertTrue(source.contains("Math.min((long) maxStack, remaining)"),
            "Large logical rewards must be chunked to legal item-stack sizes");
    }
}
