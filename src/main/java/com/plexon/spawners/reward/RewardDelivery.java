package com.plexon.spawners.reward;

import com.plexon.spawners.config.PluginSettings;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RewardDelivery {
    private RewardDelivery() {}

    public static long deliver(
        final Player player,
        final Location fallbackLocation,
        final ItemStack template,
        final long totalAmount,
        final PluginSettings.RewardDelivery mode
    ) {
        if (totalAmount <= 0L || template == null || template.getType().isAir()) return 0L;
        final int maxStack = Math.max(1, template.getMaxStackSize());
        long remaining = totalAmount;
        long delivered = 0L;
        while (remaining > 0L) {
            final int amount = (int) Math.min((long) maxStack, remaining);
            final ItemStack stack = template.clone();
            stack.setAmount(amount);
            if (mode == PluginSettings.RewardDelivery.GROUND) {
                fallbackLocation.getWorld().dropItemNaturally(fallbackLocation, stack);
            } else {
                final Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                for (final ItemStack extra : overflow.values()) {
                    fallbackLocation.getWorld().dropItemNaturally(fallbackLocation, extra);
                }
            }
            delivered += amount;
            remaining -= amount;
        }
        return delivered;
    }
}
