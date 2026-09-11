package com.plexon.spawners.managed;

import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnerOriginService {
    private static final int ORIGIN_SCHEMA = 1;

    private final NamespacedKey markerKey;
    private final NamespacedKey schemaKey;
    private final NamespacedKey sourceKey;

    public SpawnerOriginService(final JavaPlugin plugin) {
        markerKey = new NamespacedKey(plugin, "spawner_origin");
        schemaKey = new NamespacedKey(plugin, "spawner_origin_schema");
        sourceKey = new NamespacedKey(plugin, "spawner_origin_source");
    }

    public void mark(final Entity entity, final UUID sourceSpawnerId) {
        final PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.INTEGER, 1);
        pdc.set(schemaKey, PersistentDataType.INTEGER, ORIGIN_SCHEMA);
        pdc.set(sourceKey, PersistentDataType.STRING, sourceSpawnerId.toString());
    }

    public boolean isSpawnerOrigin(final Entity entity) {
        if (entity == null) {
            return false;
        }
        final PersistentDataContainer pdc = entity.getPersistentDataContainer();
        final Integer marker = pdc.get(markerKey, PersistentDataType.INTEGER);
        final Integer schema = pdc.get(schemaKey, PersistentDataType.INTEGER);
        return marker != null && marker == 1 && schema != null && schema == ORIGIN_SCHEMA;
    }

    public UUID sourceSpawnerId(final Entity entity) {
        if (!isSpawnerOrigin(entity)) {
            return null;
        }
        final String raw = entity.getPersistentDataContainer().get(sourceKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }
}
