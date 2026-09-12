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
        final ManagedSpawner record = ManagedSpawner.placed(
            event.getBlockPlaced().getWorld().getUID(),
            event.getBlockPlaced().getX(), event.getBlockPlaced().getY(), event.getBlockPlaced().getZ(),
            type, event.getPlayer().getUniqueId(), tier, tuning.defaultAccess());

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
        if (!(event.getBlockPlaced().getState() instanceof CreatureSpawner finalState)
            || !stateService.isManaged(finalState)) {
            return;
        }

        ManagedSpawner finalRecord = pending;
        final ManagedSpawner target = registry.findAutoStackTarget(event.getBlockPlaced().getLocation(), pending, stackSettings);
        if (target != null) {
            final NativeStackPolicy.MergeResult merge = NativeStackPolicy.merge(
                target.stackAmount(), 1, stackSettings.maxStackSize());
            if (merge.mergedAmount() == 1) {
                final ManagedSpawner updated = registry.updateStackAmount(target.id(), merge.targetAmount());
                if (updated != null && applyPhysical(updated)) {
                    event.getBlockPlaced().setType(Material.AIR, false);
                    finalRecord = updated;
                    redstoneLocks.refresh(updated);
                    displays.refresh(updated);
                } else if (updated != null) {
                    registry.updateStackAmount(target.id(), target.stackAmount());
                }
            }
        }

        if (finalRecord.id().equals(pending.id())) {
            registry.register(pending);
            redstoneLocks.refresh(pending);
            displays.refresh(pending);
        }
        consumeCreativeUnitIfConfigured(event);
        counters.managedPlacementSuccess();

        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("PlexonSpawners placement events must fire on the primary server thread");
        }
        final String transactionId = UUID.randomUUID().toString();
        Bukkit.getPluginManager().callEvent(new PlexonSpawnerPlacedEvent(
            event.getPlayer(), type, event.getBlockPlaced().getLocation(), event.getItemInHand(),
            transactionId + ":placed", transactionId));
    }

    private void consumeCreativeUnitIfConfigured(final BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE || !stackSettings.creativeConsumeOnPlace()) {
            return;
        }
        final ItemStack current = event.getItemInHand().clone();
        final int remaining = current.getAmount() - 1;
        final ItemStack replacement;
        if (remaining <= 0) {
            replacement = new ItemStack(Material.AIR);
        } else {
            current.setAmount(remaining);
            replacement = current;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.getPlayer().getInventory().setItemInOffHand(replacement);
        } else {
            event.getPlayer().getInventory().setItemInMainHand(replacement);
        }
    }

    private boolean applyPhysical(final ManagedSpawner record) {
        final org.bukkit.World world = Bukkit.getWorld(record.worldId());
        if (world == null || !world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            return false;
        }
        final Location location = new Location(world, record.x(), record.y(), record.z());
        return stateService.applyAt(location, record, tuning.tier(record.tier()));
    }

    private record PlacementKey(UUID worldId, int x, int y, int z) {
        private static PlacementKey of(final BlockPlaceEvent event) {
            return new PlacementKey(event.getBlockPlaced().getWorld().getUID(),
                event.getBlockPlaced().getX(), event.getBlockPlaced().getY(), event.getBlockPlaced().getZ());
        }
    }
}
