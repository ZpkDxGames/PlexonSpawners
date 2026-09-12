package com.plexon.spawners.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class EssenceRewardPolicyTest {
    @Test
    void zeroPercentNeverAwards() {
        final AtomicInteger calls = new AtomicInteger();
        final var award = EssenceRewardPolicy.evaluate(8, 2, 0.0D, () -> {
            calls.incrementAndGet();
            return 0.0D;
        });
        assertFalse(award.awarded());
        assertEquals(8, award.rolls());
        assertEquals(0, award.successes());
        assertEquals(0, award.totalAmount());
        assertEquals(0, calls.get());
    }

    @Test
    void hundredPercentAwardsEveryLogicalUnit() {
        final var award = EssenceRewardPolicy.evaluate(8, 2, 100.0D, () -> 99.9D);
        assertTrue(award.awarded());
        assertEquals(8, award.rolls());
        assertEquals(8, award.successes());
        assertEquals(16, award.totalAmount());
    }

    @Test
    void deterministicPartialRollsAggregateBeforeDelivery() {
        final double[] rolls = {5.0D, 50.0D, 9.0D, 99.0D, 10.0D};
        final AtomicInteger index = new AtomicInteger();
        final var award = EssenceRewardPolicy.evaluate(5, 3, 10.0D,
            () -> rolls[index.getAndIncrement()]);
        assertEquals(5, award.rolls());
        assertEquals(2, award.successes());
        assertEquals(6, award.totalAmount());
    }

    @Test
    void oneBreakAndAllBreakUseRemovedLogicalQuantity() {
        final var one = EssenceRewardPolicy.evaluate(1, 4, 100.0D, () -> 0.0D);
        final var all = EssenceRewardPolicy.evaluate(8, 4, 100.0D, () -> 0.0D);
        assertEquals(4, one.totalAmount());
        assertEquals(32, all.totalAmount());
    }

    @Test
    void invalidRandomValuesDoNotAccidentallyWin() {
        final var negative = EssenceRewardPolicy.evaluate(1, 1, 50.0D, () -> -1.0D);
        final var nan = EssenceRewardPolicy.evaluate(1, 1, 50.0D, () -> Double.NaN);
        assertEquals(0, negative.totalAmount());
        assertEquals(0, nan.totalAmount());
    }
}
