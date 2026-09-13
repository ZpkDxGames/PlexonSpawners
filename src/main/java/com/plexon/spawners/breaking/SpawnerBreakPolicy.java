package com.plexon.spawners.breaking;

public final class SpawnerBreakPolicy {
    public enum Outcome {
        RECOVER,
        ESSENCE,
        NONE
    }

    private SpawnerBreakPolicy() {}

    public static Outcome decide(
        final boolean creative,
        final boolean creativeRecover,
        final boolean creativeEssence,
        final int silkLevel,
        final int requiredSilkLevel,
        final boolean bypassEnabled,
        final boolean hasBypassPermission,
        final boolean essenceEnabled
    ) {
        if (creative) {
            if (creativeRecover) return Outcome.RECOVER;
            if (creativeEssence && essenceEnabled) return Outcome.ESSENCE;
            return Outcome.NONE;
        }

        final boolean silkQualified = silkLevel >= Math.max(0, requiredSilkLevel)
            || (bypassEnabled && hasBypassPermission);
        if (silkQualified) return Outcome.RECOVER;
        return essenceEnabled ? Outcome.ESSENCE : Outcome.NONE;
    }
}
