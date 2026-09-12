package com.plexon.spawners.compat;

import org.bukkit.entity.LivingEntity;

/**
 * Safe fallback when no logical entity-stack provider is available. Every
 * physical Bukkit entity represents exactly one logical entity.
 */
public final class PhysicalFallbackBackend implements EntityStackBackend {
    public static final PhysicalFallbackBackend INSTANCE = new PhysicalFallbackBackend();

    private PhysicalFallbackBackend() {}

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String backendName() {
        return "physical-fallback";
    }

    @Override
    public LogicalAmount logicalAmount(final LivingEntity entity) {
        return LogicalAmount.available(1);
    }

    @Override
    public boolean compatible(final LivingEntity source, final LivingEntity target, final int contribution) {
        return false;
    }

    @Override
    public MutationResult mergeInto(
        final LivingEntity source,
        final LivingEntity target,
        final int contribution
    ) {
        return MutationResult.UNAVAILABLE;
    }

    @Override
    public MutationResult setLogicalAmount(final LivingEntity entity, final int amount) {
        return amount == 1 ? MutationResult.SUCCESS : MutationResult.UNAVAILABLE;
    }

    @Override
    public String status() {
        return "bounded physical fallback";
    }
}
