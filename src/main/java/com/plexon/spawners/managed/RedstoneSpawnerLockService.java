package com.plexon.spawners.managed;

import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import com.plexon.spawners.config.RedstoneLockSettings;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runtime redstone lock for managed spawners.
 *
 * <p>The physical delay is moved to a distant hold value while powered and the exact
 * pre-lock delay is kept in memory. Unlocking restores that delay, so live redstone
 * time does not consume the spawn countdown. One shared poll covers only managed
 * spawners in loaded chunks; event hooks provide immediate reconciliation and a
 * final spawn-time safety gate.</p>
 */
public final class RedstoneSpawnerLockService implements Listener, AutoCloseable {
    private static final int HOLD_DELAY = 32_000;
    private static final int HOLD_REFRESH_BELOW = 30_000;
    private static final int REDSTONE_DISCOVERY_RADIUS = 2;

    private final JavaPlugin plugin;
    private final ManagedSpawnerRegistry registry;
    private final RedstoneLockSettings settings;
    private final Set<UUID> loadedManaged = new HashSet<>();
    private final Map<UUID, FrozenLock> locked = new HashMap<>();
    private BukkitTask pollTask;

    public RedstoneSpawnerLockService(
        final JavaPlugin plugin,
        final ManagedSpawnerRegistry registry,
        final RedstoneLockSettings settings
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.settings = settings;
    }

    public void start() {
        rebuildLoadedSet();
        reconcileLoaded();
        reschedule();
    }

    public void reload() {
        if (!settings.enabled()) {
            restoreAll();
        } else {
            rebuildLoadedSet();
            reconcileLoaded();
        }
        reschedule();
    }

    /** Reconcile one known managed spawner immediately after a physical state update. */
    public void refresh(final ManagedSpawner record) {
        if (record == null) {
            return;
        }
        if (!settings.enabled()) {
            final CreatureSpawner spawner = physicalSpawner(record);
            if (spawner != null) {
                unlock(record, spawner);
            } else {
                locked.remove(record.id());
            }
            return;
        }
        loadedManaged.add(record.id());
        reconcile(record);
    }

    public boolean isLocked(final UUID spawnerId) {
        return spawnerId != null && locked.containsKey(spawnerId);
    }

    public int lockedCount() {
        return locked.size();
    }

    public int loadedManagedCount() {
        return loadedManaged.size();
    }

    /** Returns the effective frozen/live countdown, or -1 when the block is unavailable. */
    public int nextSpawnTicks(final ManagedSpawner record) {
        if (record == null) {
            return -1;
        }
        final FrozenLock frozen = locked.get(record.id());
        if (frozen != null) {
            return frozen.delayTicks();
        }
        final CreatureSpawner spawner = physicalSpawner(record);
        return spawner == null ? -1 : Math.max(0, spawner.getDelay());
    }

    public boolean isPowered(final ManagedSpawner record) {
        if (record == null) {
            return false;
        }
        final World world = Bukkit.getWorld(record.worldId());
        if (world == null || !world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            return false;
        }
        return powered(world.getBlockAt(record.x(), record.y(), record.z()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(final ChunkLoadEvent event) {
        if (!settings.enabled()) {
            return;
        }
        for (final ManagedSpawner record : registry.entriesInChunk(
            event.getWorld(), event.getChunk().getX(), event.getChunk().getZ())) {
            loadedManaged.add(record.id());
            reconcile(record);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(final ChunkUnloadEvent event) {
        for (final ManagedSpawner record : registry.entriesInChunk(
            event.getWorld(), event.getChunk().getX(), event.getChunk().getZ())) {
            loadedManaged.remove(record.id());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (!settings.enabled()) {
            return;
        }
        final ManagedSpawner record = registry.find(event.getBlockPlaced().getLocation());
        if (record != null) {
            loadedManaged.add(record.id());
            reconcile(record);
        } else {
            reconcileNear(event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(final BlockBreakEvent event) {
        reconcileNear(event.getBlock().getLocation());
        locked.entrySet().removeIf(entry -> registry.find(entry.getKey()) == null);
        loadedManaged.removeIf(id -> registry.find(id) == null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstoneChange(final BlockRedstoneEvent event) {
        if (settings.enabled()) {
            reconcileNear(event.getBlock().getLocation());
        }
    }

    /** Safety gate in case a power transition reaches spawn evaluation before reconciliation. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreSpawnerSpawn(final PreSpawnerSpawnEvent event) {
        if (!settings.enabled()) {
            return;
        }
        final ManagedSpawner record = registry.find(event.getSpawnerLocation());
        if (record == null) {
            return;
        }
        if (isPowered(record) || isLocked(record.id())) {
            reconcile(record);
            event.setCancelled(true);
            event.setShouldAbortSpawn(true);
        }
    }

    /** Backup for non-Paper/custom spawner flows that still emit Bukkit's spawn event. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawnerSpawn(final SpawnerSpawnEvent event) {
        if (!settings.enabled() || event.getSpawner() == null) {
            return;
        }
        final ManagedSpawner record = registry.find(event.getSpawner().getLocation());
        if (record == null) {
            return;
        }
        if (isPowered(record) || isLocked(record.id())) {
            reconcile(record);
            event.setCancelled(true);
        }
    }

    private void reschedule() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        if (!settings.enabled()) {
            return;
        }
        final long interval = settings.pollIntervalTicks();
        pollTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::reconcileLoaded,
            interval,
            interval
        );
    }

    private void rebuildLoadedSet() {
        loadedManaged.clear();
        for (final ManagedSpawner record : registry.snapshot()) {
            final World world = Bukkit.getWorld(record.worldId());
            if (world != null && world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
                loadedManaged.add(record.id());
            }
        }
    }

    private void reconcileLoaded() {
        if (!settings.enabled()) {
            return;
        }
        loadedManaged.removeIf(id -> registry.find(id) == null);
        for (final UUID id : Set.copyOf(loadedManaged)) {
            final ManagedSpawner record = registry.find(id);
            if (record != null) {
                reconcile(record);
            }
        }
    }

    private void reconcileNear(final Location origin) {
        final World world = origin.getWorld();
        if (world == null) {
            return;
        }
        final int radius = REDSTONE_DISCOVERY_RADIUS;
        final int minChunkX = (origin.getBlockX() - radius) >> 4;
        final int maxChunkX = (origin.getBlockX() + radius) >> 4;
        final int minChunkZ = (origin.getBlockZ() - radius) >> 4;
        final int maxChunkZ = (origin.getBlockZ() + radius) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                for (final ManagedSpawner record : registry.entriesInChunk(world, chunkX, chunkZ)) {
                    if (Math.abs(record.x() - origin.getBlockX()) <= radius
                        && Math.abs(record.y() - origin.getBlockY()) <= radius
                        && Math.abs(record.z() - origin.getBlockZ()) <= radius) {
                        loadedManaged.add(record.id());
                        reconcile(record);
                    }
                }
            }
        }
    }

    private void reconcile(final ManagedSpawner record) {
        final CreatureSpawner spawner = physicalSpawner(record);
        if (spawner == null) {
            loadedManaged.remove(record.id());
            locked.remove(record.id());
            return;
        }

        if (powered(spawner.getBlock())) {
            lock(record, spawner);
        } else {
            unlock(record, spawner);
        }
    }

    private void lock(final ManagedSpawner record, final CreatureSpawner spawner) {
        FrozenLock frozen = locked.get(record.id());
        if (frozen == null) {
            frozen = new FrozenLock(Math.max(0, spawner.getDelay()));
            locked.put(record.id(), frozen);
        }
        if (spawner.getDelay() < HOLD_REFRESH_BELOW) {
            spawner.setDelay(HOLD_DELAY);
            spawner.update(true, false);
        }
    }

    private void unlock(final ManagedSpawner record, final CreatureSpawner spawner) {
        final FrozenLock frozen = locked.remove(record.id());
        if (frozen == null) {
            return;
        }
        spawner.setDelay(frozen.delayTicks());
        spawner.update(true, false);
    }

    private CreatureSpawner physicalSpawner(final ManagedSpawner record) {
        final World world = Bukkit.getWorld(record.worldId());
        if (world == null || !world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            return null;
        }
        final Block block = world.getBlockAt(record.x(), record.y(), record.z());
        if (block.getType() != Material.SPAWNER || !(block.getState() instanceof CreatureSpawner spawner)) {
            return null;
        }
        return spawner;
    }

    private static boolean powered(final Block block) {
        return block.isBlockPowered() || block.isBlockIndirectlyPowered();
    }

    private void restoreAll() {
        for (final Map.Entry<UUID, FrozenLock> entry : Map.copyOf(locked).entrySet()) {
            final ManagedSpawner record = registry.find(entry.getKey());
            if (record == null) {
                continue;
            }
            final CreatureSpawner spawner = physicalSpawner(record);
            if (spawner != null) {
                spawner.setDelay(entry.getValue().delayTicks());
                spawner.update(true, false);
            }
        }
        locked.clear();
    }

    @Override
    public void close() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        restoreAll();
        loadedManaged.clear();
    }

    private record FrozenLock(int delayTicks) {}
}
