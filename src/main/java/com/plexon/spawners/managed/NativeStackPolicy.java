package com.plexon.spawners.managed;

/** Pure native-stack math kept free of Bukkit state so transactions are easy to test. */
public final class NativeStackPolicy {
    private NativeStackPolicy() {}

    public record MergeResult(int targetAmount, int remainder, int mergedAmount) {}

    public static MergeResult merge(final int current, final int incoming, final int maximum) {
        if (current < 1 || incoming < 1 || maximum < 1) {
            throw new IllegalArgumentException("stack amounts and maximum must be positive");
        }
        if (current > maximum) {
            return new MergeResult(current, incoming, 0);
        }
        final int capacity = maximum - current;
        final int merged = Math.min(capacity, incoming);
        return new MergeResult(current + merged, incoming - merged, merged);
    }

    public static int requestedLogicalOutput(
        final int tierSpawnCount,
        final int stackAmount,
        final int cycleCap,
        final boolean scaleWithStack
    ) {
        if (tierSpawnCount < 1 || stackAmount < 1 || cycleCap < 1) {
            throw new IllegalArgumentException("spawn values must be positive");
        }
        final long requested = scaleWithStack
            ? (long) tierSpawnCount * stackAmount
            : tierSpawnCount;
        return (int) Math.min(cycleCap, Math.min(Integer.MAX_VALUE, requested));
    }

    public static int allowedLogicalOutput(
        final int requested,
        final int currentNearby,
        final int nearbyMaximum,
        final boolean respectNearbyCap
    ) {
        if (requested < 0) {
            throw new IllegalArgumentException("requested must be non-negative");
        }
        if (!respectNearbyCap) {
            return requested;
        }
        final int remaining = Math.max(0, nearbyMaximum - Math.max(0, currentNearby));
        return Math.min(requested, remaining);
    }

    public static long upgradeCost(final int baseCost, final int stackAmount) {
        if (baseCost < 0 || stackAmount < 1) {
            throw new IllegalArgumentException("invalid upgrade cost inputs");
        }
        return Math.multiplyExact((long) baseCost, (long) stackAmount);
    }

    public static boolean compatible(
        final ManagedSpawner left,
        final ManagedSpawner right,
        final boolean sameType,
        final boolean sameOwner,
        final boolean sameTier,
        final boolean sameAccess
    ) {
        if (left == null || right == null) {
            return false;
        }
        return (!sameType || left.type() == right.type())
            && (!sameOwner || left.ownerId().equals(right.ownerId()))
            && (!sameTier || left.tier() == right.tier())
            && (!sameAccess || left.access() == right.access());
    }
}
