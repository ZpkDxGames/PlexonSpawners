package com.plexon.spawners.listener;

import com.bgsoftware.wildstacker.api.events.SpawnerDropEvent;
import com.bgsoftware.wildstacker.api.events.SpawnerUnstackEvent;
import com.bgsoftware.wildstacker.api.objects.StackedSpawner;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnstack(final SpawnerUnstackEvent event) {
        if (wildStacker.isWithdrawalInProgress()) return;
        if (event.isAsynchronous()) return;
        if (!(event.getUnstackSource() instanceof Player player)) return;
        if (!settings.breakingEnabled() || !settings.isWorldEnabled(event.getSpawner().getWorld())) return;

        final int amount = event.getAmount();
        final int before = event.getSpawner().getStackAmount();
        if (amount < 1 || amount > before) return;

        final int silkLevel = player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.SILK_TOUCH);
        final SpawnerBreakPolicy.Decision decision = SpawnerBreakPolicy.decide(
            player.getGameMode() == GameMode.CREATIVE,
            settings.creativeRecoverSpawner(),
            settings.creativeAwardEssence(),
            settings.creativeAwardCustomItem(),
            silkLevel,
            settings.requiredSilkTouchLevel(),
            settings.silkBypassPermissionEnabled(),
            player.hasPermission("plexonspawners.bypass.silk"),
            settings.nonSilkRewardMode(event.getSpawner().getSpawnedType()),
            settings.essenceEnabled(),
            settings.customDropEnabled());

        final BreakContext context = new BreakContext(
            BlockKey.of(event.getSpawner().getLocation()), event.getSpawner(), player, before, amount, decision);
        pending.computeIfAbsent(context.key(), ignored -> new ConcurrentLinkedDeque<>()).addLast(context);
        plugin.getServer().getScheduler().runTask(plugin, () -> finalizeFallback(context));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(final SpawnerDropEvent event) {
        final Player player = event.getPlayer();
        if (player == null) return;
        final BreakContext context = takeMatching(BlockKey.of(event.getSpawner().getLocation()), player.getUniqueId());
        if (context == null || !context.completed().compareAndSet(false, true)) return;

        if (context.decision().recoverSpawner()) {
            final ItemStack authoritative = wildStacker.createSpawnerItem(event.getSpawner(), context.amount());
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
        if (!context.completed().compareAndSet(false, true)) return;
        remove(context);

        // SpawnerUnstackEvent is emitted before WildStacker mutates/removes the stack. Re-resolve
        // the location on the next tick so validation reflects WildStacker's authoritative state,
        // including the final-spawner case where the block/cache entry no longer exists.
        final int expectedAfter = Math.max(0, context.beforeAmount() - context.amount());
        final StackedSpawner currentSpawner = wildStacker.resolve(context.location());
        final int current = currentSpawner == null ? 0 : Math.max(0, currentSpawner.getStackAmount());
        if (current > expectedAfter) return;

        if (context.decision().recoverSpawner()) {
            final ItemStack authoritative = wildStacker.createSpawnerItem(context.stackedSpawner(), context.amount());
            if (authoritative != null && !authoritative.getType().isAir()) {
                deliverSpawnerItem(context.player(), context.location(), authoritative);
                sendRecovered(context);
            }
        }
        awardConfiguredRewards(context);
    }

    private void awardConfiguredRewards(final BreakContext context) {
        if (context.decision().awardEssence()) awardEssence(context);
        if (context.decision().awardCustomItem()) awardCustom(context);
    }

    private void awardEssence(final BreakContext context) {
        final RewardRollPolicy.Award award = essence.evaluate(context.stackedSpawner().getSpawnedType(), context.amount());
        if (!award.awarded()) return;
        essence.deliver(context.player(), context.location(), award.totalAmount());
        if (settings.essenceAwardedMessage()) {
            messages.send(context.player(), "essence-awarded", Map.of(
                "amount", Long.toString(award.totalAmount()),
                "mob", pretty(context.stackedSpawner().getSpawnedType().name())));
        }
    }

    private void awardCustom(final BreakContext context) {
        final RewardRollPolicy.Award award = customDrop.evaluate(context.stackedSpawner().getSpawnedType(), context.amount());
        if (!award.awarded()) return;
        customDrop.deliver(context.player(), context.location(), award.totalAmount());
        if (settings.customDropAwardedMessage()) {
            messages.send(context.player(), "custom-drop-awarded", Map.of(
                "amount", Long.toString(award.totalAmount()),
                "mob", pretty(context.stackedSpawner().getSpawnedType().name())));
        }
    }

    private void sendRecovered(final BreakContext context) {
        if (!settings.silkRecoveredMessage()) return;
        messages.send(context.player(), "spawner-recovered", Map.of(
            "amount", Integer.toString(context.amount()),
            "mob", pretty(context.stackedSpawner().getSpawnedType().name())));
    }

    private void deliverSpawnerItem(final Player player, final Location location, final ItemStack item) {
        final Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (final ItemStack extra : overflow.values()) location.getWorld().dropItemNaturally(location, extra);
    }

    private BreakContext takeMatching(final BlockKey key, final UUID playerId) {
        final ConcurrentLinkedDeque<BreakContext> queue = pending.get(key);
        if (queue == null) return null;
        for (final BreakContext context : queue) {
            if (context.player().getUniqueId().equals(playerId) && queue.remove(context)) {
                if (queue.isEmpty()) pending.remove(key, queue);
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

    private record BreakContext(
        BlockKey key,
        StackedSpawner stackedSpawner,
        Player player,
        int beforeAmount,
        int amount,
        SpawnerBreakPolicy.Decision decision,
        AtomicBoolean completed
    ) {
        private BreakContext(
            final BlockKey key,
            final StackedSpawner stackedSpawner,
            final Player player,
            final int beforeAmount,
            final int amount,
            final SpawnerBreakPolicy.Decision decision
        ) {
            this(key, stackedSpawner, player, beforeAmount, amount, decision, new AtomicBoolean(false));
        }

        private Location location() {
            return stackedSpawner.getLocation();
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(final Location location) {
            return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
