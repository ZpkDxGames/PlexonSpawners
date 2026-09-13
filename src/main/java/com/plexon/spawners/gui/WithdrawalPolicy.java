package com.plexon.spawners.gui;

public final class WithdrawalPolicy {
    private WithdrawalPolicy() {}

    public static int maximumWithdrawable(final int currentStackAmount) {
        return Math.max(0, currentStackAmount - 1);
    }

    public static boolean isValidRequest(final int currentStackAmount, final int requestedAmount) {
        return requestedAmount > 0 && requestedAmount <= maximumWithdrawable(currentStackAmount);
    }
}
