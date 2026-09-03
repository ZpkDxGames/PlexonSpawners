package com.plexon.spawners.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SpawnerItemService {
    private final JavaPlugin plugin;
    private final NamespacedKey managedKey;
    private final NamespacedKey typeKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private String nameTemplate;
    private List<String> loreTemplate;

    public SpawnerItemService(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.managedKey = new NamespacedKey(plugin, "managed_spawner");
        this.typeKey = new NamespacedKey(plugin, "spawner_type");
        reload();
    }

    public void reload() {
        final FileConfiguration config = plugin.getConfig();
        nameTemplate = config.getString("spawner-item.name", "<light_purple><b>%mob% Spawner</b></light_purple>");
        loreTemplate = List.copyOf(config.getStringList("spawner-item.lore"));
    }

    public ItemStack createSpawner(final EntityType entityType, final int amount) {
        final ItemStack item = new ItemStack(Material.SPAWNER, Math.max(1, Math.min(64, amount)));
        final ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof BlockStateMeta meta)) {
            throw new IllegalStateException("SPAWNER item did not expose BlockStateMeta");
        }

        final BlockState blockState = meta.getBlockState();
        if (blockState instanceof CreatureSpawner creatureSpawner) {
            creatureSpawner.setSpawnedType(entityType);
            meta.setBlockState(creatureSpawner);
        }

        meta.getPersistentDataContainer().set(managedKey, PersistentDataType.INTEGER, 1);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, entityType.getKey().asString());

        final String display = pretty(entityType);
        meta.displayName(miniMessage.deserialize(nameTemplate.replace("%mob%", display)));

        final List<Component> lore = new ArrayList<>();
        for (final String line : loreTemplate) {
            lore.add(miniMessage.deserialize(line.replace("%mob%", display)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isManagedSpawner(final ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }
        final Integer marker = item.getItemMeta().getPersistentDataContainer()
            .get(managedKey, PersistentDataType.INTEGER);
        return marker != null && marker == 1;
    }

    public EntityType readSpawnerType(final ItemStack item) {
        if (!isManagedSpawner(item)) {
            return null;
        }
        final String key = item.getItemMeta().getPersistentDataContainer()
            .get(typeKey, PersistentDataType.STRING);
        if (key == null) {
            return null;
        }
        for (final EntityType type : EntityType.values()) {
            if (type.getKey().asString().equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }

    public static String pretty(final EntityType type) {
        final String[] words = type.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String word : words) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }
}
