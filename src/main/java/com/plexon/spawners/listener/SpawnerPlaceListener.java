package com.plexon.spawners.listener;

import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.event.PlexonSpawnerPlacedEvent;
import com.plexon.spawners.item.SpawnerItemService;
import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.RedstoneSpawnerLockService;
import com.plexon.spawners.managed.SpawnerStateService;
import com.plexon.spawners.managed.SpawnerTuning;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class SpawnerPlaceListener implements Listener {
    private final SpawnerItemService spawnerItemService;
    private final SpawnerStateService stateService;
    private final ManagedSpawnerRegistry registry;
    private final SpawnerTuning tuning;
    private final RedstoneSpawnerLockService redstoneLocks;
    private final PerformanceCounters counters;
    private final Map<PlacementKey, ManagedSpawner> pendingPlacements = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SpawnerPlaceListener(
        final SpawnerItemService spawnerItemService,
        final SpawnerStateService stateService,
        final ManagedSpawnerRegistry registry,
        final SpawnerTuning tuning,
        final RedstoneSpawnerLockService redstoneLocks,
        final PerformanceCounters counters
    ) {
        this.spawnerItemService = spawnerItemService;
        this.stateService = stateService;
        this.registry = registry;
        this.tuning = tuning;
        this.redstoneLocks = redstoneLocks;
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

        if (registry.countInChunk(event.getBlockPlaced().getChunk()) >= tuning.maxManagedPerChunk()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(miniMessage.deserialize(
                "<!italic><#FF6B6B>This chunk has reached the managed spawner safety limit.</#FF6B6B>"
            ));
            return;
        }

        final int tier = Math.min(tuning.maxTier(), spawnerItemService.readSpawnerTier(event.getItemInHand()));
        final ManagedSpawner record = ManagedSpawner.placed(
            event.getBlockPlaced().getWorld().getUID(),
            event.getBlockPlaced().getX(),
            event.getBlockPlaced().getY(),
            event.getBlockPlaced().getZ(),
            type,
            event.getPlayer().getUniqueId(),
            tier,
            tuning.defaultAccess()
        );
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
        if (event.isCancelled()) {
            return;
        }

        final EntityType type = spawnerItemService.readSpawnerType(event.getItemInHand());
        if (type == null) {
            return;
        }

        if (pending != null) {
            if (!(event.getBlockPlaced().getState() instanceof CreatureSpawner finalState)
                || !stateService.isManaged(finalState)) {
                return;
            }
            registry.register(pending);
            redstoneLocks.refresh(pending);
        }
        counters.managedPlacementSuccess();

        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("PlexonSpawners placement events must fire on the primary server thread");
        }
        final String transactionId = UUID.randomUUID().toString();
        Bukkit.getPluginManager().callEvent(new PlexonSpawnerPlacedEvent(
            event.getPlayer(),
            type,
            event.getBlockPlaced().getLocation(),
            event.getItemInHand(),
            transactionId + ":placed",
            transactionId
        ));
    }

    private record PlacementKey(UUID worldId, int x, int y, int z) {
        private static PlacementKey of(final BlockPlaceEvent event) {
            return new PlacementKey(
                event.getBlockPlaced().getWorld().getUID(),
                event.getBlockPlaced().getX(),
                event.getBlockPlaced().getY(),
                event.getBlockPlaced().getZ()
            );
        }
    }
}
