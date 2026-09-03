package com.plexon.spawners.item;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class EssenceService {
    private final JavaPlugin plugin;
    private final NamespacedKey essenceKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private ItemStack template;

    public EssenceService(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.essenceKey = new NamespacedKey(plugin, "spawner_essence");
        reload();
    }

    public void reload() {
        final FileConfiguration config = plugin.getConfig();
        final ItemStack configured = config.getItemStack("essence.item");
        template = secure(configured == null || configured.getType().isAir() ? createDefault() : configured);
    }

    public ItemStack create(final int amount) {
        final ItemStack item = template.clone();
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        return item;
    }

    public ItemStack template() {
        final ItemStack item = template.clone();
        item.setAmount(1);
        return item;
    }

    public boolean isEssence(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        final Integer marker = item.getItemMeta().getPersistentDataContainer()
            .get(essenceKey, PersistentDataType.INTEGER);
        return marker != null && marker == 1;
    }

    public boolean setTemplate(final ItemStack source) {
        if (source == null || source.getType().isAir()) {
            return false;
        }
        template = secure(source);
        final ItemStack serialized = template.clone();
        serialized.setAmount(1);
        plugin.getConfig().set("essence.item", serialized);
        plugin.saveConfig();
        return true;
    }

    private ItemStack secure(final ItemStack source) {
        final ItemStack secured = source.clone();
        secured.setAmount(1);
        final ItemMeta meta = secured.getItemMeta();
        meta.getPersistentDataContainer().set(essenceKey, PersistentDataType.INTEGER, 1);
        secured.setItemMeta(meta);
        return secured;
    }

    private ItemStack createDefault() {
        final ItemStack item = new ItemStack(Material.ECHO_SHARD);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize("<gradient:#8A2BE2:#D56BFF><b>Spawner Essence</b></gradient>"));
        meta.lore(List.of(
            miniMessage.deserialize(""),
            miniMessage.deserialize("<gray>A condensed remnant of a shattered</gray>"),
            miniMessage.deserialize("<gray>creature spawner.</gray>"),
            miniMessage.deserialize(""),
            miniMessage.deserialize("<dark_gray>Physical spawner currency</dark_gray>")
        ));
        item.setItemMeta(meta);
        return item;
    }
}
