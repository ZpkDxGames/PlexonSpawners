package com.plexon.spawners.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public final class ItemFactory {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ItemFactory() {
    }

    public static ItemStack create(final Material material, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(MINI_MESSAGE.deserialize(name));
        final List<Component> loreComponents = Arrays.stream(lore)
            .map(MINI_MESSAGE::deserialize)
            .toList();
        meta.lore(loreComponents);
        item.setItemMeta(meta);
        return item;
    }
}
