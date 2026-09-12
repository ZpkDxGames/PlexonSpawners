package com.plexon.spawners.compat;

import org.bukkit.entity.LivingEntity;

/**
 * Runtime contract for representing and aggregating logical entity stacks.
 * Implementations must not own spawner blocks or spawner stack amounts.
 */
public interface EntityStackBackend {
    enum MutationResult {
        SUCCESS,
        INCOMPATIBLE,
        UNAVAILABLE
    }

    record LogicalAmount(boolean available, int amount) {
        public static LogicalAmount available(final int amount) {
            return new LogicalAmount(true, Math.max(1, amount));
        }

        public static LogicalAmount unavailable() {
            return new LogicalAmount(false, 0);
        }
    }

    /** Whether this backend can currently represent logical entity amounts. */
    boolean available();

    /** Human-readable backend identifier for diagnostics. */
    String backendName();

    LogicalAmount logicalAmount(LivingEntity entity);

    /**
     * Checks whether {@code source}'s logical contribution may be merged into
     * {@code target} without bypassing the provider's compatibility/limit rules.
     */
    boolean compatible(LivingEntity source, LivingEntity target, int contribution);

    /**
     * Adds the contribution to target and removes the source representation only
     * after the target mutation succeeds. Implementations must roll back on a
     * source-removal failure whenever the provider API allows it.
     */
    MutationResult mergeInto(LivingEntity source, LivingEntity target, int contribution);

    MutationResult setLogicalAmount(LivingEntity entity, int amount);

    String status();
}
