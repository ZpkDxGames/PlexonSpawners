package com.plexon.spawners.listener;

import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.NativeStackSettings;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.event.PlexonSpawnerEssenceAwardedEvent;
import com.plexon.spawners.event.PlexonSpawnerRecoveredEvent;
import com.plexon.spawners.item.EssenceRewardPolicy;
import com.plexon.spawners.item.EssenceService;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.SpawnerStackDisplayService;
import com.plexon.spawners.managed.SpawnerStateService;
import com.plexon.spawners.managed.SpawnerTuning;
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
    private final NativeStackSettings stackSettings;
    private final EssenceService essenceService;
    private final SpawnerItemService spawnerItemService;
    private final MessageService messages;
    private final WildStackerCompat wildStackerCompat;
    private final PerformanceCounters counters;
    private final ManagedSpawnerRegistry registry;
    private final SpawnerStateService stateService;
    private final SpawnerTuning tuning;
    private final SpawnerStackDisplayService displays;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SpawnerBreakListener(
        final PluginSettings settings,
        final NativeStackSettings stackSettings,
        final EssenceService essenceService,
        final SpawnerItemService spawnerItemService,
        final MessageService messages,
        final WildStackerCompat wildStackerCompat,
        final PerformanceCounters counters,
        final ManagedSpawnerRegistry registry,
        final SpawnerStateService stateService,
        final SpawnerTuning tuning,
        final SpawnerStackDisplayService displays
    ) {
        this.settings = settings;
        this.stackSettings = stackSettings;
        this.essenceService = essenceService;
        this.spawnerItemService = spawnerItemService;
        this.messages = messages;
        this.wildStackerCompat = wildStackerCompat;
        this.counters = counters;
        this.registry = registry;
        this.stateService = stateService;
        this.tuning = tuning;
        this.displays = displays;
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
                    "<!italic><#FF6B6B>You do not have permission to break this managed spawner.</#FF6B6B>"));
                return;
            }
            if (stackSettings.enabled()) {
                if (stackSettings.requireOwner()
                    && !administrator
                    && !managed.ownerId().equals(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage(miniMessage.deserialize(
                        "<!italic><#FF6B6B>Only the owner or an administrator can break this native spawner stack.</#FF6B6B>"));
                    return;
                }
                handleNativeManagedBreak(event, player, spawner, managed);
                return;
            }
        }

        handleLegacyProviderBreak(event, player, spawner, managed);
    }

    private void handleNativeManagedBreak(
        final BlockBreakEvent event,
        final Player player,
        final CreatureSpawner spawner,
        final ManagedSpawner managed
    ) {
        if (managed.migrationState().blocksMutation()) {
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#FFB86C>This spawner is awaiting safe stack migration; mutation is blocked.</#FFB86C>"));
            return;
        }

        final Qualification qualification = qualification(player);
        final int logicalAmount = managed.stackAmount();
        final boolean removeAll = player.isSneaking() ? stackSettings.sneakBreakAll() : stackSettings.normalBreakAll();
        final int removedAmount = removeAll ? logicalAmount : 1;
        final int remaining = logicalAmount - removedAmount;
        final int experience = settings.dropExperience() ? Math.max(0, event.getExpToDrop()) : 0;

        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);

        if (remaining > 0) {
            final ManagedSpawner updated = registry.updateStackAmount(managed.id(), remaining);
            if (updated == null || !stateService.apply(spawner, updated, tuning.tier(updated.tier()))) {
                if (updated != null) {
                    registry.updateStackAmount(managed.id(), logicalAmount);
                }
                player.sendMessage(miniMessage.deserialize(
                    "<!italic><#FF6B6B>The stack could not be updated safely; nothing was removed.</#FF6B6B>"));
                return;
            }
            displays.refresh(updated);
        } else {
            displays.remove(managed);
            registry.remove(managed.id());
            event.getBlock().setType(Material.AIR, false);
        }

        damageTool(player);
        awardExperience(event, experience);
        if (player.getGameMode() == GameMode.CREATIVE && !stackSettings.creativeDropOnBreak()) {
            return;
        }
        handleOutcome(event, player, managed.type(), managed.tier(), qualification,
            false, removedAmount);
    }

    private void handleLegacyProviderBreak(
        final BlockBreakEvent event,
        final Player player,
        final CreatureSpawner spawner,
        final ManagedSpawner managed
    ) {
        EntityType entityType = managed == null ? spawner.getSpawnedType() : managed.type();
        if (entityType == EntityType.UNKNOWN) {
            event.setCancelled(true);
            player.sendMessage(miniMessage.deserialize(
                "<!italic><#FF6B6B>This spawner has an invalid UNKNOWN entity type; the break was cancelled to preserve it.</#FF6B6B>"));
            return;
        }
        if (entityType == null) {
            entityType = EntityType.PIG;
        }
        final int recoveredTier = managed == null ? 1 : managed.tier();
        final Qualification qualification = qualification(player);

        if (!settings.takeOwnership()) {
            event.setDropItems(false);
            if (!settings.dropExperience()) {
                event.setExpToDrop(0);
            }
            handleOutcome(event, player, entityType, recoveredTier, qualification, false, 1);
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
        if (stackResult == WildStackerCompat.Result.UNAVAILABLE || stackResult == WildStackerCompat.Result.CANCELLED) {
            return;
        }

        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (stackResult == WildStackerCompat.Result.NOT_INSTALLED || stackResult == WildStackerCompat.Result.NOT_STACKED) {
            event.getBlock().setType(Material.AIR, false);
            if (managed != null) {
                displays.remove(managed);
                registry.remove(managed.id());
            }
        }
        damageTool(player);
        awardExperience(event, experience);
        if (player.getGameMode() == GameMode.CREATIVE && !settings.creativeDrops()) {
            return;
        }
        handleOutcome(event, player, entityType, recoveredTier, qualification,
            stackResult == WildStackerCompat.Result.SUCCESS, 1);
    }

    private Qualification qualification(final Player player) {
        final ItemStack tool = player.getInventory().getItemInMainHand();
        final int silkLevel = tool.getEnchantmentLevel(Enchantment.SILK_TOUCH);
        final int requiredLevel = settings.requiredSilkTouchLevel();
        final boolean needsBypass = requiredLevel > 0 && silkLevel < requiredLevel && settings.silkBypassPermissionEnabled();
        final boolean usedBypass = needsBypass && player.hasPermission("plexonspawners.bypass.silk");
        final boolean qualified = requiredLevel <= 0 || silkLevel >= requiredLevel || usedBypass;
        return new Qualification(silkLevel, usedBypass, qualified);
    }

    private void handleOutcome(
        final BlockBreakEvent event,
        final Player player,
        final EntityType entityType,
        final int recoveredTier,
        final Qualification qualification,
        final boolean wildStackerManaged,
        final int recoveredAmount
    ) {
        if (qualification.qualified()) {
            if (!settings.dropSpawnerWhenQualified()) {
                return;
            }
            final Location sourceLocation = event.getBlock().getLocation();
            deliverSpawnerItems(player, sourceLocation, entityType, recoveredTier, recoveredAmount);
            counters.qualifiedRecovery();
            final String transactionId = newTransactionId();
            fireRecoveredEvent(player, entityType, recoveredAmount, sourceLocation,
                qualification.silkLevel(), qualification.usedBypass(), wildStackerManaged, transactionId);
            if (settings.breakSuccessMessages()) {
                messages.send(player, "spawner-recovered", Map.of("mob", SpawnerItemService.pretty(entityType)));
            }
            return;
        }

        if (!settings.essenceEnabled()) {
            return;
        }

        final PluginSettings.EssenceRule rule = settings.essenceRule(entityType);
        final EssenceRewardPolicy.Award award = EssenceRewardPolicy.evaluate(
            recoveredAmount,
            rule.amount(),
            rule.chance(),
            () -> ThreadLocalRandom.current().nextDouble(100.0D));
        for (int index = 0; index < award.rolls(); index++) {
            counters.essenceRoll();
        }
        if (!award.awarded()) {
            return;
        }

        final Location sourceLocation = event.getBlock().getLocation();
        deliverEssence(player, sourceLocation, award.totalAmount());
        counters.essenceWin();
        counters.essenceLogicalAmountAwarded(award.totalAmount());
        final String transactionId = newTransactionId();
        fireEssenceEvent(player, entityType, sourceLocation, award.totalAmount(), transactionId);
        if (settings.breakFailedMessages()) {
            messages.send(player, "essence-dropped", Map.of("amount", Integer.toString(award.totalAmount())));
        }
    }

    private void deliverSpawnerItems(
        final Player player,
        final Location sourceLocation,
        final EntityType type,
        final int tier,
        final int amount
    ) {
        final ItemStack[] stacks = spawnerItemService.createSpawnerStacks(type, amount, tier);
        player.getInventory().addItem(stacks).values().forEach(leftover ->
            sourceLocation.getWorld().dropItemNaturally(sourceLocation, leftover));
    }

    private void fireRecoveredEvent(
        final Player player,
        final EntityType entityType,
        final int amount,
        final Location sourceLocation,
        final int silkLevel,
        final boolean usedBypass,
        final boolean wildStackerManaged,
        final String transactionId
    ) {
        requirePrimaryThread();
        Bukkit.getPluginManager().callEvent(new PlexonSpawnerRecoveredEvent(
            player, entityType, amount, sourceLocation, silkLevel, usedBypass, wildStackerManaged,
            transactionId + ":recovered", transactionId));
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
            player, entityType, amount, deliveryMode, sourceLocation,
            transactionId + ":essence", transactionId));
    }

    private static void awardExperience(final BlockBreakEvent event, final int experience) {
        if (experience <= 0) {
            return;
        }
        final Location xpLocation = event.getBlock().getLocation().clone().add(0.5, 0.5, 0.5);
        final ExperienceOrb orb = event.getBlock().getWorld().spawn(xpLocation, ExperienceOrb.class);
        orb.setExperience(experience);
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

    private static String newTransactionId() { return UUID.randomUUID().toString(); }

    private record Qualification(int silkLevel, boolean usedBypass, boolean qualified) {}
}
