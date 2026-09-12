package com.plexon.spawners.listener;

import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.NativeStackSettings;
import com.plexon.spawners.config.NearbyStackCapSettings;
import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.NativeStackPolicy;
import com.plexon.spawners.managed.NearbyStackCapPolicy;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

/** Native Plexon stack scaling plus exact nearby logical-population guard. */
@SuppressWarnings("deprecation")
public final class NearbyStackCapListener implements Listener, WildStackerCompat.SpawnGuard {
    private final ManagedSpawnerRegistry registry;
    private final NearbyStackCapSettings capSettings;
    private final NativeStackSettings stackSettings;
    private final WildStackerCompat wildStacker;
    private final PerformanceCounters counters;
    private CycleContext activeCycle;

    public NearbyStackCapListener(
        final ManagedSpawnerRegistry registry,
        final NearbyStackCapSettings capSettings,
        final NativeStackSettings stackSettings,
        final WildStackerCompat wildStacker,
        final PerformanceCounters counters
    ) {
        this.registry = registry;
        this.capSettings = capSettings;
        this.stackSettings = stackSettings;
        this.wildStacker = wildStacker;
        this.counters = counters;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreSpawnerSpawn(final PreSpawnerSpawnEvent event) {
        final ManagedSpawner managed = registry.find(event.getSpawnerLocation());
        if (managed == null || managed.type() != event.getType()) {
            return;
        }
        if (managed.migrationState().blocksMutation()) {
            failClosed(event);
            return;
        }
        if (!capSettings.enabled() || !stackSettings.respectNearbyLogicalCap()) {
            return;
        }
        counters.nearbyStackCapCheck();
        final CountResult count = countNearby(event.getSpawnerLocation(), managed.type());
        if (!count.available) {
            failClosed(event);
            return;
        }
        if (count.logicalAmount >= capSettings.maximumAmount()) {
            block(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawnerSpawn(final SpawnerSpawnEvent event) {
        final CreatureSpawner spawner = event.getSpawner();
        if (spawner == null) {
            return;
        }
        final ManagedSpawner managed = registry.find(spawner.getLocation());
        if (managed == null || managed.type() != event.getEntityType()) {
            return;
        }
        if (managed.migrationState().blocksMutation()) {
            cancelSpawnerSpawn(event, true);
            return;
        }

        CycleContext cycle = currentCycle(spawner.getLocation(), managed.type());
        if (cycle == null) {
            cycle = createCycle(spawner, managed);
            if (cycle == null) {
                cancelSpawnerSpawn(event, true);
                return;
            }
            activeCycle = cycle;
        }
        if (cycle.remainingLogical <= 0) {
            cancelSpawnerSpawn(event, false);
            return;
        }

        final int perPhysicalContribution = stackSettings.scaleWithStack() ? managed.stackAmount() : 1;
        int desired = Math.min(perPhysicalContribution, cycle.remainingLogical);
        if (capSettings.enabled() && stackSettings.respectNearbyLogicalCap()) {
            desired = Math.min(desired, cycle.remainingNearby);
        }
        if (desired <= 0) {
            cancelSpawnerSpawn(event, false);
            return;
        }

        if (wildStacker.installed()) {
            if (!(event.getEntity() instanceof LivingEntity living)) {
                cancelSpawnerSpawn(event, true);
                return;
            }
            final WildStackerCompat.Amount pending = wildStacker.getLogicalEntityAmount(living);
            if (pending.result() != WildStackerCompat.Result.SUCCESS) {
                cancelSpawnerSpawn(event, true);
                return;
            }
            counters.nearbyStackCapWildStackerLookup();
            if (pending.amount() != desired) {
                final WildStackerCompat.Result resized = wildStacker.resizeLogicalEntity(living, desired);
                if (resized != WildStackerCompat.Result.SUCCESS) {
                    cancelSpawnerSpawn(event, true);
                    return;
                }
            }
            consume(cycle, desired);
            return;
        }

        final int remainingPhysicalBudget = Math.max(0, stackSettings.vanillaPhysicalOutputCap() - cycle.vanillaPhysicalSpawned);
        if (remainingPhysicalBudget <= 0) {
            cancelSpawnerSpawn(event, false);
            return;
        }
        desired = Math.min(desired, remainingPhysicalBudget);
        consume(cycle, desired);
        cycle.vanillaPhysicalSpawned += desired;

        final int extra = desired - 1;
        if (extra > 0) {
            final Location spawnLocation = event.getEntity().getLocation();
            final World world = spawnLocation.getWorld();
            for (int index = 0; index < extra; index++) {
                // Preserve first-party SPAWNER provenance for the additional physical
                // entities representing this logical native-stack contribution.
                world.spawnEntity(spawnLocation, managed.type(), CreatureSpawnEvent.SpawnReason.SPAWNER);
            }
        }
    }

    /** Called reflectively from WildStacker's SpawnerStackedEntitySpawnEvent. */
    @Override
    public boolean shouldUseStackedSpawn(final CreatureSpawner spawner) {
        if (!Bukkit.isPrimaryThread()) {
            activeCycle = null;
            counters.nearbyStackCapFailClosed();
            return false;
        }
        final ManagedSpawner managed = registry.find(spawner.getLocation());
        if (managed == null || managed.type() != spawner.getSpawnedType()) {
            activeCycle = null;
            return true;
        }
        if (managed.migrationState().blocksMutation()) {
            activeCycle = null;
            counters.nearbyStackCapFailClosed();
            return false;
        }
        final CycleContext cycle = createCycle(spawner, managed);
        activeCycle = cycle;
        if (cycle == null || cycle.remainingLogical <= 0 || cycle.remainingNearby <= 0) {
            counters.nearbyStackCapBlocked();
            return false;
        }
        return true;
    }

    /** Prevent direct in-place entity growth so the pending entity can be resized to the exact native contribution. */
    @Override
    public boolean shouldCancelEntityStack(final LivingEntity existingTarget) {
        final CycleContext cycle = activeCycle;
        if (cycle == null || cycle.expired(existingTarget.getWorld())) {
            activeCycle = null;
            return false;
        }
        return cycle.matchesEntity(existingTarget);
    }

    private CycleContext createCycle(final CreatureSpawner spawner, final ManagedSpawner managed) {
        counters.nearbyStackCapCheck();
        final int requested = NativeStackPolicy.requestedLogicalOutput(
            Math.max(1, spawner.getSpawnCount()), managed.stackAmount(),
            stackSettings.maxLogicalOutputPerCycle(), stackSettings.scaleWithStack());
        int nearbyRemaining = Integer.MAX_VALUE;
        if (capSettings.enabled() && stackSettings.respectNearbyLogicalCap()) {
            final CountResult count = countNearby(spawner.getLocation(), managed.type());
            if (!count.available) {
                counters.nearbyStackCapFailClosed();
                return null;
            }
            nearbyRemaining = NearbyStackCapPolicy.remainingCapacity(count.logicalAmount, capSettings.maximumAmount());
        }
        final int allowed = Math.min(requested, nearbyRemaining);
        return CycleContext.create(spawner.getLocation(), managed.type(), capSettings.radius(), allowed, nearbyRemaining);
    }

    private void consume(final CycleContext cycle, final int amount) {
        cycle.remainingLogical = Math.max(0, cycle.remainingLogical - amount);
        if (cycle.remainingNearby != Integer.MAX_VALUE) {
            cycle.remainingNearby = Math.max(0, cycle.remainingNearby - amount);
        }
    }

    private CountResult countNearby(final Location spawnerLocation, final EntityType type) {
        final World world = spawnerLocation.getWorld();
        if (world == null) {
            return CountResult.unavailable();
        }
        final double radius = capSettings.radius();
        final Location center = spawnerLocation.clone().add(0.5D, 0.5D, 0.5D);
        final Collection<Entity> nearby = world.getNearbyEntities(center, radius, radius, radius);
        int logicalAmount = 0;
        for (final Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!NearbyStackCapPolicy.contributes(capSettings.sameTypeOnly(), living.getType() == type)) {
                continue;
            }
            final WildStackerCompat.Amount amount = wildStacker.getLogicalEntityAmount(living);
            if (amount.result() == WildStackerCompat.Result.UNAVAILABLE
                || amount.result() == WildStackerCompat.Result.CANCELLED) {
                return CountResult.unavailable();
            }
            if (amount.result() == WildStackerCompat.Result.SUCCESS) {
                counters.nearbyStackCapWildStackerLookup();
            }
            final int contribution = Math.max(1, amount.amount());
            logicalAmount = NearbyStackCapPolicy.accumulateLogicalAmount(
                logicalAmount, contribution, capSettings.maximumAmount());
            counters.nearbyStackCapLogicalEntitiesCounted(contribution);
            if (logicalAmount >= capSettings.maximumAmount()) {
                break;
            }
        }
        return new CountResult(true, logicalAmount);
    }

    private CycleContext currentCycle(final Location spawnerLocation, final EntityType type) {
        final CycleContext cycle = activeCycle;
        if (cycle == null) {
            return null;
        }
        if (cycle.expired(spawnerLocation.getWorld())) {
            activeCycle = null;
            return null;
        }
        return cycle.matchesSpawner(spawnerLocation, type) ? cycle : null;
    }

    private void block(final PreSpawnerSpawnEvent event) {
        counters.nearbyStackCapBlocked();
        event.setCancelled(true);
        event.setShouldAbortSpawn(true);
    }

    private void failClosed(final PreSpawnerSpawnEvent event) {
        counters.nearbyStackCapFailClosed();
        block(event);
    }

    private void cancelSpawnerSpawn(final SpawnerSpawnEvent event, final boolean failClosed) {
        if (failClosed) {
            counters.nearbyStackCapFailClosed();
        }
        counters.nearbyStackCapBlocked();
        event.setCancelled(true);
    }

    private record CountResult(boolean available, int logicalAmount) {
        private static CountResult unavailable() { return new CountResult(false, 0); }
    }

    private static final class CycleContext {
        private final UUID worldId;
        private final long worldTime;
        private final int x;
        private final int y;
        private final int z;
        private final EntityType type;
        private final double radius;
        private int remainingLogical;
        private int remainingNearby;
        private int vanillaPhysicalSpawned;

        private CycleContext(
            final UUID worldId, final long worldTime,
            final int x, final int y, final int z,
            final EntityType type, final double radius,
            final int remainingLogical, final int remainingNearby
        ) {
            this.worldId = worldId;
            this.worldTime = worldTime;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.radius = radius;
            this.remainingLogical = remainingLogical;
            this.remainingNearby = remainingNearby;
        }

        private static CycleContext create(
            final Location location, final EntityType type, final double radius,
            final int remainingLogical, final int remainingNearby
        ) {
            final World world = location.getWorld();
            return new CycleContext(world.getUID(), world.getFullTime(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), type, radius,
                remainingLogical, remainingNearby);
        }

        private boolean expired(final World world) {
            return world == null || !worldId.equals(world.getUID()) || worldTime != world.getFullTime();
        }

        private boolean matchesSpawner(final Location location, final EntityType candidateType) {
            final World world = location.getWorld();
            return world != null && worldId.equals(world.getUID())
                && x == location.getBlockX() && y == location.getBlockY() && z == location.getBlockZ()
                && type == candidateType;
        }

        private boolean matchesEntity(final LivingEntity entity) {
            if (entity.getType() != type || !worldId.equals(entity.getWorld().getUID())) {
                return false;
            }
            final Location location = entity.getLocation();
            final double dx = location.getX() - (x + 0.5D);
            final double dy = location.getY() - (y + 0.5D);
            final double dz = location.getZ() - (z + 0.5D);
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
    }
}
