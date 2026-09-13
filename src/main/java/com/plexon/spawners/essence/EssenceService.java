package com.plexon.spawners.essence;

import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.reward.RewardDelivery;
import com.plexon.spawners.reward.RewardItemFactory;
import com.plexon.spawners.reward.RewardRollPolicy;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EssenceService {
    private static final String DEFAULT_NAME = "<gradient:#56B9F2:#92E1FF><b>Spawner Essence</b></gradient>";
    private static final List<String> DEFAULT_LORE = List.of(
        "<gray>A concentrated fragment of spawner energy.</gray>",
        "<dark_gray>PlexonSpawners essence reward</dark_gray>");

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final NamespacedKey essenceKey;
    private final RewardItemFactory itemFactory = new RewardItemFactory();
    private ItemStack template;

    public EssenceService(final JavaPlugin plugin, final PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.essenceKey = new NamespacedKey(plugin, "spawner_essence");
        reload();
    }

    public void reload() {
        final ItemStack legacy = plugin.getConfig().getItemStack("essence.item");
        if (legacy != null && !legacy.getType().isAir()) {
            final ItemStack secured = legacy.clone();
            secured.setAmount(1);
            final ItemMeta meta = secured.getItemMeta();
            meta.getPersistentDataContainer().set(essenceKey, PersistentDataType.INTEGER, 1);
            secured.setItemMeta(meta);
            template = secured;
            return;
        }
        final RewardItemFactory.ItemDefinition definition = itemFactory.read(
            plugin.getConfig().getConfigurationSection("essence.item"),
            Material.AMETHYST_SHARD,
            DEFAULT_NAME,
            DEFAULT_LORE,
            true);
        template = itemFactory.build(definition, essenceKey);
    }

    public RewardRollPolicy.Award evaluate(final EntityType type, final int logicalAmount) {
        final PluginSettings.RewardRule rule = settings.essenceRule(type);
        return EssenceRewardPolicy.evaluate(logicalAmount, rule.amount(), rule.chance(),
            () -> ThreadLocalRandom.current().nextDouble(100.0D));
    }

    public long deliver(final Player player, final Location fallbackLocation, final long totalAmount) {
        return RewardDelivery.deliver(player, fallbackLocation, template, totalAmount, settings.essenceDelivery());
    }

    public ItemStack preview() {
        return template.clone();
    }
}
