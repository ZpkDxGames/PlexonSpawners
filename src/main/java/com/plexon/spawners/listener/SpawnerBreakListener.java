package com.plexon.spawners.listener;

import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.message.MessageService;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class SpawnerBreakListener implements Listener {
    private final PluginSettings settings;
    private final EssenceService essenceService;
    private final SpawnerItemService spawnerItemService;
    private final MessageService messages;

    public SpawnerBreakListener(
        final PluginSettings settings,
        final EssenceService essenceService,
        final SpawnerItemService spawnerItemService,
        final MessageService messages
    ) {
        this.settings = settings;
        this.essenceService = essenceService;
        this.spawnerItemService = spawnerItemService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerBreak(final BlockBreakEvent event) {
        if (!settings.breakingEnabled() || event.getBlock().getType() != Material.SPAWNER) {
            return;
        }
        if (!settings.isWorldEnabled(event.getBlock().getWorld().getName())) {
            return;
        }

        final Player player = event.getPlayer();
        if (!settings.shouldHandleCreative(player.getGameMode())) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }

        if (!(event.getBlock().getState() instanceof CreatureSpawner spawner)) {
            return;
        }

        EntityType entityType = spawner.getSpawnedType();
        if (entityType == null) {
            entityType = EntityType.PIG;
        }

        final ItemStack tool = player.getInventory().getItemInMainHand();
        final int silkLevel = tool.getEnchantmentLevel(Enchantment.SILK_TOUCH);
        final int requiredLevel = settings.requiredSilkTouchLevel();
        final boolean qualified = requiredLevel <= 0
            || silkLevel >= requiredLevel
            || player.hasPermission("plexonspawners.bypass.silk");

        event.setDropItems(false);
        if (!settings.dropExperience()) {
            event.setExpToDrop(0);
        }

        if (qualified) {
            if (settings.dropSpawnerWhenQualified()) {
                event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation(),
                    spawnerItemService.createSpawner(entityType, 1)
                );
                if (settings.breakSuccessMessages()) {
                    messages.send(player, "spawner-recovered", Map.of("mob", SpawnerItemService.pretty(entityType)));
                }
            }
            return;
        }

        if (!settings.essenceEnabled()) {
            return;
        }

        final int amount = settings.essenceAmount(entityType);
        deliverEssence(player, event, amount);
        if (settings.breakFailedMessages()) {
            messages.send(player, "essence-dropped", Map.of("amount", Integer.toString(amount)));
        }
    }

    private void deliverEssence(final Player player, final BlockBreakEvent event, final int totalAmount) {
        int remaining = totalAmount;
        final int maxStack = essenceService.template().getMaxStackSize();
        while (remaining > 0) {
            final int stackAmount = Math.min(maxStack, remaining);
            final ItemStack stack = essenceService.create(stackAmount);
            if (settings.essenceDelivery() == PluginSettings.EssenceDelivery.INVENTORY) {
                player.getInventory().addItem(stack).values().forEach(leftover ->
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), leftover)
                );
            } else {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), stack);
            }
            remaining -= stackAmount;
        }
    }
}
