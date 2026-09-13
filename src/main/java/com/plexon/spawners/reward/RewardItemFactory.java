package com.plexon.spawners.reward;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class RewardItemFactory {
    public record ItemDefinition(
        Material material,
        String name,
        List<String> lore,
        boolean glow,
        Integer customModelData
    ) {
        public ItemDefinition {
            lore = List.copyOf(lore == null ? List.of() : lore);
        }

        public boolean valid() {
            return material != null && !material.isAir();
        }
    }

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ItemDefinition read(
        final ConfigurationSection section,
        final Material defaultMaterial,
        final String defaultName,
        final List<String> defaultLore,
        final boolean defaultGlow
    ) {
        if (section == null) return new ItemDefinition(defaultMaterial, defaultName, defaultLore, defaultGlow, null);
        final Material material = Material.matchMaterial(section.getString("material", defaultMaterial.name()));
        return new ItemDefinition(
            material == null ? defaultMaterial : material,
            section.getString("name", defaultName),
            section.getStringList("lore").isEmpty() ? defaultLore : section.getStringList("lore"),
            section.getBoolean("glow", defaultGlow),
            section.contains("custom-model-data") ? section.getInt("custom-model-data") : null);
    }

    public ItemStack build(final ItemDefinition definition, final NamespacedKey identityKey) {
        if (definition == null || !definition.valid()) return new ItemStack(Material.AIR);
        final ItemStack item = new ItemStack(definition.material());
        final ItemMeta meta = item.getItemMeta();
        if (definition.name() != null && !definition.name().isBlank()) {
            meta.displayName(miniMessage.deserialize(definition.name()));
        }
        if (!definition.lore().isEmpty()) {
            final List<Component> lore = new ArrayList<>(definition.lore().size());
            for (final String line : definition.lore()) lore.add(miniMessage.deserialize(line));
            meta.lore(lore);
        }
        meta.setEnchantmentGlintOverride(definition.glow());
        if (definition.customModelData() != null) meta.setCustomModelData(definition.customModelData());
        meta.getPersistentDataContainer().set(identityKey, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemDefinition sanitize(final ItemStack source) {
        if (source == null || source.getType().isAir()) return null;
        final ItemMeta sourceMeta = source.getItemMeta();
        final String name = sourceMeta.hasDisplayName() && sourceMeta.displayName() != null
            ? miniMessage.serialize(sourceMeta.displayName())
            : "<white>" + pretty(source.getType().name()) + "</white>";
        final List<String> lore = sourceMeta.hasLore() && sourceMeta.lore() != null
            ? sourceMeta.lore().stream().map(miniMessage::serialize).toList()
            : List.of();
        final boolean glow = Boolean.TRUE.equals(sourceMeta.getEnchantmentGlintOverride());
        final Integer model = sourceMeta.hasCustomModelData() ? sourceMeta.getCustomModelData() : null;
        return new ItemDefinition(source.getType(), name, lore, glow, model);
    }

    private static String pretty(final String raw) {
        final String normalized = raw.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return normalized.isEmpty() ? raw : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
