package com.plexon.spawners.managed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ManagedSpawnerRegistry implements AutoCloseable {
    private static final String HEADER = "PLEXON_SPAWNERS_DB|1";

    private final JavaPlugin plugin;
    private final Path databasePath;
    private final ConcurrentMap<UUID, ManagedSpawner> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<BlockKey, UUID> byBlock = new ConcurrentHashMap<>();
    private final ConcurrentMap<ChunkKey, Set<UUID>> byChunk = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong persistedRevision = new AtomicLong();
    private final AtomicBoolean writerRunning = new AtomicBoolean();
    private final ExecutorService writer;
    private volatile boolean closed;

    public ManagedSpawnerRegistry(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.databasePath = plugin.getDataFolder().toPath().resolve("managed-spawners.db");
        this.writer = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task, "PlexonSpawners-Persistence");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void load() {
        if (!Files.exists(databasePath)) {
            return;
        }

        final List<String> lines;
        try {
            lines = Files.readAllLines(databasePath, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not read managed-spawners.db", exception);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("managed-spawners.db is empty; refusing a silent data reset");
        }
        if (!HEADER.equals(lines.getFirst().trim())) {
            throw new IllegalStateException("Unsupported managed-spawners.db schema header: " + lines.getFirst());
        }

        int loaded = 0;
        int rejected = 0;
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            final String raw = lines.get(lineNumber).trim();
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }
            try {
                final ManagedSpawner record = decode(raw);
                putLoaded(record);
                loaded++;
            } catch (final RuntimeException exception) {
                rejected++;
                plugin.getLogger().log(
                    Level.WARNING,
                    "Skipping corrupt managed-spawner record at line " + (lineNumber + 1) + ": " + exception.getMessage()
                );
            }
        }
        revision.set(0L);
        persistedRevision.set(0L);
        plugin.getLogger().info("Loaded " + loaded + " managed spawners" + (rejected == 0 ? "." : "; rejected " + rejected + " corrupt records."));
    }

    public ManagedSpawner registerPlacement(
        final Location location,
        final EntityType type,
        final UUID ownerId,
        final int tier,
        final SpawnerAccess access
    ) {
        final ManagedSpawner record = ManagedSpawner.placed(
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            type,
            ownerId,
            tier,
            access
        );
        register(record);
        return record;
    }

    public void register(final ManagedSpawner record) {
        final BlockKey blockKey = BlockKey.of(record);
        final UUID previousAtBlock = byBlock.put(blockKey, record.id());
        if (previousAtBlock != null && !previousAtBlock.equals(record.id())) {
            final ManagedSpawner displaced = byId.remove(previousAtBlock);
            if (displaced != null) {
                removeFromChunkIndex(displaced);
            }
        }

        final ManagedSpawner previousById = byId.put(record.id(), record);
        if (previousById != null && !BlockKey.of(previousById).equals(blockKey)) {
            byBlock.remove(BlockKey.of(previousById), record.id());
            removeFromChunkIndex(previousById);
        }
        addToChunkIndex(record);
        markDirty();
    }

    public ManagedSpawner find(final Location location) {
        final UUID id = byBlock.get(BlockKey.of(location));
        return id == null ? null : byId.get(id);
    }

    public ManagedSpawner find(final UUID id) {
        return id == null ? null : byId.get(id);
    }

    public ManagedSpawner remove(final Location location) {
        final UUID id = byBlock.remove(BlockKey.of(location));
        if (id == null) {
            return null;
        }
        final ManagedSpawner removed = byId.remove(id);
        if (removed != null) {
            removeFromChunkIndex(removed);
            markDirty();
        }
        return removed;
    }

    public ManagedSpawner remove(final UUID id) {
        final ManagedSpawner removed = id == null ? null : byId.remove(id);
        if (removed != null) {
            byBlock.remove(BlockKey.of(removed), removed.id());
            removeFromChunkIndex(removed);
            markDirty();
        }
        return removed;
    }

    public ManagedSpawner updateTier(final UUID id, final int tier) {
        return update(id, current -> current.withTier(tier));
    }

    public ManagedSpawner updateAccess(final UUID id, final SpawnerAccess access) {
        return update(id, current -> current.withAccess(access));
    }

    public ManagedSpawner incrementSpawnCount(final UUID id) {
        return update(id, current -> current.withLifetimeSpawns(current.lifetimeSpawns() + 1L));
    }

    public int size() {
        return byId.size();
    }

    public int countInChunk(final Chunk chunk) {
        final Set<UUID> ids = byChunk.get(new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
        return ids == null ? 0 : ids.size();
    }

    public List<ManagedSpawner> entriesInChunk(final World world, final int chunkX, final int chunkZ) {
        final Set<UUID> ids = byChunk.get(new ChunkKey(world.getUID(), chunkX, chunkZ));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        final List<ManagedSpawner> records = new ArrayList<>(ids.size());
        for (final UUID id : ids) {
            final ManagedSpawner record = byId.get(id);
            if (record != null) {
                records.add(record);
            }
        }
        return List.copyOf(records);
    }

    public ManagedSpawner nearest(final Location location, final EntityType type, final int radius) {
        final World world = location.getWorld();
        if (world == null || radius < 1) {
            return null;
        }
        final int minChunkX = (location.getBlockX() - radius) >> 4;
        final int maxChunkX = (location.getBlockX() + radius) >> 4;
        final int minChunkZ = (location.getBlockZ() - radius) >> 4;
        final int maxChunkZ = (location.getBlockZ() + radius) >> 4;
        final double radiusSquared = (double) radius * radius;
        ManagedSpawner nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                final Set<UUID> ids = byChunk.get(new ChunkKey(world.getUID(), chunkX, chunkZ));
                if (ids == null) {
                    continue;
                }
                for (final UUID id : ids) {
                    final ManagedSpawner record = byId.get(id);
                    if (record == null || record.type() != type) {
                        continue;
                    }
                    final double dx = location.getX() - (record.x() + 0.5D);
                    final double dy = location.getY() - (record.y() + 0.5D);
                    final double dz = location.getZ() - (record.z() + 0.5D);
                    final double distance = dx * dx + dy * dy + dz * dz;
                    if (distance <= radiusSquared && distance < nearestDistance) {
                        nearest = record;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    public List<ManagedSpawner> snapshot() {
        return byId.values().stream()
            .sorted(Comparator.comparing(ManagedSpawner::worldId)
                .thenComparingInt(ManagedSpawner::x)
                .thenComparingInt(ManagedSpawner::y)
                .thenComparingInt(ManagedSpawner::z))
            .toList();
    }

    public void flushAsync() {
        if (closed || revision.get() <= persistedRevision.get()) {
            return;
        }
        if (!writerRunning.compareAndSet(false, true)) {
            return;
        }

        writer.execute(() -> {
            boolean failed = false;
            try {
                while (!closed) {
                    final long targetRevision = revision.get();
                    final List<ManagedSpawner> records = snapshot();
                    writeSnapshot(records);
                    persistedRevision.set(targetRevision);
                    if (revision.get() == targetRevision) {
                        break;
                    }
                }
            } catch (final IOException exception) {
                failed = true;
                plugin.getLogger().log(Level.SEVERE, "Could not persist managed spawners; in-memory state remains authoritative for this runtime.", exception);
            } finally {
                writerRunning.set(false);
                if (!failed && !closed && revision.get() > persistedRevision.get()) {
                    flushAsync();
                }
            }
        });
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }

        if (revision.get() > persistedRevision.get()) {
            try {
                writeSnapshot(snapshot());
                persistedRevision.set(revision.get());
            } catch (final IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Final managed-spawner persistence flush failed.", exception);
            }
        }
    }

    private ManagedSpawner update(final UUID id, final java.util.function.UnaryOperator<ManagedSpawner> operation) {
        if (id == null) {
            return null;
        }
        final ManagedSpawner updated = byId.computeIfPresent(id, (ignored, current) -> operation.apply(current));
        if (updated != null) {
            byBlock.put(BlockKey.of(updated), updated.id());
            markDirty();
        }
        return updated;
    }

    private void putLoaded(final ManagedSpawner record) {
        final ManagedSpawner displaced = byId.put(record.id(), record);
        if (displaced != null) {
            byBlock.remove(BlockKey.of(displaced), displaced.id());
            removeFromChunkIndex(displaced);
        }
        final UUID previousAtBlock = byBlock.put(BlockKey.of(record), record.id());
        if (previousAtBlock != null && !previousAtBlock.equals(record.id())) {
            final ManagedSpawner previous = byId.remove(previousAtBlock);
            if (previous != null) {
                removeFromChunkIndex(previous);
            }
        }
        addToChunkIndex(record);
    }

    private void markDirty() {
        revision.incrementAndGet();
    }

    private void addToChunkIndex(final ManagedSpawner record) {
        byChunk.computeIfAbsent(ChunkKey.of(record), ignored -> ConcurrentHashMap.newKeySet()).add(record.id());
    }

    private void removeFromChunkIndex(final ManagedSpawner record) {
        final ChunkKey key = ChunkKey.of(record);
        final Set<UUID> ids = byChunk.get(key);
        if (ids == null) {
            return;
        }
        ids.remove(record.id());
        if (ids.isEmpty()) {
            byChunk.remove(key, ids);
        }
    }

    private void writeSnapshot(final List<ManagedSpawner> records) throws IOException {
        Files.createDirectories(databasePath.getParent());
        final Path temp = databasePath.resolveSibling(databasePath.getFileName() + ".tmp");
        final List<String> lines = new ArrayList<>(records.size() + 1);
        lines.add(HEADER);
        for (final ManagedSpawner record : records) {
            lines.add(encode(record));
        }
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, databasePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException ignored) {
            Files.move(temp, databasePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String encode(final ManagedSpawner record) {
        return String.join("|",
            "R",
            record.id().toString(),
            record.worldId().toString(),
            Integer.toString(record.x()),
            Integer.toString(record.y()),
            Integer.toString(record.z()),
            record.type().name(),
            record.ownerId().toString(),
            Integer.toString(record.tier()),
            record.access().name(),
            Long.toString(record.placedAtEpochMillis()),
            Long.toString(record.lifetimeSpawns())
        );
    }

    private static ManagedSpawner decode(final String raw) {
        final String[] fields = raw.split("\\|", -1);
        if (fields.length != 12 || !"R".equals(fields[0])) {
            throw new IllegalArgumentException("invalid record shape");
        }
        return new ManagedSpawner(
            UUID.fromString(fields[1]),
            UUID.fromString(fields[2]),
            Integer.parseInt(fields[3]),
            Integer.parseInt(fields[4]),
            Integer.parseInt(fields[5]),
            EntityType.valueOf(fields[6]),
            UUID.fromString(fields[7]),
            Integer.parseInt(fields[8]),
            SpawnerAccess.valueOf(fields[9]),
            Long.parseLong(fields[10]),
            Long.parseLong(fields[11])
        );
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey of(final Location location) {
            return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private static BlockKey of(final ManagedSpawner record) {
            return new BlockKey(record.worldId(), record.x(), record.y(), record.z());
        }
    }

    private record ChunkKey(UUID worldId, int x, int z) {
        private static ChunkKey of(final ManagedSpawner record) {
            return new ChunkKey(record.worldId(), record.x() >> 4, record.z() >> 4);
        }
    }
}
