package com.plexon.spawners.essence;

import com.plexon.spawners.reward.RewardRollPolicy;

public final class EssenceRewardPolicy {
    @FunctionalInterface
    public interface RandomSource {
        double nextPercentage();
    }

    private EssenceRewardPolicy() {}

    public static RewardRollPolicy.Award evaluate(
        final int logicalUnits,
        final int amountPerSuccess,
        final double chance,
        final RandomSource random
    ) {
        return RewardRollPolicy.evaluate(logicalUnits, amountPerSuccess, chance, random::nextPercentage);
    }
}
