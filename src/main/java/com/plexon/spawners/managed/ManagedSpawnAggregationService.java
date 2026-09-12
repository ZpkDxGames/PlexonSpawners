package com.plexon.spawners.managed;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure policy for one managed-spawner logical output cycle. Bukkit/provider
 * mutation is deliberately kept outside this class so cap and target-selection
 * semantics can be tested without a live server.
 */
public final class ManagedSpawnAggregationService {
    private ManagedSpawnAggregationService() {}

    public record CyclePlan(
        int requested,
        int nearbyLogicalAmount,
        int nearbyRemainingCapacity,
        int allowedContribution
    ) {}

    public record Candidate(
        UUID id,
        double distanceSquared,
        int logicalAmount,
        boolean compatible
    ) {
        public Candidate {
            Objects.requireNonNull(id, "id");
            if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D) {
                throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
            }
            if (logicalAmount < 1) {
                throw new IllegalArgumentException("logicalAmount must be positive");
            }
        }
    }

    public static CyclePlan plan(
        final int requested,
        final int nearbyLogicalAmount,
        final int maximumNearbyAmount,
        final boolean respectNearbyCap
    ) {
        final int safeRequested = Math.max(0, requested);
        final int safeNearby = Math.max(0, nearbyLogicalAmount);
        if (!respectNearbyCap) {
            return new CyclePlan(safeRequested, safeNearby, Integer.MAX_VALUE, safeRequested);
        }
        final int safeMaximum = Math.max(0, maximumNearbyAmount);
        final int remaining = safeNearby >= safeMaximum ? 0 : safeMaximum - safeNearby;
        return new CyclePlan(safeRequested, safeNearby, remaining, Math.min(safeRequested, remaining));
    }

    /** Nearest compatible target wins; UUID provides a stable deterministic tie-break. */
    public static Optional<Candidate> chooseTarget(final Collection<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
            .filter(Objects::nonNull)
            .filter(Candidate::compatible)
            .min(Comparator.comparingDouble(Candidate::distanceSquared)
                .thenComparing(Candidate::id));
    }

    public static int saturatingMultiply(final int left, final int right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        final long product = (long) left * (long) right;
        return product > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) product;
    }
}
