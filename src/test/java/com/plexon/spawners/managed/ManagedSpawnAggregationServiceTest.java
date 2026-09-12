package com.plexon.spawners.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ManagedSpawnAggregationServiceTest {
    @Test
    void exactNearbyCapAllowsOnlyRemainingCapacity() {
        final ManagedSpawnAggregationService.CyclePlan plan =
            ManagedSpawnAggregationService.plan(8, 95, 99, true);
        assertEquals(8, plan.requested());
        assertEquals(95, plan.nearbyLogicalAmount());
        assertEquals(4, plan.nearbyRemainingCapacity());
        assertEquals(4, plan.allowedContribution());
    }

    @Test
    void fullNearbyCapBlocksContribution() {
        assertEquals(0, ManagedSpawnAggregationService.plan(8, 99, 99, true).allowedContribution());
        assertEquals(0, ManagedSpawnAggregationService.plan(8, 120, 99, true).allowedContribution());
    }

    @Test
    void disabledNearbyCapLeavesRequestUntouched() {
        assertEquals(64, ManagedSpawnAggregationService.plan(64, 500, 99, false).allowedContribution());
    }

    @Test
    void nearestCompatibleTargetWins() {
        final UUID far = UUID.fromString("00000000-0000-0000-0000-000000000010");
        final UUID near = UUID.fromString("00000000-0000-0000-0000-000000000020");
        final var selected = ManagedSpawnAggregationService.chooseTarget(List.of(
            new ManagedSpawnAggregationService.Candidate(far, 9.0D, 20, true),
            new ManagedSpawnAggregationService.Candidate(near, 1.0D, 40, true)
        ));
        assertTrue(selected.isPresent());
        assertEquals(near, selected.orElseThrow().id());
    }

    @Test
    void incompatibleNearestTargetIsIgnored() {
        final UUID incompatible = UUID.fromString("00000000-0000-0000-0000-000000000001");
        final UUID compatible = UUID.fromString("00000000-0000-0000-0000-000000000002");
        final var selected = ManagedSpawnAggregationService.chooseTarget(List.of(
            new ManagedSpawnAggregationService.Candidate(incompatible, 0.25D, 10, false),
            new ManagedSpawnAggregationService.Candidate(compatible, 4.0D, 10, true)
        ));
        assertEquals(compatible, selected.orElseThrow().id());
    }

    @Test
    void uuidProvidesStableDistanceTieBreak() {
        final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        final var selected = ManagedSpawnAggregationService.chooseTarget(List.of(
            new ManagedSpawnAggregationService.Candidate(second, 1.0D, 5, true),
            new ManagedSpawnAggregationService.Candidate(first, 1.0D, 5, true)
        ));
        assertEquals(first, selected.orElseThrow().id());
    }

    @Test
    void noCompatibleTargetProducesEmptySelection() {
        final var selected = ManagedSpawnAggregationService.chooseTarget(List.of(
            new ManagedSpawnAggregationService.Candidate(UUID.randomUUID(), 1.0D, 1, false)
        ));
        assertTrue(selected.isEmpty());
    }

    @Test
    void multiplicationSaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE,
            ManagedSpawnAggregationService.saturatingMultiply(Integer.MAX_VALUE, 2));
        assertEquals(64, ManagedSpawnAggregationService.saturatingMultiply(8, 8));
    }
}
