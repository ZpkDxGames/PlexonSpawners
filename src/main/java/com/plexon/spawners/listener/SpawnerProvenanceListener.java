package com.plexon.spawners.listener;

import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.SpawnerOriginService;
import com.plexon.spawners.managed.SpawnerTier;
import com.plexon.spawners.managed.SpawnerTuning;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class SpawnerProvenanceListener implements Listener {
    private final ManagedSpawnerRegistry registry;
    private final SpawnerTuning tuning;
    private final SpawnerOriginService originService;

    public SpawnerProvenanceListener(
        final ManagedSpawnerRegistry registry,
        final SpawnerTuning tuning,
        final SpawnerOriginService originService
    ) {
        this.registry = registry;
        this.tuning = tuning;
        this.originService = originService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerSpawn(final CreatureSpawnEvent event) {
        if (!tuning.enabled() || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) {
            return;
        }

        final Location location = event.getLocation();
        final ManagedSpawner source = registry.nearest(
            location,
            event.getEntityType(),
            tuning.provenanceSearchRadius()
        );
        if (source == null) {
            return;
        }

        final SpawnerTier tier = tuning.tier(source.tier());
        final double dx = location.getX() - (source.x() + 0.5D);
        final double dy = location.getY() - (source.y() + 0.5D);
        final double dz = location.getZ() - (source.z() + 0.5D);
        final double allowed = tier.spawnRange() + 2.0D;
        if (dx * dx + dy * dy + dz * dz > allowed * allowed) {
            return;
        }

        originService.mark(event.getEntity(), source.id());
        registry.incrementSpawnCount(source.id());
    }
}
