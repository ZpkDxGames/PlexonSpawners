package com.plexon.spawners.listener;

import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import com.plexon.spawners.compat.WildStackerCompat;
import com.plexon.spawners.config.NearbyStackCapSettings;
import com.plexon.spawners.diagnostics.PerformanceCounters;
import com.plexon.spawners.managed.ManagedSpawner;
import com.plexon.spawners.managed.ManagedSpawnerRegistry;
import com.plexon.spawners.managed.NearbyStackCapPolicy;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;

/** Runtime-only guard that caps the logical nearby population of managed spawners. */
@SuppressWarnings("deprecation")
public final class NearbyStackCapListener implements Listener, WildStackerCompat.SpawnGuard {
    private final ManagedSpawnerRegistry registry;
    private final NearbyStackCapSettings settings;
    private final WildStackerCompat wildStacker;
    private final PerformanceCounters counters;
    private CycleContext activeCycle;

    public NearbyStackCapListener(
        final ManagedSpawnerRegistry registry,
        final NearbyStackCapSettings settings,
        final WildStackerCompat wildStacker,
        final PerformanceCounters counters
    ) {
        this.registry = registry;
        this.settings = settings;
        this.wildStacker = wildStacker;
        this.counters = counters;
    }

    /**
     * Paper's pre-spawn hook provides a unit-granular safety net. During a WildStacker
     * overridden cycle, the active context reserves exactly the remaining capacity.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreSpawnerSpawn(final PreSpawnerSpawnEvent event) {
        if (!enabled()) {
            return;
        }

        final Location spawnerLocation = event.getSpawnerLocation();
        final ManagedSpawner managed = registry.find(spawnerLocation);
        if (managed == null || !matchesSpawnType(managed.type(), event.getType())) {
            return;
        }

        counters.nearbyStackCapCheck();
        final CycleContext cycle = currentCycle(spawnerLocation, managed.type());
        if (cycle != null) {
            if (cycle.mode == NearbyStackCapPolicy.Decision.FAST_PATH) {
                return;
            }
            if (cycle.remainingCapacity > 0) {
                cycle.remainingCapacity--;
                return;
            }
            block(event);
            return;
        }

        final CountResult count = countNearby(spawnerLocation, managed.type());
        if (!count.available) {
            failClosed(event);
            return;
        }
        if (count.logicalAmount >= settings.maximumAmount()) {
            block(event);
            return;
        }

        /*
         * If WildStacker is present but this spawn did not enter through its overridden
         * cycle event, the public API exposes no pending contribution amount here. Reject
         * a whole at-risk cycle rather than knowingly permitting an overshoot.
         */
        if (wildStacker.installed()) {
            final CreatureSpawner spawner = spawnerAt(spawnerLocation);
            if (spawner == null || spawner.getSpawnedType() != managed.type()) {
                failClosed(event);
                return;
            }
            final WildStackerCompat.Amount stackAmount = wildStacker.getLogicalSpawnerAmount(spawner);
            if (stackAmount.result() != WildStackerCompat.Result.SUCCESS) {
                failClosed(event);
                return;
            }
            counters.nearbyStackCapWildStackerLookup();
            final long maximumContribution = NearbyStackCapPolicy.maximumCycleContribution(
                spawner.getSpawnCount(),
                stackAmount.amount()
            );
            if (maximumContribution > NearbyStackCapPolicy.remainingCapacity(
                count.logicalAmount,
                settings.maximumAmount()
            )) {
                block(event);
            }
        }
    }

    /**
     * Covers WildStacker's non-overridden Bukkit SpawnerSpawnEvent flow. PlexonSpawners
     * loads first, and this LOWEST listener cancels before WildStacker's LOWEST handler.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawnerSpawn(final SpawnerSpawnEvent event) {
        if (!enabled() || !wildStacker.installed()) {
            return;
        }

        final CreatureSpawner spawner = event.getSpawner();
        if (spawner == null) {
            return;
        }
        final Location spawnerLocation = spawner.getLocation();
        final ManagedSpawner managed = registry.find(spawnerLocation);
        if (managed == null || managed.type() != spawner.getSpawnedType() || managed.type() != event.getEntityType()) {
            return;
        }

        final CycleContext cycle = currentCycle(spawnerLocation, managed.type());
        if (cycle != null) {
            return;
        }

        counters.nearbyStackCapCheck();
        final CountResult count = countNearby(spawnerLocation, managed.type());
        if (!count.available) {
            cancelSpawnerSpawn(event, true);
            return;
        }
        if (count.logicalAmount >= settings.maximumAmount()) {
            cancelSpawnerSpawn(event, false);
            return;
        }

        final WildStackerCompat.Amount stackAmount = wildStacker.getLogicalSpawnerAmount(spawner);
        if (stackAmount.result() != WildStackerCompat.Result.SUCCESS) {
            cancelSpawnerSpawn(event, true);
            return;
        }
        counters.nearbyStackCapWildStackerLookup();
        final int additional = Math.max(0, stackAmount.amount() - 1);
        final int remaining = NearbyStackCapPolicy.remainingCapacity(count.logicalAmount, settings.maximumAmount());
        if (additional > remaining) {
            cancelSpawnerSpawn(event, false);
        }
    }

    /** Called reflectively from WildStacker's SpawnerStackedEntitySpawnEvent. */
    @Override
    public boolean shouldUseStackedSpawn(final CreatureSpawner spawner) {
        if (!enabled()) {
            activeCycle = null;
            return true;
        }
        if (!Bukkit.isPrimaryThread()) {
            activeCycle = null;
            counters.nearbyStackCapFailClosed();
            return false;
        }

        final Location location = spawner.getLocation();
        final ManagedSpawner managed = registry.find(location);
        if (managed == null || managed.type() != spawner.getSpawnedType()) {
            activeCycle = null;
            return true;
        }

        counters.nearbyStackCapCheck();
        final CountResult count = countNearby(location, managed.type());
        if (!count.available) {
            counters.nearbyStackCapFailClosed();
            activeCycle = CycleContext.create(
                location,
                managed.type(),
                settings.radius(),
                NearbyStackCapPolicy.Decision.FAIL_CLOSED,
                0
            );
            return false;
        }

        final WildStackerCompat.Amount spawnerAmount = wildStacker.getLogicalSpawnerAmount(spawner);
        if (spawnerAmount.result() != WildStackerCompat.Result.SUCCESS) {
            counters.nearbyStackCapFailClosed();
            activeCycle = CycleContext.create(
                location,
                managed.type(),
                settings.radius(),
                NearbyStackCapPolicy.Decision.FAIL_CLOSED,
                0
            );
            return false;
        }
        counters.nearbyStackCapWildStackerLookup();

        final long maximumContribution = NearbyStackCapPolicy.maximumCycleContribution(
            spawner.getSpawnCount(),
            spawnerAmount.amount()
        );
        final NearbyStackCapPolicy.Decision decision = NearbyStackCapPolicy.decide(
            count.logicalAmount,
            settings.maximumAmount(),
            maximumContribution,
            true
        );
        final int remaining = NearbyStackCapPolicy.remainingCapacity(count.logicalAmount, settings.maximumAmount());
        activeCycle = CycleContext.create(location, managed.type(), settings.radius(), decision, remaining);

        if (decision == NearbyStackCapPolicy.Decision.BLOCKED) {
            counters.nearbyStackCapBlocked();
            return false;
        }
        return decision == NearbyStackCapPolicy.Decision.FAST_PATH;
    }

    /** Called reflectively before WildStacker's direct targetEntity.increaseStackAmount path. */
    @Override
    public boolean shouldCancelEntityStack(final LivingEntity existingTarget) {
        final CycleContext cycle = activeCycle;
        if (cycle == null || cycle.expired(existingTarget.getWorld())) {
            activeCycle = null;
            return false;
        }
        if (!cycle.matchesEntity(existingTarget)) {
            return false;
        }
        return cycle.mode != NearbyStackCapPolicy.Decision.FAST_PATH;
    }

    private boolean enabled() {
        return settings.enabled();
    }

    private boolean matchesSpawnType(final EntityType managedType, final EntityType candidate) {
        return managedType == candidate;
    }

    private CountResult countNearby(final Location spawnerLocation, final EntityType type) {
        final World world = spawnerLocation.getWorld();
        if (world == null) {
            return CountResult.unavailable();
        }

        final double radius = settings.radius();
        final Location center = spawnerLocation.clone().add(0.5D, 0.5D, 0.5D);
        final Collection<Entity> nearby = world.getNearbyEntities(center, radius, radius, radius);
        int logicalAmount = 0;
        for (final Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (settings.sameTypeOnly() && living.getType() != type) {
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
            final long summed = (long) logicalAmount + contribution;
            logicalAmount = (int) Math.min(settings.maximumAmount(), summed);
            counters.nearbyStackCapLogicalEntitiesCounted(contribution);
            if (logicalAmount >= settings.maximumAmount()) {
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

    private CreatureSpawner spawnerAt(final Location location) {
        final BlockState state = location.getBlock().getState();
        return state instanceof CreatureSpawner spawner ? spawner : null;
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
        private static CountResult unavailable() {
            return new CountResult(false, 0);
        }
    }

    private static final class CycleContext {
        private final UUID worldId;
        private final long worldTime;
        private final int x;
        private final int y;
        private final int z;
        private final EntityType type;
        private final double radius;
        private final NearbyStackCapPolicy.Decision mode;
        private int remainingCapacity;

        private CycleContext(
            final UUID worldId,
            final long worldTime,
            final int x,
            final int y,
            final int z,
            final EntityType type,
            final double radius,
            final NearbyStackCapPolicy.Decision mode,
            final int remainingCapacity
        ) {
            this.worldId = worldId;
            this.worldTime = worldTime;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.radius = radius;
            this.mode = mode;
            this.remainingCapacity = remainingCapacity;
        }

        private static CycleContext create(
            final Location location,
            final EntityType type,
            final double radius,
            final NearbyStackCapPolicy.Decision mode,
            final int remainingCapacity
        ) {
            final World world = location.getWorld();
            return new CycleContext(
                world.getUID(),
                world.getFullTime(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                type,
                radius,
                mode,
                remainingCapacity
            );
        }

        private boolean expired(final World world) {
            return world == null || !worldId.equals(world.getUID()) || worldTime != world.getFullTime();
        }

        private boolean matchesSpawner(final Location location, final EntityType candidateType) {
            final World world = location.getWorld();
            return world != null
                && worldId.equals(world.getUID())
                && x == location.getBlockX()
                && y == location.getBlockY()
                && z == location.getBlockZ()
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
            return Math.abs(dx) <= radius && Math.abs(dy) <= radius && Math.abs(dz) <= radius;
        }
    }
}
