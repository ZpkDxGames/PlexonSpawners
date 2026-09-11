package com.plexon.spawners.listener;

import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.SpawnerStateService;
import com.plexon.spawners.managed.SpawnerTuning;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public final class SpawnerChunkListener implements Listener {
    private final ManagedSpawnerRegistry registry;
    private final SpawnerStateService stateService;
    private final SpawnerTuning tuning;

    public SpawnerChunkListener(
        final ManagedSpawnerRegistry registry,
        final SpawnerStateService stateService,
        final SpawnerTuning tuning
    ) {
        this.registry = registry;
        this.stateService = stateService;
        this.tuning = tuning;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(final ChunkLoadEvent event) {
        if (!tuning.enabled()) {
            return;
        }
        for (final ManagedSpawner record : registry.entriesInChunk(
            event.getWorld(), event.getChunk().getX(), event.getChunk().getZ())) {
            reconcile(record, event.getWorld());
        }
    }

    public void reconcileAlreadyLoaded() {
        if (!tuning.enabled()) {
            return;
        }
        for (final ManagedSpawner record : registry.snapshot()) {
            final World world = Bukkit.getWorld(record.worldId());
            if (world == null || !world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
                continue;
            }
            reconcile(record, world);
        }
    }

    private void reconcile(final ManagedSpawner record, final World world) {
        final Location location = new Location(world, record.x(), record.y(), record.z());
        if (location.getBlock().getType() != Material.SPAWNER
            || !(location.getBlock().getState() instanceof CreatureSpawner spawner)) {
            registry.remove(record.id());
            return;
        }

        if (!stateService.isManaged(spawner)) {
            registry.remove(record.id());
            return;
        }

        final ManagedSpawner physical = stateService.recover(spawner);
        if (!samePhysicalIdentity(record, physical)) {
            registry.remove(record.id());
            return;
        }

        stateService.apply(spawner, record, tuning.tier(record.tier()));
    }

    static boolean samePhysicalIdentity(final ManagedSpawner expected, final ManagedSpawner physical) {
        return physical != null
            && expected.id().equals(physical.id())
            && expected.worldId().equals(physical.worldId())
            && expected.x() == physical.x()
            && expected.y() == physical.y()
            && expected.z() == physical.z();
    }
}
