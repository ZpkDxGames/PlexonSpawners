package com.plexon.spawners.listener;

import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import com.plexon.spawners.compat.EntityStackBackend;
import com.plexon.spawners.compat.PhysicalFallbackBackend;
import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.NativeStackSettings;
import com.plexon.spawners.config.NearbyStackCapSettings;
import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.managed.ManagedSpawnAggregationService;
import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.NativeStackPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/** Native Plexon stack scaling, direct entity aggregation and nearby logical-population guard. */
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

        final boolean providerRequested = stackSettings.entityAggregationBackend()
            == NativeStackSettings.EntityAggregationBackend.AUTO;
        final boolean providerReady = providerRequested && wildStacker.methodCacheReady();
        if (providerReady && event.getEntity() instanceof LivingEntity living) {
            counters.nearbyStackCapWildStackerLookup();

            if (stackSettings.entityAggregationEnabled() && stackSettings.preferExistingEntityStack()) {
                final LivingEntity target = chooseCompatibleTarget(living, desired);
                if (target != null) {
                    final EntityStackBackend.MutationResult merged = wildStacker.mergeInto(living, target, desired);
                    if (merged == EntityStackBackend.MutationResult.SUCCESS) {
                        consume(cycle, desired);
                        // The contribution now lives in the existing target. Cancelling the
                        // pending spawn prevents a second physical representation.
                        event.setCancelled(true);
                        return;
                    }
                    if (merged == EntityStackBackend.MutationResult.UNAVAILABLE) {
                        counters.wildStackerDegraded();
                    }
                }
            }

            final WildStackerCompat.Result resized = wildStacker.resizeLogicalEntity(living, desired);
            if (resized == WildStackerCompat.Result.SUCCESS) {
                consume(cycle, desired);
                return;
            }
            counters.wildStackerDegraded();
            // Provider failure must not silently delete the cycle. Fall through to
            // the bounded physical representation below.
        }

        admitPhysicalFallback(event, managed, cycle, desired);
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

    /**
     * 3.4 deliberately does not blanket-cancel WildStacker's EntityStackEvent.
     * Plexon owns the exact contribution/cap; the entity backend may then perform
     * its normal compatible entity lifecycle without regressing stack-interval: 0.
     */
    @Override
    public boolean shouldCancelEntityStack(final LivingEntity existingTarget) {
        final CycleContext cycle = activeCycle;
        if (cycle != null && cycle.expired(existingTarget.getWorld())) {
            activeCycle = null;
        }
        return false;
    }

    private CycleContext createCycle(final CreatureSpawner spawner, final ManagedSpawner managed) {
        counters.nearbyStackCapCheck();
        final int requested = NativeStackPolicy.requestedLogicalOutput(
            Math.max(1, spawner.getSpawnCount()), managed.stackAmount(),
            stackSettings.maxLogicalOutputPerCycle(), stackSettings.scaleWithStack());

        int nearbyAmount = 0;
        if (capSettings.enabled() && stackSettings.respectNearbyLogicalCap()) {
            nearbyAmount = countNearby(spawner.getLocation(), managed.type()).logicalAmount;
        }
        final ManagedSpawnAggregationService.CyclePlan plan = ManagedSpawnAggregationService.plan(
            requested,
            nearbyAmount,
            capSettings.maximumAmount(),
            capSettings.enabled() && stackSettings.respectNearbyLogicalCap());
        return CycleContext.create(
            spawner.getLocation(), managed.type(), stackSettings.entityAggregationRadius(),
            plan.allowedContribution(), plan.nearbyRemainingCapacity());
    }

    private LivingEntity chooseCompatibleTarget(final LivingEntity source, final int contribution) {
        final Location center = source.getLocation();
        final World world = center.getWorld();
        if (world == null) {
            return null;
        }
        final double radius = stackSettings.entityAggregationRadius();
        final Collection<Entity> nearby = world.getNearbyEntities(center, radius, radius, radius);
        final List<ManagedSpawnAggregationService.Candidate> candidates = new ArrayList<>();
        final Map<UUID, LivingEntity> byId = new HashMap<>();

        for (final Entity entity : nearby) {
            if (!(entity instanceof LivingEntity target) || target == source || target.getType() != source.getType()) {
                continue;
            }
            final EntityStackBackend.LogicalAmount amount = wildStacker.logicalAmount(target);
            if (!amount.available()) {
                continue;
            }
            final boolean compatible = wildStacker.compatible(source, target, contribution);
            final double distance = target.getLocation().distanceSquared(center);
            candidates.add(new ManagedSpawnAggregationService.Candidate(
                target.getUniqueId(), distance, amount.amount(), compatible));
            byId.put(target.getUniqueId(), target);
        }

        return ManagedSpawnAggregationService.chooseTarget(candidates)
            .map(candidate -> byId.get(candidate.id()))
            .orElse(null);
    }

    private void admitPhysicalFallback(
        final SpawnerSpawnEvent event,
        final ManagedSpawner managed,
        final CycleContext cycle,
        int desired
    ) {
        final int remainingPhysicalBudget = Math.max(
            0, stackSettings.vanillaPhysicalOutputCap() - cycle.vanillaPhysicalSpawned);
        if (remainingPhysicalBudget <= 0) {
            cancelSpawnerSpawn(event, false);
            return;
        }
        desired = Math.min(desired, remainingPhysicalBudget);
        if (desired <= 0) {
            cancelSpawnerSpawn(event, false);
            return;
        }

        consume(cycle, desired);
        cycle.vanillaPhysicalSpawned += desired;
        final int extra = desired - 1;
        if (extra <= 0) {
            return;
        }
        final Location spawnLocation = event.getEntity().getLocation();
        final World world = spawnLocation.getWorld();
        if (world == null) {
            return;
        }
        for (int index = 0; index < extra; index++) {
            world.spawnEntity(spawnLocation, managed.type(), CreatureSpawnEvent.SpawnReason.SPAWNER);
        }
    }

    private EntityStackBackend entityBackend() {
        if (stackSettings.entityAggregationBackend() == NativeStackSettings.EntityAggregationBackend.AUTO
            && wildStacker.methodCacheReady()) {
            return wildStacker;
        }
        return PhysicalFallbackBackend.INSTANCE;
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
            return new CountResult(0);
        }
        final double radius = capSettings.radius();
        final Location center = spawnerLocation.clone().add(0.5D, 0.5D, 0.5D);
        final Collection<Entity> nearby = world.getNearbyEntities(center, radius, radius, radius);
        final EntityStackBackend backend = entityBackend();
        int logicalAmount = 0;
        for (final Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (capSettings.sameTypeOnly() && living.getType() != type) {
                continue;
            }
            final EntityStackBackend.LogicalAmount amount = backend.logicalAmount(living);
            final int contribution;
            if (amount.available()) {
                contribution = Math.max(1, amount.amount());
                if (backend == wildStacker) {
                    counters.nearbyStackCapWildStackerLookup();
                }
            } else {
                contribution = 1;
                counters.wildStackerDegraded();
            }
            logicalAmount = saturatingAccumulate(logicalAmount, contribution, capSettings.maximumAmount());
            counters.nearbyStackCapLogicalEntitiesCounted(contribution);
            if (logicalAmount >= capSettings.maximumAmount()) {
                break;
            }
        }
        return new CountResult(logicalAmount);
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

    private static int saturatingAccumulate(final int current, final int contribution, final int ceiling) {
        if (current >= ceiling) {
            return ceiling;
        }
        final long total = (long) current + Math.max(1, contribution);
        return (int) Math.min((long) ceiling, total);
    }

    private record CountResult(int logicalAmount) {}

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
            final UUID worldId,
            final long worldTime,
            final int x,
            final int y,
            final int z,
            final EntityType type,
            final double radius,
            final int remainingLogical,
            final int remainingNearby
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
            final Location location,
            final EntityType type,
            final double radius,
            final int remainingLogical,
            final int remainingNearby
        ) {
            final World world = location.getWorld();
            if (world == null) {
                return null;
            }
            return new CycleContext(
                world.getUID(), world.getFullTime(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                type, radius, remainingLogical, remainingNearby);
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

        @SuppressWarnings("unused")
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
