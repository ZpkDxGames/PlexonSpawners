package com.plexon.spawners.listener;

import com.bgsoftware.wildstacker.api.events.SpawnerDropEvent;
import com.bgsoftware.wildstacker.api.events.SpawnerUnstackEvent;
import com.bgsoftware.wildstacker.api.objects.StackedSpawner;
import com.plexon.spawners.breaking.BreakCompletionGate;
import com.plexon.spawners.breaking.BreakReconciliation;
import com.plexon.spawners.breaking.SpawnerBreakPolicy;
import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.essence.EssenceService;
import com.plexon.spawners.integration.WildStackerBridge;
import com.plexon.spawners.message.MessageService;
import com.plexon.spawners.reward.CustomDropService;
import com.plexon.spawners.reward.RewardRollPolicy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnerBreakListener implements Listener {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final EssenceService essence;
    private final CustomDropService customDrop;
    private final MessageService messages;
    private final WildStackerBridge wildStacker;
    private final Map<BlockKey, ConcurrentLinkedDeque<BreakContext>> pending = new ConcurrentHashMap<>();
    private final AtomicLong transactionSequence = new AtomicLong();

    public SpawnerBreakListener(
        final JavaPlugin plugin,
        final PluginSettings settings,
        final EssenceService essence,
        final CustomDropService customDrop,
        final MessageService messages,
        final WildStackerBridge wildStacker
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.essence = essence;
        this.customDrop = customDrop;
        this.messages = messages;
        this.wildStacker = wildStacker;
    }

    /**
     * Capture the physical intent before WildStacker mutates the block. WildStacker still owns
     * cancellation, stack mutation, placement, persistence and item identity; this path only
     * snapshots enough policy state to protect a transient/non-cached final 1x spawner.
     *
     * <p>Cancelled events are intentionally observed too. WildStacker cancels vanilla breaking as
     * part of its own authoritative pipeline. A captured intent never proves success: the next-tick
     * reconciliation or WildStacker events must still prove an actual state decrease before Plexon
     * pays anything.</p>
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPhysicalBreakIntent(final BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SPAWNER) return;
        if (!settings.breakingEnabled() || !settings.isWorldEnabled(event.getBlock().getWorld())) return;
        if (!(event.getBlock().getState() instanceof CreatureSpawner)) return;

        final StackedSpawner stacked = wildStacker.resolve(event.getBlock().getLocation());
        if (stacked == null) return;

        final int before = Math.max(1, stacked.getStackAmount());
        final Player player = event.getPlayer();
        final EntityType spawnedType = stacked.getSpawnedType();
        final SpawnerBreakPolicy.Decision decision = decide(player, spawnedType);

        final ItemStack oneUnitSnapshot = snapshotRecovery(stacked, 1);
        final BreakContext context = new BreakContext(
            BlockKey.of(event.getBlock().getLocation()),
            event.getBlock().getLocation().clone(),
            player,
            spawnedType,
            before,
            before == 1 ? 1 : 0,
            decision,
            oneUnitSnapshot,
            oneUnitSnapshot == null ? 0 : 1,
            transactionSequence.incrementAndGet());

        pending.computeIfAbsent(context.key(), ignored -> new ConcurrentLinkedDeque<>()).addLast(context);
        plugin.getServer().getScheduler().runTask(plugin, () -> finalizeFallback(context));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnstack(final SpawnerUnstackEvent event) {
        if (wildStacker.isWithdrawalInProgress()) return;
        if (event.isAsynchronous()) return;
        if (!(event.getUnstackSource() instanceof Player player)) return;
        if (!settings.breakingEnabled() || !settings.isWorldEnabled(event.getSpawner().getWorld())) return;

        final int amount = event.getAmount();
        final int before = event.getSpawner().getStackAmount();
        if (amount < 1 || amount > before) return;

        final BlockKey key = BlockKey.of(event.getSpawner().getLocation());
        BreakContext context = findMatching(key, player.getUniqueId());
        final ItemStack authoritativeSnapshot = snapshotRecovery(event.getSpawner(), amount);

        if (context == null) {
            context = new BreakContext(
                key,
                event.getSpawner().getLocation().clone(),
                player,
                event.getSpawner().getSpawnedType(),
                before,
                amount,
                decide(player, event.getSpawner().getSpawnedType()),
                authoritativeSnapshot,
                authoritativeSnapshot == null ? 0 : amount,
                transactionSequence.incrementAndGet());
            pending.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>()).addLast(context);
            final BreakContext scheduled = context;
            plugin.getServer().getScheduler().runTask(plugin, () -> finalizeFallback(scheduled));
            return;
        }

        context.observeUnstack(before, amount, authoritativeSnapshot);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(final SpawnerDropEvent event) {
        final Player player = event.getPlayer();
        if (player == null) return;

        final BreakContext context = findMatching(BlockKey.of(event.getSpawner().getLocation()), player.getUniqueId());
        if (context == null || context.amount() < 1) return;
        if (!context.completion().complete(successOutcome(context.decision()))) return;

        remove(context);

        if (context.decision().recoverSpawner()) {
            ItemStack authoritative = context.recoveryItemFor(context.amount());
            if (authoritative == null || authoritative.getType().isAir()) {
                authoritative = snapshotRecovery(event.getSpawner(), context.amount());
            }
            if (authoritative != null && !authoritative.getType().isAir()) {
                event.setItemStack(authoritative);
                sendRecovered(context);
            }
        } else {
            event.setItemStack(new ItemStack(Material.AIR));
        }

        awardConfiguredRewards(context);
    }

    private void finalizeFallback(final BreakContext context) {
        if (context.completion().isComplete()) {
            remove(context);
            return;
        }

        final StackedSpawner currentSpawner = wildStacker.resolve(context.location());
        final boolean blockStillSpawner = context.location().getBlock().getType() == Material.SPAWNER;
        final Integer currentAmount = currentSpawner == null ? null : Math.max(0, currentSpawner.getStackAmount());

        final int removed = BreakReconciliation.confirmedRemovedAmount(
            context.beforeAmount(),
            context.amount(),
            blockStillSpawner,
            currentAmount);

        if (removed < 1) {
            if (context.completion().complete(BreakCompletionGate.Outcome.DENIED_OR_UNCHANGED)) {
                remove(context);
            }
            return;
        }

        context.confirmFallbackAmount(removed);
        final ItemStack fallbackRecovery = context.decision().recoverSpawner()
            ? context.recoveryItemFor(context.amount())
            : null;
        if (context.decision().recoverSpawner()
            && (fallbackRecovery == null || fallbackRecovery.getType().isAir())) {
            if (context.completion().complete(BreakCompletionGate.Outcome.ABORTED)) {
                remove(context);
                plugin.getLogger().severe("Could not finalize authoritative WildStacker recovery for break transaction "
                    + context.sequence() + " at " + context.key() + "; no compatible pre-removal item snapshot exists.");
            }
            return;
        }

        if (!context.completion().complete(successOutcome(context.decision()))) return;
        remove(context);

        if (fallbackRecovery != null) {
            deliverSpawnerItem(context.player(), context.location(), fallbackRecovery);
            sendRecovered(context);
        }

        awardConfiguredRewards(context);
    }

    private SpawnerBreakPolicy.Decision decide(final Player player, final EntityType spawnedType) {
        final int silkLevel = player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.SILK_TOUCH);
        return SpawnerBreakPolicy.decide(
            player.getGameMode() == GameMode.CREATIVE,
            settings.creativeRecoverSpawner(),
            settings.creativeAwardEssence(),
            settings.creativeAwardCustomItem(),
            silkLevel,
            settings.requiredSilkTouchLevel(),
            settings.silkBypassPermissionEnabled(),
            player.hasPermission("plexonspawners.bypass.silk"),
            settings.nonSilkRewardMode(spawnedType),
            settings.essenceEnabled(),
            settings.customDropEnabled());
    }

    private ItemStack snapshotRecovery(final StackedSpawner stackedSpawner, final int amount) {
        final ItemStack authoritative = wildStacker.createSpawnerItem(stackedSpawner, amount);
        return authoritative == null ? null : authoritative.clone();
    }

    private static BreakCompletionGate.Outcome successOutcome(final SpawnerBreakPolicy.Decision decision) {
        if (decision.recoverSpawner()) return BreakCompletionGate.Outcome.RECOVERED;
        if (decision.awardEssence() || decision.awardCustomItem()) return BreakCompletionGate.Outcome.REWARDED;
        return BreakCompletionGate.Outcome.NO_REWARD_POLICY;
    }

    private void awardConfiguredRewards(final BreakContext context) {
        if (context.decision().awardEssence()) awardEssence(context);
        if (context.decision().awardCustomItem()) awardCustom(context);
    }

    private void awardEssence(final BreakContext context) {
        final RewardRollPolicy.Award award = essence.evaluate(context.spawnedType(), context.amount());
        if (!award.awarded()) return;
        essence.deliver(context.player(), context.location(), award.totalAmount());
        if (settings.essenceAwardedMessage()) {
            messages.send(context.player(), "essence-awarded", Map.of(
                "amount", Long.toString(award.totalAmount()),
                "mob", pretty(context.spawnedType().name())));
        }
    }

    private void awardCustom(final BreakContext context) {
        final RewardRollPolicy.Award award = customDrop.evaluate(context.spawnedType(), context.amount());
        if (!award.awarded()) return;
        customDrop.deliver(context.player(), context.location(), award.totalAmount());
        if (settings.customDropAwardedMessage()) {
            messages.send(context.player(), "custom-drop-awarded", Map.of(
                "amount", Long.toString(award.totalAmount()),
                "mob", pretty(context.spawnedType().name())));
        }
    }

    private void sendRecovered(final BreakContext context) {
        if (!settings.silkRecoveredMessage()) return;
        messages.send(context.player(), "spawner-recovered", Map.of(
            "amount", Integer.toString(context.amount()),
            "mob", pretty(context.spawnedType().name())));
    }

    private void deliverSpawnerItem(final Player player, final Location location, final ItemStack item) {
        final Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (final ItemStack extra : overflow.values()) location.getWorld().dropItemNaturally(location, extra);
    }

    private BreakContext findMatching(final BlockKey key, final UUID playerId) {
        final ConcurrentLinkedDeque<BreakContext> queue = pending.get(key);
        if (queue == null) return null;
        for (final BreakContext context : queue) {
            if (context.player().getUniqueId().equals(playerId) && !context.completion().isComplete()) {
                return context;
            }
        }
        return null;
    }

    private void remove(final BreakContext context) {
        final ConcurrentLinkedDeque<BreakContext> queue = pending.get(context.key());
        if (queue == null) return;
        queue.remove(context);
        if (queue.isEmpty()) pending.remove(context.key(), queue);
    }

    private static String pretty(final String raw) {
        final String normalized = raw.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return normalized.isEmpty() ? raw : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static final class BreakContext {
        private final BlockKey key;
        private final Location location;
        private final Player player;
        private final EntityType spawnedType;
        private final SpawnerBreakPolicy.Decision decision;
        private final BreakCompletionGate completion = new BreakCompletionGate();
        private final long sequence;
        private int beforeAmount;
        private int amount;
        private ItemStack recoveryItem;
        private int recoverySnapshotAmount;

        private BreakContext(
            final BlockKey key,
            final Location location,
            final Player player,
            final EntityType spawnedType,
            final int beforeAmount,
            final int amount,
            final SpawnerBreakPolicy.Decision decision,
            final ItemStack recoveryItem,
            final int recoverySnapshotAmount,
            final long sequence
        ) {
            this.key = key;
            this.location = location;
            this.player = player;
            this.spawnedType = spawnedType;
            this.beforeAmount = beforeAmount;
            this.amount = amount;
            this.decision = decision;
            this.recoveryItem = recoveryItem == null ? null : recoveryItem.clone();
            this.recoverySnapshotAmount = recoveryItem == null ? 0 : recoverySnapshotAmount;
            this.sequence = sequence;
        }

        private void observeUnstack(final int beforeAmount, final int amount, final ItemStack recoveryItem) {
            this.beforeAmount = beforeAmount;
            this.amount = amount;
            if (recoveryItem != null && !recoveryItem.getType().isAir()) {
                this.recoveryItem = recoveryItem.clone();
                this.recoverySnapshotAmount = amount;
            }
        }

        private void confirmFallbackAmount(final int amount) {
            if (this.amount < 1) this.amount = amount;
        }

        private BlockKey key() { return key; }
        private Location location() { return location; }
        private Player player() { return player; }
        private EntityType spawnedType() { return spawnedType; }
        private int beforeAmount() { return beforeAmount; }
        private int amount() { return amount; }
        private SpawnerBreakPolicy.Decision decision() { return decision; }
        private BreakCompletionGate completion() { return completion; }
        private long sequence() { return sequence; }

        private ItemStack recoveryItemFor(final int amount) {
            if (recoveryItem == null || recoverySnapshotAmount != amount) return null;
            return recoveryItem.clone();
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(final Location location) {
            return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
