package com.plexon.spawners.breaking;

public final class BreakReconciliation {
    private BreakReconciliation() {}

    /**
     * Returns the logical amount that can safely be considered removed after the authoritative
     * WildStacker mutation window. Zero means the break must be treated as denied/unchanged.
     *
     * @param beforeAmount pre-break WildStacker logical amount
     * @param authoritativeRemoved amount reported by SpawnerUnstackEvent, or zero when absent
     * @param blockStillSpawner whether a physical spawner still exists at the captured location
     * @param currentAmount current WildStacker amount, or null when the block/stack no longer resolves
     */
    public static int confirmedRemovedAmount(
        final int beforeAmount,
        final int authoritativeRemoved,
        final boolean blockStillSpawner,
        final Integer currentAmount
    ) {
        if (beforeAmount < 1) return 0;

        if (authoritativeRemoved > 0) {
            if (authoritativeRemoved > beforeAmount) return 0;
            final int expectedAfter = beforeAmount - authoritativeRemoved;

            if (currentAmount != null) {
                return currentAmount <= expectedAfter ? authoritativeRemoved : 0;
            }

            return !blockStillSpawner && expectedAfter == 0 ? authoritativeRemoved : 0;
        }

        if (currentAmount != null) {
            if (currentAmount < 0 || currentAmount >= beforeAmount) return 0;
            return beforeAmount - currentAmount;
        }

        if (!blockStillSpawner) {
            return beforeAmount;
        }

        return 0;
    }
}
