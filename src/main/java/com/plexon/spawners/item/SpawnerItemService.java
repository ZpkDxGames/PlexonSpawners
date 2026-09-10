package com.plexon.spawners.item;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnerItemService {
    private static final String DEFAULT_NAME =
        "<!italic><gradient:#56B9F2:#92E1FF><b>✦ %mob% Spawner</b></gradient>";
    private static final List<String> DEFAULT_LORE = List.of(
        "",
        "<!italic><#D8DEE9>A dormant cage bound to the</#D8DEE9>",
        "<!italic><#D8DEE9>essence of <white>%mob%</white>.</#D8DEE9>",
        "",
        "<!italic><#4B5563>› <#8B95A7>Creature</#8B95A7> <white>%mob%</white>",
        "<!italic><#4B5563>› <#8B95A7>Tier</#8B95A7> <#72F1B8>%tier%</#72F1B8>",
        "<!italic><#4B5563>› <#8B95A7>State</#8B95A7> <#72F1B8>Ready to Place</#72F1B8>",
        "",
        "<!italic><#8B95A7>Place to awaken this spawner.</#8B95A7>",
        "<!italic><gradient:#C850C0:#FF7EB3>PlexonCraft</gradient> <dark_gray>• Spawner</dark_gray>"
    );
    private static final int CURRENT_SCHEMA = 2;
    private static final int MIN_SUPPORTED_SCHEMA = 1;
    private static final Map<EntityType, String> DISPLAY_NAMES = buildDisplayNames();

    private final JavaPlugin plugin;
    private final NamespacedKey managedKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey schemaKey;
    private final NamespacedKey tierKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, EntityType> entityByKey = buildEntityKeyLookup();
    private final Map<TemplateKey, ItemStack> templateCache = new HashMap<>();

    private String nameTemplate;
    private List<String> loreTemplate;

    public SpawnerItemService(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.managedKey = new NamespacedKey(plugin, "managed_spawner");
        this.typeKey = new NamespacedKey(plugin, "spawner_type");
        this.schemaKey = new NamespacedKey(plugin, "spawner_schema");
        this.tierKey = new NamespacedKey(plugin, "spawner_tier");
        reload();
    }

    public void reload() {
        final FileConfiguration config = plugin.getConfig();
        nameTemplate = config.getString("spawner-item.name", DEFAULT_NAME);
        loreTemplate = config.contains("spawner-item.lore")
            ? List.copyOf(config.getStringList("spawner-item.lore"))
            : DEFAULT_LORE;
        templateCache.clear();
    }

    public ItemStack createSpawner(final EntityType entityType, final int amount) {
        return createSpawner(entityType, amount, 1);
    }

    public ItemStack createSpawner(final EntityType entityType, final int amount, final int tier) {
        final int safeTier = Math.max(1, tier);
        final TemplateKey key = new TemplateKey(entityType, safeTier);
        final ItemStack template = templateCache.computeIfAbsent(key, ignored -> createTemplate(entityType, safeTier));
        final ItemStack item = template.clone();
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        return item;
    }

    public boolean isManagedSpawner(final ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }
        return hasManagedMarker(item.getItemMeta().getPersistentDataContainer());
    }

    public EntityType readSpawnerType(final ItemStack item) {
        final PersistentDataContainer pdc = readablePdc(item);
        if (pdc == null) {
            return null;
        }
        final String key = pdc.get(typeKey, PersistentDataType.STRING);
        if (key == null || key.isBlank()) {
            return null;
        }
        return entityByKey.get(key.toLowerCase(Locale.ROOT));
    }

    public int readSpawnerTier(final ItemStack item) {
        final PersistentDataContainer pdc = readablePdc(item);
        if (pdc == null) {
            return 1;
        }
        final Integer schema = pdc.get(schemaKey, PersistentDataType.INTEGER);
        if (schema == null || schema == 1) {
            return 1;
        }
        final Integer tier = pdc.get(tierKey, PersistentDataType.INTEGER);
        return tier == null ? 1 : Math.max(1, tier);
    }

    public int templateCacheSize() {
        return templateCache.size();
    }

    public int entityKeyLookupSize() {
        return entityByKey.size();
    }

    public static String pretty(final EntityType type) {
        return DISPLAY_NAMES.getOrDefault(type, type.name());
    }

    private PersistentDataContainer readablePdc(final ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return null;
        }
        final PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!hasManagedMarker(pdc)) {
            return null;
        }
        final Integer schema = pdc.get(schemaKey, PersistentDataType.INTEGER);
        if (schema != null && (schema < MIN_SUPPORTED_SCHEMA || schema > CURRENT_SCHEMA)) {
            return null;
        }
        return pdc;
    }

    private ItemStack createTemplate(final EntityType entityType, final int tier) {
        final ItemStack item = new ItemStack(Material.SPAWNER, 1);
        final ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof BlockStateMeta meta)) {
            throw new IllegalStateException("SPAWNER item did not expose BlockStateMeta");
        }

        final BlockState blockState = meta.getBlockState();
        if (blockState instanceof CreatureSpawner creatureSpawner) {
            creatureSpawner.setSpawnedType(entityType);
            meta.setBlockState(creatureSpawner);
        }

        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(managedKey, PersistentDataType.INTEGER, 1);
        pdc.set(typeKey, PersistentDataType.STRING, entityType.getKey().asString());
        pdc.set(schemaKey, PersistentDataType.INTEGER, CURRENT_SCHEMA);
        pdc.set(tierKey, PersistentDataType.INTEGER, tier);

        final String display = pretty(entityType);
        meta.displayName(miniMessage.deserialize(resolve(nameTemplate, display, tier)));

        final List<Component> lore = new ArrayList<>(loreTemplate.size());
        for (final String line : loreTemplate) {
            lore.add(miniMessage.deserialize(resolve(line, display, tier)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String resolve(final String input, final String display, final int tier) {
        return input.replace("%mob%", display).replace("%tier%", Integer.toString(tier));
    }

    private boolean hasManagedMarker(final PersistentDataContainer pdc) {
        final Integer marker = pdc.get(managedKey, PersistentDataType.INTEGER);
        return marker != null && marker == 1;
    }

    private static Map<String, EntityType> buildEntityKeyLookup() {
        final Map<String, EntityType> lookup = new HashMap<>();
        for (final EntityType type : EntityType.values()) {
            lookup.put(type.getKey().asString().toLowerCase(Locale.ROOT), type);
            lookup.put(type.name().toLowerCase(Locale.ROOT), type);
        }
        return Map.copyOf(lookup);
    }

    private static Map<EntityType, String> buildDisplayNames() {
        final EnumMap<EntityType, String> displayNames = new EnumMap<>(EntityType.class);
        for (final EntityType type : EntityType.values()) {
            displayNames.put(type, formatDisplayName(type.name()));
        }
        return Map.copyOf(displayNames);
    }

    private static String formatDisplayName(final String enumName) {
        final String lower = enumName.toLowerCase(Locale.ROOT);
        final StringBuilder builder = new StringBuilder(lower.length());
        boolean capitalize = true;
        for (int index = 0; index < lower.length(); index++) {
            final char character = lower.charAt(index);
            if (character == '_') {
                builder.append(' ');
                capitalize = true;
            } else if (capitalize) {
                builder.append(Character.toUpperCase(character));
                capitalize = false;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private record TemplateKey(EntityType type, int tier) {}
}
