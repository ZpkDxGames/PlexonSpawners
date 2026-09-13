package com.plexon.spawners.breaking;

public final class SpawnerBreakPolicy {
    public record Decision(boolean recoverSpawner, boolean awardEssence, boolean awardCustomItem) {
        public boolean suppressNativeDrop() {
            return !recoverSpawner;
        }
    }

    private SpawnerBreakPolicy() {}

    public static Decision decide(
        final boolean creative,
        final boolean creativeRecover,
        final boolean creativeEssence,
        final boolean creativeCustomItem,
        final int silkLevel,
        final int requiredSilkLevel,
        final boolean bypassEnabled,
        final boolean hasBypassPermission,
        final NonSilkRewardMode nonSilkMode,
        final boolean essenceEnabled,
        final boolean customItemEnabled
    ) {
        if (creative) {
            return new Decision(
                creativeRecover,
                creativeEssence && essenceEnabled,
                creativeCustomItem && customItemEnabled);
        }

        final boolean silkQualified = silkLevel >= Math.max(0, requiredSilkLevel)
            || (bypassEnabled && hasBypassPermission);
        if (silkQualified) return new Decision(true, false, false);

        final NonSilkRewardMode mode = nonSilkMode == null ? NonSilkRewardMode.NONE : nonSilkMode;
        return new Decision(
            false,
            essenceEnabled && mode.awardsEssence(),
            customItemEnabled && mode.awardsCustomItem());
    }
}
