package com.plexon.spawners.listener;

import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.message.MessageService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnerBreakListener implements Listener {
    private final PluginSettings settings;
    private final EssenceService essenceService;
    private final SpawnerItemService spawnerItemService;
    private final MessageService messages;
    private final WildStackerCompat wildStackerCompat;

    public SpawnerBreakListener(
        final PluginSettings settings,
        final EssenceService essenceService,
        final SpawnerItemService spawnerItemService,
        final MessageService messages,
        final WildStackerCompat wildStackerCompat
    ) {
        this.settings = settings;
        this.essenceService = essenceService;
        this.spawnerItemService = spawnerItemService;
        this.messages = messages;
        this.wildStackerCompat = wildStackerCompat;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerBreak(final BlockBreakEvent event) {
        if (!settings.breakingEnabled() || event.getBlock().getType() != Material.SPAWNER) {
            return;
        }
        if (!settings.isWorldEnabled(event.getBlock().getWorld().getName())) {
            return;
        }
        if (!(event.getBlock().getState() instanceof CreatureSpawner spawner)) {
            return;
        }

        final Player player = event.getPlayer();
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

        if (!settings.takeOwnership()) {
            handleLegacyOutcome(event, player, entityType, qualified);
            return;
        }

        final int experience = settings.dropExperience() ? Math.max(0, event.getExpToDrop()) : 0;
        final WildStackerCompat.Result stackResult = wildStackerCompat.unstackOne(spawner, player);

        // If WildStacker is present but its API cannot safely complete the unstack,
        // do not force-remove the physical block. Let the external manager continue.
        if (stackResult == WildStackerCompat.Result.UNAVAILABLE
            || stackResult == WildStackerCompat.Result.CANCELLED) {
            return;
        }

        // From this point PlexonSpawners owns the successful break. Cancelling the
        // Bukkit event prevents later spawner managers that respect cancellation
        // (including WildStacker) from executing a second break/drop pipeline.
        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);

        if (stackResult == WildStackerCompat.Result.NOT_INSTALLED
            || stackResult == WildStackerCompat.Result.NOT_STACKED) {
            event.getBlock().setType(Material.AIR, false);
        }

        damageTool(player);
        if (experience > 0) {
            final ExperienceOrb orb = event.getBlock().getWorld().spawn(
                event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                ExperienceOrb.class
            );
            orb.setExperience(experience);
        }

        if (player.getGameMode() == GameMode.CREATIVE && !settings.creativeDrops()) {
            return;
        }

        handleManagedOutcome(event, player, entityType, qualified);
    }

    private void handleLegacyOutcome(
        final BlockBreakEvent event,
        final Player player,
        final EntityType entityType,
        final boolean qualified
    ) {
        if (!settings.shouldHandleCreative(player.getGameMode())) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }
        event.setDropItems(false);
        if (!settings.dropExperience()) {
            event.setExpToDrop(0);
        }
        handleManagedOutcome(event, player, entityType, qualified);
    }

    private void handleManagedOutcome(
        final BlockBreakEvent event,
        final Player player,
        final EntityType entityType,
        final boolean qualified
    ) {
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

        final double chance = settings.essenceChance(entityType);
        if (!passesChance(chance)) {
            return;
        }

        final int amount = settings.essenceAmount(entityType);
        deliverEssence(player, event, amount);
        if (settings.breakFailedMessages()) {
            messages.send(player, "essence-dropped", Map.of("amount", Integer.toString(amount)));
        }
    }

    private static void damageTool(final Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        final ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType().isAir()) {
            return;
        }
        player.damageItemStack(EquipmentSlot.HAND, 1);
    }

    private static boolean passesChance(final double chance) {
        if (chance <= 0.0) {
            return false;
        }
        if (chance >= 100.0) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble(100.0) < chance;
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
