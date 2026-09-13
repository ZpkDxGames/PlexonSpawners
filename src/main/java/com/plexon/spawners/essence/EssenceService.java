package com.plexon.spawners.essence;

import com.plexon.spawners.config.PluginSettings;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EssenceService {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final NamespacedKey essenceKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private ItemStack template;
    private int maxStackSize;

    public EssenceService(final JavaPlugin plugin, final PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.essenceKey = new NamespacedKey(plugin, "spawner_essence");
        reload();
    }

    public void reload() {
        final ItemStack configured = plugin.getConfig().getItemStack("essence.item");
        setTemplate(configured == null || configured.getType().isAir() ? createDefault() : configured);
    }

    public EssenceRewardPolicy.Award evaluate(final EntityType type, final int logicalAmount) {
        final PluginSettings.EssenceRule rule = settings.essenceRule(type);
        return EssenceRewardPolicy.evaluate(logicalAmount, rule.amount(), rule.chance(),
            () -> ThreadLocalRandom.current().nextDouble(100.0D));
    }

    public int deliver(final Player player, final Location fallbackLocation, final int totalAmount) {
        if (totalAmount <= 0) return 0;
        int delivered = 0;
        for (final ItemStack stack : createStacks(totalAmount)) {
            if (settings.essenceDelivery() == PluginSettings.EssenceDelivery.GROUND) {
                fallbackLocation.getWorld().dropItemNaturally(fallbackLocation, stack);
                delivered += stack.getAmount();
                continue;
            }
            final Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            delivered += stack.getAmount();
            for (final ItemStack extra : overflow.values()) {
                fallbackLocation.getWorld().dropItemNaturally(fallbackLocation, extra);
            }
        }
        return delivered;
    }

    public ItemStack[] createStacks(final int totalAmount) {
        final int safeTotal = Math.max(1, totalAmount);
        final int stackCount = (safeTotal + maxStackSize - 1) / maxStackSize;
        final ItemStack[] result = new ItemStack[stackCount];
        int remaining = safeTotal;
        for (int index = 0; index < stackCount; index++) {
            final ItemStack item = template.clone();
            final int amount = Math.min(maxStackSize, remaining);
            item.setAmount(amount);
            result[index] = item;
            remaining -= amount;
        }
        return result;
    }

    private void setTemplate(final ItemStack source) {
        final ItemStack secured = source.clone();
        secured.setAmount(1);
        final ItemMeta meta = secured.getItemMeta();
        meta.getPersistentDataContainer().set(essenceKey, PersistentDataType.INTEGER, 1);
        secured.setItemMeta(meta);
        template = secured;
        maxStackSize = Math.max(1, secured.getMaxStackSize());
    }

    private ItemStack createDefault() {
        final ItemStack item = new ItemStack(Material.ECHO_SHARD);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize("<gradient:#8A2BE2:#D56BFF><b>Spawner Essence</b></gradient>"));
        meta.lore(List.of(
            miniMessage.deserialize("<gray>A condensed remnant of a shattered spawner.</gray>"),
            miniMessage.deserialize("<dark_gray>PlexonCraft • Spawner currency</dark_gray>")));
        item.setItemMeta(meta);
        return item;
    }
}
