package com.plexon.spawners.api;

import com.plexon.spawners.PlexonSpawners;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

public final class DefaultPlexonSpawnersApi implements PlexonSpawnersApi {
    private final PlexonSpawners plugin;

    public DefaultPlexonSpawnersApi(final PlexonSpawners plugin) {
        this.plugin = plugin;
    }

    @Override public String version() { return plugin.getPluginMeta().getVersion(); }
    @Override public long generation() { return plugin.runtimeGeneration(); }
    @Override public boolean isWorldEnabled(final World world) { return plugin.settings().isWorldEnabled(world); }

    @Override
    public BreakPolicyView policy(final EntityType type) {
        return new BreakPolicyView(
            plugin.settings().nonSilkRewardMode(type),
            plugin.settings().essenceEnabled(),
            plugin.settings().customDropEnabled(),
            plugin.settings().requiredSilkTouchLevel(),
            plugin.settings().silkBypassPermissionEnabled());
    }

    @Override public boolean isEssence(final ItemStack item) { return plugin.essenceService().isEssence(item); }
    @Override public boolean isCustomReward(final ItemStack item) { return plugin.customDropService().isCustomReward(item); }
    @Override public ItemStack essenceTemplate() { return plugin.essenceService().preview(); }
    @Override public ItemStack customRewardTemplate() { return plugin.customDropService().preview(); }

    @Override
    public IntegrationStatus integration() {
        return new IntegrationStatus(
            true,
            plugin.wildStacker().version(),
            plugin.coreBridge().installed(),
            plugin.coreBridge().compatible(),
            plugin.coreBridge().pluginVersion(),
            plugin.coreBridge().apiVersion(),
            plugin.coreBridge().registrationState());
    }
}
