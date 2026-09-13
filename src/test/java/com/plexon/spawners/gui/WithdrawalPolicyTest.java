package com.plexon.spawners.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WithdrawalPolicyTest {
    @Test
    void finalPlacedSpawnerCannotBeWithdrawn() {
        assertEquals(0, WithdrawalPolicy.maximumWithdrawable(1));
        assertFalse(WithdrawalPolicy.isValidRequest(1, 1));
    }

    @Test
    void expectedPresetsRespectLiveAmount() {
        assertTrue(WithdrawalPolicy.isValidRequest(2, 1));
        assertTrue(WithdrawalPolicy.isValidRequest(9, 8));
        assertTrue(WithdrawalPolicy.isValidRequest(17, 16));
        assertFalse(WithdrawalPolicy.isValidRequest(17, 17));
        assertFalse(WithdrawalPolicy.isValidRequest(17, 0));
    }
}
