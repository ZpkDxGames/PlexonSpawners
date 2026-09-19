package com.plexon.spawners.breaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SingularBreakParityTest {
    @Test
    void singularSilkBreakRecoversExactlyOneLogicalSpawner() {
        final SpawnerBreakPolicy.Decision decision = decide(false, 1, NonSilkRewardMode.ESSENCE_AND_CUSTOM_ITEM, true, true,
            false, false, false);
        assertTrue(decision.recoverSpawner());
        assertFalse(decision.awardEssence());
        assertFalse(decision.awardCustomItem());
        assertEquals(1, BreakReconciliation.confirmedRemovedAmount(1, 1, false, null));
    }

    @Test
    void singularNonSilkEssenceModeAwardsEssenceOnly() {
        final SpawnerBreakPolicy.Decision decision = decide(false, 0, NonSilkRewardMode.ESSENCE, true, true,
            false, false, false);
        assertFalse(decision.recoverSpawner());
        assertTrue(decision.awardEssence());
        assertFalse(decision.awardCustomItem());
    }

    @Test
    void singularNonSilkCustomModeAwardsCustomOnly() {
        final SpawnerBreakPolicy.Decision decision = decide(false, 0, NonSilkRewardMode.CUSTOM_ITEM, true, true,
            false, false, false);
        assertFalse(decision.recoverSpawner());
        assertFalse(decision.awardEssence());
        assertTrue(decision.awardCustomItem());
    }

    @Test
    void singularNonSilkBothModeAwardsBothWithoutSpawner() {
        final SpawnerBreakPolicy.Decision decision = decide(false, 0, NonSilkRewardMode.ESSENCE_AND_CUSTOM_ITEM, true, true,
            false, false, false);
        assertFalse(decision.recoverSpawner());
        assertTrue(decision.awardEssence());
        assertTrue(decision.awardCustomItem());
    }

    @Test
    void singularNoneModeAwardsNothingAndDoesNotRecover() {
        final SpawnerBreakPolicy.Decision decision = decide(false, 0, NonSilkRewardMode.NONE, true, true,
            false, false, false);
        assertFalse(decision.recoverSpawner());
        assertFalse(decision.awardEssence());
        assertFalse(decision.awardCustomItem());
    }

    @Test
    void singularCreativePolicyIsExplicit() {
        final SpawnerBreakPolicy.Decision decision = decide(true, 0, NonSilkRewardMode.NONE, true, true,
            true, true, true);
        assertTrue(decision.recoverSpawner());
        assertTrue(decision.awardEssence());
        assertTrue(decision.awardCustomItem());
    }

    @Test
    void singularCancelledOrUnchangedBreakPaysNothing() {
        assertEquals(0, BreakReconciliation.confirmedRemovedAmount(1, 0, true, 1));
    }

    @Test
    void twoToOneThenOneToZeroBothConfirmOneLogicalRemoval() {
        assertEquals(1, BreakReconciliation.confirmedRemovedAmount(2, 1, true, 1));
        assertEquals(1, BreakReconciliation.confirmedRemovedAmount(1, 1, false, null));
    }

    private static SpawnerBreakPolicy.Decision decide(
        final boolean creative,
        final int silk,
        final NonSilkRewardMode mode,
        final boolean essenceEnabled,
        final boolean customEnabled,
        final boolean creativeRecover,
        final boolean creativeEssence,
        final boolean creativeCustom
    ) {
        return SpawnerBreakPolicy.decide(
            creative,
            creativeRecover,
            creativeEssence,
            creativeCustom,
            silk,
            1,
            false,
            false,
            mode,
            essenceEnabled,
            customEnabled);
    }
}
