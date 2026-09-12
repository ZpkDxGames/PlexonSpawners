package com.plexon.spawners.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
