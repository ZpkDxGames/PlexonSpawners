package com.plexon.spawners.listener;

import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.event.PlexonSpawnerEssenceAwardedEvent;
import com.plexon.spawners.event.PlexonSpawnerRecoveredEvent;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.message.MessageService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
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
        final boolean hasExplicitBypass = settings.silkBypassPermissionEnabled()
            && player.hasPermission("plexonspawners.bypass.silk");
        final boolean usedBypass = requiredLevel > 0 && silkLevel < requiredLevel && hasExplicitBypass;
        final boolean qualified = requiredLevel <= 0 || silkLevel >= requiredLevel || hasExplicitBypass;
        final String transactionId = UUID.randomUUID().toString();

        if (!settings.takeOwnership()) {
            handleLegacyOutcome(event, player, entityType, silkLevel, usedBypass, transactionId, qualified);
            return;
        }

        final int experience = settings.dropExperience() ? Math.max(0, event.getExpToDrop()) : 0;
        final WildStackerCompat.Result stackResult = wildStackerCompat.unstackOne(spawner, player);

        if (stackResult == WildStackerCompat.Result.UNAVAILABLE
            || stackResult == WildStackerCompat.Result.CANCELLED) {
            return;
        }

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

        handleManagedOutcome(
            event,
            player,
            entityType,
            silkLevel,
            usedBypass,
            stackResult == WildStackerCompat.Result.SUCCESS,
            transactionId,
            qualified
        );
    }

    private void handleLegacyOutcome(
        final BlockBreakEvent event,
        final Player player,
        final EntityType entityType,
        final int silkLevel,
        final boolean usedBypass,
        final String transactionId,
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
        handleManagedOutcome(event, player, entityType, silkLevel, usedBypass, false, transactionId, qualified);
    }

    private void handleManagedOutcome(
        final BlockBreakEvent event,
        final Player player,
        final EntityType entityType,
        final int silkLevel,
        final boolean usedBypass,
        final boolean wildStackerManaged,
        final String transactionId,
        final boolean qualified
    ) {
        if (qualified) {
            if (settings.dropSpawnerWhenQualified()) {
                event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation(),
                    spawnerItemService.createSpawner(entityType, 1)
                );
                fireRecoveredEvent(
                    player,
                    entityType,
                    event,
                    silkLevel,
                    usedBypass,
                    wildStackerManaged,
                    transactionId
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
        fireEssenceEvent(player, entityType, event, amount, transactionId);
        if (settings.breakFailedMessages()) {
            messages.send(player, "essence-dropped", Map.of("amount", Integer.toString(amount)));
        }
    }

    private void fireRecoveredEvent(
        Player player,
        EntityType entityType,
        BlockBreakEvent source,
        int silkLevel,
        boolean usedBypass,
        boolean wildStackerManaged,
        String transactionId
    ) {
        requirePrimaryThread();
        Bukkit.getPluginManager().callEvent(new PlexonSpawnerRecoveredEvent(
            player,
            entityType,
            1,
            source.getBlock().getLocation(),
            silkLevel,
            usedBypass,
            wildStackerManaged,
            transactionId + ":recovered",
            transactionId
        ));
    }

    private void fireEssenceEvent(
        Player player,
        EntityType entityType,
        BlockBreakEvent source,
        int amount,
        String transactionId
    ) {
        requirePrimaryThread();
        final PlexonSpawnerEssenceAwardedEvent.DeliveryMode deliveryMode =
            settings.essenceDelivery() == PluginSettings.EssenceDelivery.INVENTORY
                ? PlexonSpawnerEssenceAwardedEvent.DeliveryMode.INVENTORY
                : PlexonSpawnerEssenceAwardedEvent.DeliveryMode.GROUND;
        Bukkit.getPluginManager().callEvent(new PlexonSpawnerEssenceAwardedEvent(
            player,
            entityType,
            amount,
            deliveryMode,
            source.getBlock().getLocation(),
            transactionId + ":essence",
            transactionId
        ));
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("PlexonSpawners public spawner events must fire on the primary server thread");
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
