package com.plexon.spawners.listener;

import com.plexon.spawners.config.NativeStackSettings;
import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.event.PlexonSpawnerPlacedEvent;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.NativeStackPolicy;
import com.plexon.spawners.managed.RedstoneSpawnerLockService;
import com.plexon.spawners.managed.SpawnerStackDisplayService;
import com.plexon.spawners.managed.SpawnerStateService;
import com.plexon.spawners.managed.SpawnerTuning;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class SpawnerPlaceListener implements Listener {
    private final SpawnerItemService spawnerItemService;
    private final SpawnerStateService stateService;
    private final ManagedSpawnerRegistry registry;
    private final SpawnerTuning tuning;
    private final NativeStackSettings stackSettings;
    private final RedstoneSpawnerLockService redstoneLocks;
    private final SpawnerStackDisplayService displays;
    private final PerformanceCounters counters;
    private final Map<PlacementKey, ManagedSpawner> pendingPlacements = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SpawnerPlaceListener(
        final SpawnerItemService spawnerItemService,
        final SpawnerStateService stateService,
        final ManagedSpawnerRegistry registry,
        final SpawnerTuning tuning,
        final NativeStackSettings stackSettings,
        final RedstoneSpawnerLockService redstoneLocks,
        final SpawnerStackDisplayService displays,
        final PerformanceCounters counters
    ) {
        this.spawnerItemService = spawnerItemService;
        this.stateService = stateService;
        this.registry = registry;
        this.tuning = tuning;
        this.stackSettings = stackSettings;
        this.redstoneLocks = redstoneLocks;
        this.displays = displays;
        this.counters = counters;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerPlaceApply(final BlockPlaceEvent event) {
        counters.blockPlaceSeen();
        if (event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }
        final EntityType type = spawnerItemService.readSpawnerType(event.getItemInHand());
        if (type == null) {
            counters.vanillaSpawnerPlacementReject();
            return;
        }
        if (!(event.getBlockPlaced().getState() instanceof CreatureSpawner spawner)) {
            return;
        }

        if (!tuning.enabled()) {
            spawner.setSpawnedType(type);
            if (!spawner.update(true, false)) {
                event.setCancelled(true);
            }
            return;
        }

        final int tier = Math.min(tuning.maxTier(), spawnerItemService.readSpawnerTier(event.getItemInHand()));
        final int incomingAmount = logicalPlacementAmount(event);
        final ManagedSpawner record = ManagedSpawner.placed(
            event.getBlockPlaced().getWorld().getUID(),
            event.getBlockPlaced().getX(), event.getBlockPlaced().getY(), event.getBlockPlaced().getZ(),
            type, event.getPlayer().getUniqueId(), tier, tuning.defaultAccess()
        ).withStackAmount(incomingAmount);

        final ManagedSpawner stackTarget = stackSettings.enabled()
            ? registry.findAutoStackTarget(event.getBlockPlaced().getLocation(), record, stackSettings)
            : null;
        if (stackTarget == null
            && registry.countInChunk(event.getBlockPlaced().getChunk()) >= tuning.maxManagedPerChunk()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(miniMessage.deserialize(
                "<!italic><#FF6B6B>This chunk has reached the managed spawner safety limit.</#FF6B6B>"));
            return;
        }

        if (!stateService.apply(spawner, record, tuning.tier(tier))) {
            event.setCancelled(true);
            return;
        }
        pendingPlacements.put(PlacementKey.of(event), record);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnerPlaceCommit(final BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }
        final ManagedSpawner pending = pendingPlacements.remove(PlacementKey.of(event));
        if (event.isCancelled() || pending == null) {
            return;
        }
        final EntityType type = spawnerItemService.readSpawnerType(event.getItemInHand());
        if (type == null) {
            return;
        }
        if (!(event.getBlockPlaced().getState() instanceof CreatureSpawner placedState)
            || !stateService.isManaged(placedState)) {
            return;
        }

        int consumedAmount = stackSettings.enabled() ? pending.stackAmount() : 1;
        Location successLocation = event.getBlockPlaced().getLocation();

        if (stackSettings.enabled()) {
            final ManagedSpawner target = registry.findAutoStackTarget(
                event.getBlockPlaced().getLocation(), pending, stackSettings);
            if (target != null) {
                final NativeStackPolicy.MergeResult merge = NativeStackPolicy.merge(
                    target.stackAmount(), pending.stackAmount(), stackSettings.maxStackSize());
                if (merge.mergedAmount() > 0) {
                    final ManagedSpawner updatedTarget = registry.updateStackAmount(target.id(), merge.targetAmount());
                    if (updatedTarget == null || !applyPhysical(updatedTarget)) {
                        if (updatedTarget != null) {
                            registry.updateStackAmount(target.id(), target.stackAmount());
                            applyPhysical(target);
                        }
                        handleMergeFailure(event, pending);
                        return;
                    }

                    redstoneLocks.refresh(updatedTarget);
                    displays.refresh(updatedTarget);
                    successLocation = locationOf(updatedTarget);

                    if (merge.remainder() == 0) {
                        event.getBlockPlaced().setType(Material.AIR, false);
                    } else if (hasRoomForNewPhysical(event)) {
                        final ManagedSpawner remainderRecord = pending.withStackAmount(merge.remainder());
                        if (!stateService.apply(placedState, remainderRecord, tuning.tier(remainderRecord.tier()))) {
                            event.getBlockPlaced().setType(Material.AIR, false);
                            consumedAmount = merge.mergedAmount();
                        } else {
                            registry.register(remainderRecord);
                            redstoneLocks.refresh(remainderRecord);
                            displays.refresh(remainderRecord);
                            successLocation = event.getBlockPlaced().getLocation();
                        }
                    } else {
                        // The compatible target accepted only part of the held amount and the
                        // placed chunk cannot own another physical managed spawner. Keep the
                        // unmerged remainder in the player's hand instead of deleting it.
                        event.getBlockPlaced().setType(Material.AIR, false);
                        consumedAmount = merge.mergedAmount();
                    }
                } else {
                    registerStandalone(pending);
                }
            } else {
                registerStandalone(pending);
            }

            adjustHeldAmount(event, consumedAmount);
        } else {
            registerStandalone(pending);
        }

        counters.managedPlacementSuccess();
        firePlacedEvent(event, type, successLocation);
    }

    private int logicalPlacementAmount(final BlockPlaceEvent event) {
        if (!stackSettings.enabled()) {
            return 1;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE && !stackSettings.creativeConsumeOnPlace()) {
            return 1;
        }
        return Math.max(1, Math.min(event.getItemInHand().getAmount(), stackSettings.maxStackSize()));
    }

    private void registerStandalone(final ManagedSpawner record) {
        registry.register(record);
        redstoneLocks.refresh(record);
        displays.refresh(record);
    }

    private void handleMergeFailure(final BlockPlaceEvent event, final ManagedSpawner pending) {
        if (hasRoomForNewPhysical(event)) {
            registerStandalone(pending);
            adjustHeldAmount(event, pending.stackAmount());
            counters.managedPlacementSuccess();
            firePlacedEvent(event, pending.type(), event.getBlockPlaced().getLocation());
            return;
        }

        event.getBlockPlaced().setType(Material.AIR, false);
        adjustHeldAmount(event, 0);
        event.getPlayer().sendMessage(miniMessage.deserialize(
            "<!italic><#FF6B6B>The target stack could not be updated safely; no spawner units were consumed.</#FF6B6B>"));
    }

    private boolean hasRoomForNewPhysical(final BlockPlaceEvent event) {
        return registry.countInChunk(event.getBlockPlaced().getChunk()) < tuning.maxManagedPerChunk();
    }

    /**
     * Bukkit/Paper restores the pre-use ItemStack before BlockPlaceEvent and applies
     * the vanilla one-item result only if plugins leave it untouched. Writing the
     * exact remainder here therefore makes multi-unit native placement atomic and
     * prevents the server from also subtracting a second unit after the event.
     */
    private void adjustHeldAmount(final BlockPlaceEvent event, final int consumedAmount) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE && !stackSettings.creativeConsumeOnPlace()) {
            return;
        }
        final ItemStack source = event.getItemInHand().clone();
        final int remaining = Math.max(0, source.getAmount() - Math.max(0, consumedAmount));
        final ItemStack replacement;
        if (remaining == 0) {
            replacement = new ItemStack(Material.AIR);
        } else {
            source.setAmount(remaining);
            replacement = source;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.getPlayer().getInventory().setItemInOffHand(replacement);
        } else {
            event.getPlayer().getInventory().setItemInMainHand(replacement);
        }
    }

    private void firePlacedEvent(final BlockPlaceEvent event, final EntityType type, final Location location) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("PlexonSpawners placement events must fire on the primary server thread");
        }
        final String transactionId = UUID.randomUUID().toString();
        Bukkit.getPluginManager().callEvent(new PlexonSpawnerPlacedEvent(
            event.getPlayer(), type, location, event.getItemInHand(),
            transactionId + ":placed", transactionId));
    }

    private boolean applyPhysical(final ManagedSpawner record) {
        final World world = Bukkit.getWorld(record.worldId());
        if (world == null || !world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            return false;
        }
        return stateService.applyAt(
            new Location(world, record.x(), record.y(), record.z()),
            record,
            tuning.tier(record.tier())
        );
    }

    private static Location locationOf(final ManagedSpawner record) {
        final World world = Bukkit.getWorld(record.worldId());
        return world == null
            ? null
            : new Location(world, record.x(), record.y(), record.z());
    }

    private record PlacementKey(UUID worldId, int x, int y, int z) {
        private static PlacementKey of(final BlockPlaceEvent event) {
            return new PlacementKey(event.getBlockPlaced().getWorld().getUID(),
                event.getBlockPlaced().getX(), event.getBlockPlaced().getY(), event.getBlockPlaced().getZ());
        }
    }
}
