package com.plexon.spawners.essence;

import com.plexon.spawners.config.PluginSettings;
import com.plexon.spawners.reward.RewardDelivery;
import com.plexon.spawners.reward.RewardItemFactory;
import com.plexon.spawners.reward.RewardRollPolicy;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EssenceService {
    private final PluginSettings settings;
    private final NamespacedKey essenceKey;
    private final RewardItemFactory itemFactory = new RewardItemFactory();
    private volatile ItemStack template;

    public EssenceService(final JavaPlugin plugin, final PluginSettings settings) {
        this.settings = settings;
        this.essenceKey = new NamespacedKey(plugin, "spawner_essence");
    }

    public ItemStack prepare(final PluginSettings.Snapshot snapshot) {
        final ItemStack prepared = itemFactory.build(snapshot.essenceItem(), essenceKey);
        if (prepared.getType().isAir()) throw new IllegalArgumentException("Prepared Essence item is AIR");
        return prepared;
    }

    public void commit(final ItemStack prepared) {
        template = prepared.clone();
        template.setAmount(1);
    }

    public RewardRollPolicy.Award evaluate(final EntityType type, final int logicalAmount) {
        final PluginSettings.RewardRule rule = settings.essenceRule(type);
        return RewardRollPolicy.evaluate(logicalAmount, rule.amount(), rule.chance(),
            () -> ThreadLocalRandom.current().nextDouble(100.0D));
    }

    public long deliver(final Player player, final Location fallbackLocation, final long totalAmount) {
        return RewardDelivery.deliver(player, fallbackLocation, template, totalAmount, settings.essenceDelivery());
    }

    public ItemStack preview() {
        return template == null ? new ItemStack(org.bukkit.Material.AIR) : template.clone();
    }

    public boolean isEssence(final ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        final Integer marker = item.getItemMeta().getPersistentDataContainer().get(essenceKey, PersistentDataType.INTEGER);
        return marker != null && marker == 1;
    }
}
