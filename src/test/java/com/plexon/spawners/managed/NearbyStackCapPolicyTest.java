package com.plexon.spawners.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NearbyStackCapPolicyTest {
    @Test
    void blocksAtOrAboveMaximum() {
        assertEquals(
            NearbyStackCapPolicy.Decision.BLOCKED,
            NearbyStackCapPolicy.decide(99, 99, 1, true)
        );
        assertEquals(
            NearbyStackCapPolicy.Decision.BLOCKED,
            NearbyStackCapPolicy.decide(120, 99, 1, true)
        );
    }

    @Test
    void usesFastPathWhenWholeContributionFits() {
        assertEquals(
            NearbyStackCapPolicy.Decision.FAST_PATH,
            NearbyStackCapPolicy.decide(97, 99, 2, true)
        );
        assertEquals(2, NearbyStackCapPolicy.remainingCapacity(97, 99));
    }

    @Test
    void usesGranularPathInsteadOfOvershooting() {
        assertEquals(
            NearbyStackCapPolicy.Decision.GRANULAR,
            NearbyStackCapPolicy.decide(97, 99, 7, true)
        );
        assertEquals(2, NearbyStackCapPolicy.remainingCapacity(97, 99));
    }

    @Test
    void resumesBelowMaximum() {
        assertEquals(
            NearbyStackCapPolicy.Decision.FAST_PATH,
            NearbyStackCapPolicy.decide(84, 99, 7, true)
        );
    }

    @Test
    void failsClosedWhenProviderIsUnavailable() {
        assertEquals(
            NearbyStackCapPolicy.Decision.FAIL_CLOSED,
            NearbyStackCapPolicy.decide(1, 99, 1, false)
        );
    }

    @Test
    void maximumCycleContributionUsesSpawnerStackAmount() {
        assertEquals(7L, NearbyStackCapPolicy.maximumCycleContribution(7, 1));
        assertEquals(140L, NearbyStackCapPolicy.maximumCycleContribution(7, 20));
    }

    @Test
    void logicalAmountsAccumulateAndClampAtCap() {
        assertEquals(1, NearbyStackCapPolicy.accumulateLogicalAmount(0, 1, 99));
        assertEquals(99, NearbyStackCapPolicy.accumulateLogicalAmount(0, 99, 99));

        int total = NearbyStackCapPolicy.accumulateLogicalAmount(0, 70, 99);
        total = NearbyStackCapPolicy.accumulateLogicalAmount(total, 20, 99);
        total = NearbyStackCapPolicy.accumulateLogicalAmount(total, 9, 99);
        assertEquals(99, total);

        assertEquals(99, NearbyStackCapPolicy.accumulateLogicalAmount(total, 50, 99));
    }

    @Test
    void sameTypeFilterIgnoresDifferentTypesByDefault() {
        assertTrue(NearbyStackCapPolicy.contributes(true, true));
        assertFalse(NearbyStackCapPolicy.contributes(true, false));
        assertTrue(NearbyStackCapPolicy.contributes(false, false));
    }

    @Test
    void overlappingManagedSpawnersIndependentlyObserveSameCap() {
        final NearbyStackCapPolicy.Decision first = NearbyStackCapPolicy.decide(99, 99, 7, true);
        final NearbyStackCapPolicy.Decision second = NearbyStackCapPolicy.decide(99, 99, 140, true);
        assertEquals(NearbyStackCapPolicy.Decision.BLOCKED, first);
        assertEquals(NearbyStackCapPolicy.Decision.BLOCKED, second);
    }
}
