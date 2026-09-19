package com.plexon.spawners.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;

final class RewardRollPolicyTest {
    @Test
    void customRewardUsesIndependentLogicalRolls() {
        final Queue<Double> rolls = new ArrayDeque<>(java.util.List.of(0.0, 30.0, 14.9, 15.0, 99.0));
        final RewardRollPolicy.Award award = RewardRollPolicy.evaluate(5, 2, 15.0, rolls::remove);
        assertEquals(5, award.rolls());
        assertEquals(2, award.successes());
        assertEquals(4L, award.totalAmount());
    }

    @Test
    void clampsChanceAndValidatesArguments() {
        assertEquals(0L, RewardRollPolicy.evaluate(3, 1, Double.NaN, () -> 0.0).totalAmount());
        assertEquals(3L, RewardRollPolicy.evaluate(3, 1, 150.0, () -> 99.0).totalAmount());
        assertThrows(IllegalArgumentException.class, () -> RewardRollPolicy.evaluate(1, 0, 100.0, () -> 0.0));
    }
}
