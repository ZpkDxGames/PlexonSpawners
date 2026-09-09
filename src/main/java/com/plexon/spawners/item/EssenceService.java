package com.plexon.spawners.item;

import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EssenceService {
    private final JavaPlugin plugin;
    private final NamespacedKey essenceKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private ItemStack template;
    private int maxStackSize;

    public EssenceService(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.essenceKey = new NamespacedKey(plugin, "spawner_essence");
        reload();
    }

    public void reload() {
        final FileConfiguration config = plugin.getConfig();
        final ItemStack configured = config.getItemStack("essence.item");
        setRuntimeTemplate(configured == null || configured.getType().isAir() ? createDefault() : configured);
    }

    public ItemStack create(final int amount) {
        final ItemStack item = template.clone();
        item.setAmount(Math.max(1, Math.min(maxStackSize, amount)));
        return item;
    }

    public ItemStack[] createStacks(final int totalAmount) {
        final int safeTotal = Math.max(1, totalAmount);
        final int stackCount = (safeTotal + maxStackSize - 1) / maxStackSize;
        final ItemStack[] stacks = new ItemStack[stackCount];
        int remaining = safeTotal;
        for (int index = 0; index < stackCount; index++) {
            final int amount = Math.min(maxStackSize, remaining);
            stacks[index] = create(amount);
            remaining -= amount;
        }
        return stacks;
    }

    public int maxStackSize() {
        return maxStackSize;
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
        setRuntimeTemplate(source);
        final ItemStack serialized = template.clone();
        serialized.setAmount(1);
        plugin.getConfig().set("essence.item", serialized);
        plugin.saveConfig();
        return true;
    }

    private void setRuntimeTemplate(final ItemStack source) {
        template = secure(source);
        maxStackSize = Math.max(1, template.getMaxStackSize());
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
