package com.plexon.spawners.reward;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Exact reward-template codec. Paper's byte serialization is authoritative; the human-readable
 * fields are summaries/fallback migration input only.
 */
public final class RewardItemFactory {
    public static final String EXACT_DATA_KEY = "exact-data";

    public record ItemDefinition(
        Material material,
        String name,
        List<String> lore,
        boolean glow,
        String itemModel,
        String exactData
    ) {
        public ItemDefinition {
            Objects.requireNonNull(material, "material");
            lore = List.copyOf(lore == null ? List.of() : lore);
            exactData = exactData == null ? "" : exactData;
        }

        public boolean valid() {
            return !material.isAir() && !exactData.isBlank();
        }
    }

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ItemDefinition read(
        final FileConfiguration config,
        final String path,
        final Material defaultMaterial,
        final String defaultName,
        final List<String> defaultLore,
        final boolean defaultGlow
    ) {
        final ItemStack legacyStack = config.getItemStack(path);
        if (legacyStack != null && !legacyStack.getType().isAir()) return sanitize(legacyStack);
        return read(config.getConfigurationSection(path), defaultMaterial, defaultName, defaultLore, defaultGlow);
    }

    public ItemDefinition read(
        final ConfigurationSection section,
        final Material defaultMaterial,
        final String defaultName,
        final List<String> defaultLore,
        final boolean defaultGlow
    ) {
        if (section != null) {
            final String exact = section.getString(EXACT_DATA_KEY);
            if (exact != null && !exact.isBlank()) return summarize(decode(exact));
        }

        final Material material = section == null
            ? defaultMaterial
            : Material.matchMaterial(section.getString("material", defaultMaterial.name()));
        final ItemStack item = new ItemStack(material == null ? defaultMaterial : material);
        final ItemMeta meta = item.getItemMeta();
        final String name = section == null ? defaultName : section.getString("name", defaultName);
        final List<String> lore = section == null || section.getStringList("lore").isEmpty()
            ? defaultLore
            : section.getStringList("lore");
        final boolean glow = section == null ? defaultGlow : section.getBoolean("glow", defaultGlow);
        final String itemModel = section == null ? null : section.getString("item-model");

        if (name != null && !name.isBlank()) meta.displayName(miniMessage.deserialize(name));
        if (lore != null && !lore.isEmpty()) {
            final List<Component> components = new ArrayList<>(lore.size());
            for (final String line : lore) components.add(miniMessage.deserialize(line));
            meta.lore(components);
        }
        meta.setEnchantmentGlintOverride(glow);
        if (itemModel != null && !itemModel.isBlank()) {
            final NamespacedKey model = NamespacedKey.fromString(itemModel);
            if (model == null) throw new IllegalArgumentException("Invalid item-model key: " + itemModel);
            meta.setItemModel(model);
        }
        item.setItemMeta(meta);
        return sanitize(item);
    }

    public ItemDefinition sanitize(final ItemStack source) {
        if (source == null || source.getType().isAir()) return null;
        final ItemStack normalized = source.clone();
        normalized.setAmount(1);
        return summarize(normalized);
    }

    public ItemStack build(final ItemDefinition definition, final NamespacedKey identityKey) {
        if (definition == null || !definition.valid()) return new ItemStack(Material.AIR);
        final ItemStack item = decode(definition.exactData());
        item.setAmount(1);
        if (identityKey != null) {
            final ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(identityKey, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack restoreExact(final ItemDefinition definition) {
        if (definition == null || !definition.valid()) return new ItemStack(Material.AIR);
        final ItemStack item = decode(definition.exactData());
        item.setAmount(1);
        return item;
    }

    public void write(final ConfigurationSection config, final String path, final ItemDefinition definition) {
        if (definition == null || !definition.valid()) {
            throw new IllegalArgumentException("Invalid exact reward item for " + path);
        }
        config.set(path, null);
        config.set(path + "." + EXACT_DATA_KEY, definition.exactData());
        config.set(path + ".material", definition.material().name());
        config.set(path + ".name", definition.name());
        config.set(path + ".lore", definition.lore());
        config.set(path + ".glow", definition.glow());
        config.set(path + ".item-model",
            definition.itemModel() == null || definition.itemModel().isBlank() ? null : definition.itemModel());
    }

    public ItemStack decode(final String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("Exact item payload is empty");
        try {
            final byte[] bytes = Base64.getDecoder().decode(encoded);
            final ItemStack item = ItemStack.deserializeBytes(bytes);
            if (item == null || item.getType().isAir()) throw new IllegalArgumentException("Exact item payload resolves to AIR");
            item.setAmount(1);
            return item;
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("Malformed exact item payload", exception);
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException("Paper rejected exact item payload", exception);
        }
    }

    private ItemDefinition summarize(final ItemStack source) {
        final ItemStack normalized = source.clone();
        normalized.setAmount(1);
        final ItemMeta meta = normalized.getItemMeta();
        final String name = meta.hasDisplayName() && meta.displayName() != null
            ? miniMessage.serialize(meta.displayName())
            : "<white>" + pretty(normalized.getType().name()) + "</white>";
        final List<String> lore = meta.hasLore() && meta.lore() != null
            ? meta.lore().stream().map(miniMessage::serialize).toList()
            : List.of();
        final boolean glow = meta.hasEnchantmentGlintOverride()
            && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride());
        final String itemModel = meta.hasItemModel() && meta.getItemModel() != null
            ? meta.getItemModel().asString()
            : null;
        final String exact = Base64.getEncoder().encodeToString(normalized.serializeAsBytes());
        return new ItemDefinition(normalized.getType(), name, lore, glow, itemModel, exact);
    }

    private static String pretty(final String raw) {
        final String normalized = raw.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return normalized.isEmpty() ? raw : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
