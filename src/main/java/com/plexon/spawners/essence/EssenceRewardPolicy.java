package com.plexon.spawners.essence;

public final class EssenceRewardPolicy {
    @FunctionalInterface
    public interface RandomSource {
        double nextPercentage();
    }

    public record Award(int logicalUnits, int rolls, int successes, int amountPerSuccess, int totalAmount) {
        public boolean awarded() {
            return totalAmount > 0;
        }
    }

    private EssenceRewardPolicy() {}

    public static Award evaluate(
        final int logicalUnits,
        final int amountPerSuccess,
        final double chance,
        final RandomSource random
    ) {
        if (logicalUnits < 1) throw new IllegalArgumentException("logicalUnits must be positive");
        if (amountPerSuccess < 1) throw new IllegalArgumentException("amountPerSuccess must be positive");
        if (random == null) throw new IllegalArgumentException("random source is required");

        final double safeChance = Double.isFinite(chance) ? Math.max(0.0D, Math.min(100.0D, chance)) : 0.0D;
        int successes = 0;
        if (safeChance >= 100.0D) {
            successes = logicalUnits;
        } else if (safeChance > 0.0D) {
            for (int index = 0; index < logicalUnits; index++) {
                final double roll = random.nextPercentage();
                if (Double.isFinite(roll) && roll >= 0.0D && roll < safeChance) successes++;
            }
        }

        final long total = (long) successes * amountPerSuccess;
        final int totalAmount = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        return new Award(logicalUnits, logicalUnits, successes, amountPerSuccess, totalAmount);
    }
}
