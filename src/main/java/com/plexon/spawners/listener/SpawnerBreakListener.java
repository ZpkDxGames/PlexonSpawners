package com.plexon.spawners.listener;

import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.event.PlexonSpawnerEssenceAwardedEvent;
import com.plexon.spawners.event.PlexonSpawnerRecoveredEvent;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.SpawnerStateService;
import com.plexon.spawners.message.MessageService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
    private final PerformanceCounters counters;
    private final ManagedSpawnerRegistry registry;
    private final SpawnerStateService stateService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SpawnerBreakListener(
        final PluginSettings settings,
        final EssenceService essenceService,
        final SpawnerItemService spawnerItemService,
        final MessageService messages,
        final WildStackerCompat wildStackerCompat,
        final PerformanceCounters counters,
        final ManagedSpawnerRegistry registry,
        final SpawnerStateService stateService
    ) {
        this.settings = settings;
        this.essenceService = essenceService;
        this.spawnerItemService = spawnerItemService;
        this.messages = messages;
        this.wildStackerCompat = wildStackerCompat;
        this.counters = counters;
        this.registry = registry;
        this.stateService = stateService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerBreak(final BlockBreakEvent event) {
        counters.blockBreakSeen();
        if (event.getBlock().getType() != Material.SPAWNER) {
            counters.nonSpawnerFastReject();
            return;
        }
        if (!settings.breakingEnabled()) {
            counters.disabledReject();
            return;
        }
        if (!settings.isWorldEnabled(event.getBlock().getWorld())) {
            counters.worldReject();
            return;
        }
        if (!(event.getBlock().getState() instanceof CreatureSpawner spawner)) {
            return;
        }
        counters.acceptedSpawnerBreak();

        final Player player = event.getPlayer();
        final Location blockLocation = event.getBlock().getLocation();
        ManagedSpawner managed = registry.find(blockLocation);
        if (managed == null && stateService.isManaged(spawner)) {
            managed = stateService.recover(spawner);
            if (managed != null) {
                registry.register(managed);
            }
        }

        if (managed != null) {
            final boolean administrator = player.hasPermission("plexonspawners.admin")
                || player.hasPermission("plexonspawners.bypass.access");
            if (!managed.access().canBreak(player.getUniqueId(), managed.ownerId(), administrator)) {
                event.setCancelled(true);
                player.sendMessage(miniMessage.deserialize(
                    "<!italic><#FF6B6B>You do not have permission to break this managed spawner.</#FF6B6B>"
                ));
                return;
            }
        }

        EntityType entityType = managed == null ? spawner.getSpawnedType() : managed.type();
        if (entityType == null) {
            entityType = EntityType.PIG;
        }
        final int recoveredTier = managed == null ? 1 : managed.tier();

        final ItemStack tool = player.getInventory().getItemInMainHand();
        final int silkLevel = tool.getEnchantmentLevel(Enchantment.SILK_TOUCH);
        final int requiredLevel = settings.requiredSilkTouchLevel();
        final boolean needsBypassCheck = requiredLevel > 0
            && silkLevel < requiredLevel
            && settings.silkBypassPermissionEnabled();
        final boolean hasExplicitBypass = needsBypassCheck && player.hasPermission("plexonspawners.bypass.silk");
        final boolean usedBypass = hasExplicitBypass;
        final boolean qualified = requiredLevel <= 0 || silkLevel >= requiredLevel || hasExplicitBypass;

        if (!settings.takeOwnership()) {
            handleLegacyOutcome(event, player, entityType, recoveredTier, silkLevel, usedBypass, qualified);
            return;
        }

        final int experience = settings.dropExperience() ? Math.max(0, event.getExpToDrop()) : 0;
        final WildStackerCompat.Result stackResult = wildStackerCompat.unstackOne(spawner, player);
        switch (stackResult) {
            case NOT_INSTALLED -> counters.wildStackerNotInstalled();
            case NOT_STACKED -> counters.wildStackerNotStacked();
            case SUCCESS -> counters.wildStackerSuccess();
            case CANCELLED -> counters.wildStackerCancelled();
            case UNAVAILABLE -> counters.wildStackerDegraded();
        }
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
            if (managed != null) {
                registry.remove(managed.id());
            }
        }

        damageTool(player);
        if (experience > 0) {
            final Location xpLocation = blockLocation.clone().add(0.5, 0.5, 0.5);
            final ExperienceOrb orb = event.getBlock().getWorld().spawn(xpLocation, ExperienceOrb.class);
            orb.setExperience(experience);
        }

        if (player.getGameMode() == GameMode.CREATIVE && !settings.creativeDrops()) {
            return;
        }

        handleManagedOutcome(
            event,
            player,
            entityType,
            recoveredTier,
            silkLevel,
            usedBypass,
            stackResult == WildStackerCompat.Result.SUCCESS,
            qualified
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLegacySpawnerBreakCommit(final BlockBreakEvent event) {
        if (settings.takeOwnership() || event.getBlock().getType() != Material.SPAWNER) {
            return;
        }
        final ManagedSpawner managed = registry.find(event.getBlock().getLocation());
        if (managed != null) {
            registry.remove(managed.id());
        }
    }

    private void handleLegacyOutcome(
        final BlockBreakEvent event,
        final Player player,
        final EntityType entityType,
        final int recoveredTier,
        final int silkLevel,
        final boolean usedBypass,
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
        handleManagedOutcome(event, player, entityType, recoveredTier, silkLevel, usedBypass, false, qualified);
    }

    private void handleManagedOutcome(
        final BlockBreakEvent event,
        final Player player,
        final EntityType entityType,
        final int recoveredTier,
        final int silkLevel,
        final boolean usedBypass,
        final boolean wildStackerManaged,
        final boolean qualified
    ) {
        if (qualified) {
            if (!settings.dropSpawnerWhenQualified()) {
                return;
            }

            final Location sourceLocation = event.getBlock().getLocation();
            event.getBlock().getWorld().dropItemNaturally(
                sourceLocation,
                spawnerItemService.createSpawner(entityType, 1, recoveredTier)
            );
            counters.qualifiedRecovery();
            final String transactionId = newTransactionId();
            fireRecoveredEvent(
                player,
                entityType,
                sourceLocation,
                silkLevel,
                usedBypass,
                wildStackerManaged,
                transactionId
            );
            if (settings.breakSuccessMessages()) {
                messages.send(player, "spawner-recovered", Map.of("mob", SpawnerItemService.pretty(entityType)));
            }
            return;
        }

        if (!settings.essenceEnabled()) {
            return;
        }

        final PluginSettings.EssenceRule rule = settings.essenceRule(entityType);
        counters.essenceRoll();
        if (!passesChance(rule.chance())) {
            return;
        }

        final Location sourceLocation = event.getBlock().getLocation();
        deliverEssence(player, sourceLocation, rule.amount());
        counters.essenceWin();
        counters.essenceLogicalAmountAwarded(rule.amount());
        final String transactionId = newTransactionId();
        fireEssenceEvent(player, entityType, sourceLocation, rule.amount(), transactionId);
        if (settings.breakFailedMessages()) {
            messages.send(player, "essence-dropped", Map.of("amount", Integer.toString(rule.amount())));
        }
    }

    private void fireRecoveredEvent(
        final Player player,
        final EntityType entityType,
        final Location sourceLocation,
        final int silkLevel,
        final boolean usedBypass,
        final boolean wildStackerManaged,
        final String transactionId
    ) {
        requirePrimaryThread();
        Bukkit.getPluginManager().callEvent(new PlexonSpawnerRecoveredEvent(
            player,
            entityType,
            1,
            sourceLocation,
            silkLevel,
            usedBypass,
            wildStackerManaged,
            transactionId + ":recovered",
            transactionId
        ));
    }

    private void fireEssenceEvent(
        final Player player,
        final EntityType entityType,
        final Location sourceLocation,
        final int amount,
        final String transactionId
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
            sourceLocation,
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

    private void deliverEssence(final Player player, final Location sourceLocation, final int totalAmount) {
        final ItemStack[] stacks = essenceService.createStacks(totalAmount);
        counters.essenceItemStacksCreated(stacks.length);
        if (settings.essenceDelivery() == PluginSettings.EssenceDelivery.INVENTORY) {
            final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stacks);
            counters.essenceGroundEntitiesCreated(leftovers.size());
            leftovers.values().forEach(leftover -> sourceLocation.getWorld().dropItemNaturally(sourceLocation, leftover));
            return;
        }

        counters.essenceGroundEntitiesCreated(stacks.length);
        for (final ItemStack stack : stacks) {
            sourceLocation.getWorld().dropItemNaturally(sourceLocation, stack);
        }
    }

    private static String newTransactionId() {
        return UUID.randomUUID().toString();
    }
}
