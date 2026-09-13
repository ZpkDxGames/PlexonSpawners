package com.plexon.spawners.breaking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SpawnerBreakPolicyTest {
    @Test
    void exactSilkLevelRecoversAuthoritativeSpawnerOnly() {
        final SpawnerBreakPolicy.Decision decision = SpawnerBreakPolicy.decide(
            false, false, false, false, 1, 1, false, false,
            NonSilkRewardMode.ESSENCE_AND_CUSTOM_ITEM, true, true);
        assertTrue(decision.recoverSpawner());
        assertFalse(decision.awardEssence());
        assertFalse(decision.awardCustomItem());
    }

    @Test
    void insufficientSilkUsesEssenceMode() {
        final SpawnerBreakPolicy.Decision decision = decide(0, 1, NonSilkRewardMode.ESSENCE, true, true);
        assertFalse(decision.recoverSpawner());
        assertTrue(decision.awardEssence());
        assertFalse(decision.awardCustomItem());
    }

    @Test
    void customModeAwardsOnlyCustomItem() {
        final SpawnerBreakPolicy.Decision decision = decide(0, 1, NonSilkRewardMode.CUSTOM_ITEM, true, true);
        assertFalse(decision.recoverSpawner());
        assertFalse(decision.awardEssence());
        assertTrue(decision.awardCustomItem());
    }

    @Test
    void combinedModeAwardsSchemesIndependently() {
        final SpawnerBreakPolicy.Decision decision = decide(0, 1, NonSilkRewardMode.ESSENCE_AND_CUSTOM_ITEM, true, true);
        assertFalse(decision.recoverSpawner());
        assertTrue(decision.awardEssence());
        assertTrue(decision.awardCustomItem());
    }

    @Test
    void disabledSchemeDoesNotLeakThroughCombinedMode() {
        final SpawnerBreakPolicy.Decision decision = decide(0, 1, NonSilkRewardMode.ESSENCE_AND_CUSTOM_ITEM, true, false);
        assertTrue(decision.awardEssence());
        assertFalse(decision.awardCustomItem());
    }

    @Test
    void noneModeSuppressesAllRewards() {
        final SpawnerBreakPolicy.Decision decision = decide(0, 1, NonSilkRewardMode.NONE, true, true);
        assertFalse(decision.recoverSpawner());
        assertFalse(decision.awardEssence());
        assertFalse(decision.awardCustomItem());
    }

    @Test
    void bypassPermissionOnlyWorksWhenConfigured() {
        final SpawnerBreakPolicy.Decision denied = SpawnerBreakPolicy.decide(
            false, false, false, false, 0, 3, false, true,
            NonSilkRewardMode.ESSENCE, true, true);
        final SpawnerBreakPolicy.Decision allowed = SpawnerBreakPolicy.decide(
            false, false, false, false, 0, 3, true, true,
            NonSilkRewardMode.ESSENCE, true, true);
        assertFalse(denied.recoverSpawner());
        assertTrue(allowed.recoverSpawner());
    }

    @Test
    void creativeMatrixIsExplicitAndIndependent() {
        final SpawnerBreakPolicy.Decision decision = SpawnerBreakPolicy.decide(
            true, false, true, true, 10, 1, true, true,
            NonSilkRewardMode.NONE, true, true);
        assertFalse(decision.recoverSpawner());
        assertTrue(decision.awardEssence());
        assertTrue(decision.awardCustomItem());
    }

    private static SpawnerBreakPolicy.Decision decide(
        final int silk,
        final int required,
        final NonSilkRewardMode mode,
        final boolean essence,
        final boolean custom
    ) {
        return SpawnerBreakPolicy.decide(false, false, false, false, silk, required, false, false,
            mode, essence, custom);
    }
}
