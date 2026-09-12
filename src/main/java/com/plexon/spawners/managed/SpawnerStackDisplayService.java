package com.plexon.spawners.managed;

import com.plexon.spawners.config.NativeStackSettings;
import com.plexon.spawners.item.SpawnerItemService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Event-driven TextDisplay lifecycle for native logical stack titles. */
public final class SpawnerStackDisplayService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ManagedSpawnerRegistry registry;
    private final SpawnerTuning tuning;
    private final NativeStackSettings settings;
    private final NamespacedKey ownerKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SpawnerStackDisplayService(
        final JavaPlugin plugin,
        final ManagedSpawnerRegistry registry,
        final SpawnerTuning tuning,
        final NativeStackSettings settings
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.tuning = tuning;
        this.settings = settings;
        this.ownerKey = new NamespacedKey(plugin, "stack_display_owner");
    }

    public void refresh(final ManagedSpawner record) {
        if (record == null) {
            return;
        }
        final World world = Bukkit.getWorld(record.worldId());
        if (world == null || !world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            return;
        }
        final Location anchor = anchor(record, world);
        final List<TextDisplay> existing = findOwnedDisplays(anchor, record.id());
        if (!shouldShow(record)) {
            existing.forEach(Entity::remove);
            return;
        }

        final TextDisplay display;
        if (existing.isEmpty()) {
            display = world.spawn(anchor, TextDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.setGravity(false);
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, record.id().toString());
            });
        } else {
            display = existing.getFirst();
            for (int index = 1; index < existing.size(); index++) {
                existing.get(index).remove();
            }
            if (display.getLocation().distanceSquared(anchor) > 0.01D) {
                display.teleport(anchor);
            }
        }
        display.setViewRange((float) settings.viewDistance());
        display.text(render(record));
    }

    public void remove(final ManagedSpawner record) {
        if (record == null) {
            return;
        }
        final World world = Bukkit.getWorld(record.worldId());
        if (world == null || !world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            return;
        }
        findOwnedDisplays(anchor(record, world), record.id()).forEach(Entity::remove);
    }

    public void reconcileLoaded() {
        for (final ManagedSpawner record : registry.snapshot()) {
            refresh(record);
        }
    }

    public void reconcileChunk(final World world, final int chunkX, final int chunkZ) {
        for (final ManagedSpawner record : registry.entriesInChunk(world, chunkX, chunkZ)) {
            refresh(record);
        }
    }

    @Override
    public void close() {
        for (final World world : Bukkit.getWorlds()) {
            for (final TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) {
                    display.remove();
                }
            }
        }
    }

    private boolean shouldShow(final ManagedSpawner record) {
        return settings.enabled()
            && settings.displayEnabled()
            && !settings.hideTitle()
            && !(settings.hideSingle() && record.stackAmount() <= 1);
    }

    private Component render(final ManagedSpawner record) {
        String raw = settings.displayFormat()
            .replace("%entity%", SpawnerItemService.pretty(record.type()))
            .replace("%amount%", Integer.toString(record.stackAmount()));
        if (settings.showTier()) {
            final SpawnerTier tier = tuning.tier(record.tier());
            raw += settings.tierFormat().replace("%tier%", tier.label() + " " + tier.level());
        }
        return miniMessage.deserialize(raw);
    }

    private List<TextDisplay> findOwnedDisplays(final Location anchor, final UUID ownerId) {
        final List<TextDisplay> matches = new ArrayList<>();
        for (final Entity entity : anchor.getWorld().getNearbyEntities(anchor, 1.0D, 3.0D, 1.0D)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            final String raw = display.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (ownerId.toString().equals(raw)) {
                matches.add(display);
            }
        }
        return matches;
    }

    private Location anchor(final ManagedSpawner record, final World world) {
        return new Location(world, record.x() + 0.5D, record.y() + settings.verticalOffset(), record.z() + 0.5D);
    }
}
