package com.plexon.spawners.breaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BreakCompletionGateTest {
    @Test
    void firstTerminalSignalWinsExactlyOnce() {
        final BreakCompletionGate gate = new BreakCompletionGate();

        assertNull(gate.outcome());
        assertFalse(gate.isComplete());
        assertTrue(gate.complete(BreakCompletionGate.Outcome.RECOVERED));
        assertFalse(gate.complete(BreakCompletionGate.Outcome.REWARDED));
        assertFalse(gate.complete(BreakCompletionGate.Outcome.DENIED_OR_UNCHANGED));
        assertEquals(BreakCompletionGate.Outcome.RECOVERED, gate.outcome());
        assertTrue(gate.isComplete());
    }

    @Test
    void deniedTransactionCannotLaterPayFallback() {
        final BreakCompletionGate gate = new BreakCompletionGate();

        assertTrue(gate.complete(BreakCompletionGate.Outcome.DENIED_OR_UNCHANGED));
        assertFalse(gate.complete(BreakCompletionGate.Outcome.REWARDED));
        assertEquals(BreakCompletionGate.Outcome.DENIED_OR_UNCHANGED, gate.outcome());
    }
}
