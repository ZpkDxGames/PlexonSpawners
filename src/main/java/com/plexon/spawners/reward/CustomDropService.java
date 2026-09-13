package com.plexon.spawners.reward;

import com.plexon.spawners.config.PluginSettings;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomDropService {
    private static final String DEFAULT_NAME = "<gradient:#7BE7FF:#4AA8FF><b>Spawner Fragment</b></gradient>";
    private static final List<String> DEFAULT_LORE = List.of(
        "<gray>Dropped when a spawner is broken without Silk Touch.</gray>",
        "<dark_gray>PlexonSpawners custom reward</dark_gray>");

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final RewardItemFactory itemFactory = new RewardItemFactory();
    private final NamespacedKey identityKey;
    private ItemStack template;

    public CustomDropService(final JavaPlugin plugin, final PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.identityKey = new NamespacedKey(plugin, "custom_drop");
        reload();
    }

    public void reload() {
        final RewardItemFactory.ItemDefinition definition = itemFactory.read(
            plugin.getConfig().getConfigurationSection("custom-drop.item"),
            Material.PRISMARINE_CRYSTALS,
            DEFAULT_NAME,
            DEFAULT_LORE,
            true);
        template = itemFactory.build(definition, identityKey);
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
        return template.clone();
    }
}
