package com.plexon.spawners.reward;

import com.plexon.spawners.config.PluginSettings;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomDropService {
    private final PluginSettings settings;
    private final RewardItemFactory itemFactory = new RewardItemFactory();
    private final NamespacedKey identityKey;
    private volatile ItemStack template;

    public CustomDropService(final JavaPlugin plugin, final PluginSettings settings) {
        this.settings = settings;
        this.identityKey = new NamespacedKey(plugin, "custom_drop");
    }

    public ItemStack prepare(final PluginSettings.Snapshot snapshot) {
        final ItemStack prepared = itemFactory.build(snapshot.customItem(), identityKey);
        if (prepared.getType().isAir()) throw new IllegalArgumentException("Prepared custom reward item is AIR");
        return prepared;
    }

    public void commit(final ItemStack prepared) {
        template = prepared.clone();
        template.setAmount(1);
    }

    public RewardRollPolicy.Award evaluate(final EntityType type, final int logicalAmount) {
        final PluginSettings.RewardRule rule = settings.customDropRule(type);
        return RewardRollPolicy.evaluate(logicalAmount, rule.amount(), rule.chance(),
            () -> ThreadLocalRandom.current().nextDouble(100.0D));
    }

    public long deliver(final Player player, final Location fallbackLocation, final long totalAmount) {
        return RewardDelivery.deliver(player, fallbackLocation, template, totalAmount, settings.customDropDelivery());
    }

    public ItemStack preview() {
        return template == null ? new ItemStack(org.bukkit.Material.AIR) : template.clone();
    }

    public boolean isCustomReward(final ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        final Integer marker = item.getItemMeta().getPersistentDataContainer().get(identityKey, PersistentDataType.INTEGER);
        return marker != null && marker == 1;
    }
}
