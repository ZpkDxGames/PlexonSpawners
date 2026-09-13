package com.plexon.spawners.essence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.plexon.spawners.reward.RewardRollPolicy;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;

final class EssenceRewardPolicyTest {
    @Test
    void performsOneRollPerLogicalSpawnerAndAggregates() {
        final Queue<Double> rolls = new ArrayDeque<>(java.util.List.of(10.0, 70.0, 20.0, 99.0));
        final RewardRollPolicy.Award award = EssenceRewardPolicy.evaluate(4, 2, 25.0, rolls::remove);
        assertEquals(4, award.rolls());
        assertEquals(2, award.successes());
        assertEquals(4L, award.totalAmount());
    }

    @Test
    void handlesZeroAndHundredPercentDeterministically() {
        assertEquals(0L, EssenceRewardPolicy.evaluate(8, 3, 0.0, () -> 0.0).totalAmount());
        assertEquals(24L, EssenceRewardPolicy.evaluate(8, 3, 100.0, () -> 99.9).totalAmount());
    }

    @Test
    void preservesLargeAggregatesAsLong() {
        assertEquals(4_611_686_014_132_420_609L,
            EssenceRewardPolicy.evaluate(Integer.MAX_VALUE, Integer.MAX_VALUE, 100.0, () -> 0.0).totalAmount());
    }

    @Test
    void rejectsInvalidLogicalAmounts() {
        assertThrows(IllegalArgumentException.class,
            () -> EssenceRewardPolicy.evaluate(0, 1, 50.0, () -> 10.0));
    }
}
