package com.plexon.spawners.managed;

/** Pure decision policy for the nearby logical stack cap. */
public final class NearbyStackCapPolicy {
    public enum Decision {
        FAST_PATH,
        GRANULAR,
        BLOCKED,
        FAIL_CLOSED
    }

    private NearbyStackCapPolicy() {}

    public static Decision decide(
        final int logicalAmount,
        final int maximumAmount,
        final long maximumCycleContribution,
        final boolean providerAvailable
    ) {
        if (!providerAvailable) {
            return Decision.FAIL_CLOSED;
        }
        if (logicalAmount >= maximumAmount) {
            return Decision.BLOCKED;
        }
        final long remaining = (long) maximumAmount - logicalAmount;
        return maximumCycleContribution <= remaining ? Decision.FAST_PATH : Decision.GRANULAR;
    }

    public static int remainingCapacity(final int logicalAmount, final int maximumAmount) {
        return Math.max(0, maximumAmount - Math.max(0, logicalAmount));
    }

    public static long maximumCycleContribution(final int spawnCount, final int spawnerStackAmount) {
        final long boundedSpawnCount = Math.max(1, spawnCount);
        final long boundedStackAmount = Math.max(1, spawnerStackAmount);
        final long product = boundedSpawnCount * boundedStackAmount;
        return Math.min(Integer.MAX_VALUE, product);
    }

    public static int accumulateLogicalAmount(
        final int currentLogicalAmount,
        final int entityLogicalAmount,
        final int maximumAmount
    ) {
        final long current = Math.max(0, currentLogicalAmount);
        final long contribution = Math.max(1, entityLogicalAmount);
        return (int) Math.min(Math.max(1, maximumAmount), current + contribution);
    }

    public static boolean contributes(final boolean sameTypeOnly, final boolean sameEntityType) {
        return !sameTypeOnly || sameEntityType;
    }
}
