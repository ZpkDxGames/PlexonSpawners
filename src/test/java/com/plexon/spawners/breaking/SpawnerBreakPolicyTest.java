package com.plexon.spawners.breaking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SpawnerBreakPolicyTest {
    @Test
    void normalSilkTouchRecovers() {
        assertEquals(SpawnerBreakPolicy.Outcome.RECOVER,
            SpawnerBreakPolicy.decide(false, false, false, 1, 1, false, false, true));
    }

    @Test
    void missingSilkAwardsEssence() {
        assertEquals(SpawnerBreakPolicy.Outcome.ESSENCE,
            SpawnerBreakPolicy.decide(false, false, false, 0, 1, false, false, true));
    }

    @Test
    void configurableMinimumAndBypassAreRespected() {
        assertEquals(SpawnerBreakPolicy.Outcome.ESSENCE,
            SpawnerBreakPolicy.decide(false, false, false, 1, 2, true, false, true));
        assertEquals(SpawnerBreakPolicy.Outcome.RECOVER,
            SpawnerBreakPolicy.decide(false, false, false, 1, 2, true, true, true));
    }

    @Test
    void creativeDefaultsToNoReward() {
        assertEquals(SpawnerBreakPolicy.Outcome.NONE,
            SpawnerBreakPolicy.decide(true, false, false, 10, 1, true, true, true));
    }
}
