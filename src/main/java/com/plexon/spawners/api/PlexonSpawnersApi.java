package com.plexon.spawners.api;

import com.plexon.spawners.breaking.NonSilkRewardMode;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/** Read-only policy/integration API. WildStacker remains the sole stack-mutation authority. */
public interface PlexonSpawnersApi {
    String version();
    long generation();
    boolean isWorldEnabled(World world);
    BreakPolicyView policy(EntityType type);
    boolean isEssence(ItemStack item);
    boolean isCustomReward(ItemStack item);
    ItemStack essenceTemplate();
    ItemStack customRewardTemplate();
    IntegrationStatus integration();

    record BreakPolicyView(
        NonSilkRewardMode nonSilkMode,
        boolean essenceEnabled,
        boolean customRewardEnabled,
        int requiredSilkTouchLevel,
        boolean bypassPermissionEnabled
    ) {}

    record IntegrationStatus(
        boolean wildStackerReady,
        String wildStackerVersion,
        boolean coreInstalled,
        boolean coreCompatible,
        String coreVersion,
        String coreApi,
        String coreState
    ) {}
}
