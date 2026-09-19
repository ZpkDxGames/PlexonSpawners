package com.plexon.spawners.breaking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class BreakReconciliationTest {
    @Test
    void singularIntentWithoutWildStackerEventConfirmsRemovalOnlyWhenBlockIsGone() {
        assertEquals(1, BreakReconciliation.confirmedRemovedAmount(1, 0, false, null));
        assertEquals(0, BreakReconciliation.confirmedRemovedAmount(1, 0, true, 1));
    }

    @Test
    void authoritativeUnstackAmountControlsTwoToOneTransition() {
        assertEquals(1, BreakReconciliation.confirmedRemovedAmount(2, 1, true, 1));
        assertEquals(0, BreakReconciliation.confirmedRemovedAmount(2, 1, true, 2));
    }

    @Test
    void authoritativeFinalOneToZeroTransitionConfirmsExactlyOne() {
        assertEquals(1, BreakReconciliation.confirmedRemovedAmount(1, 1, false, null));
    }

    @Test
    void noEventFallbackMayUseWildStackerBeforeAfterDelta() {
        assertEquals(1, BreakReconciliation.confirmedRemovedAmount(2, 0, true, 1));
        assertEquals(2, BreakReconciliation.confirmedRemovedAmount(2, 0, false, null));
    }

    @Test
    void partialAuthoritativeRemovalFailsClosedWhenExpectedRemainderDisappears() {
        assertEquals(0, BreakReconciliation.confirmedRemovedAmount(2, 1, false, null));
    }

    @Test
    void impossibleAmountsNeverFinalize() {
        assertEquals(0, BreakReconciliation.confirmedRemovedAmount(1, 2, false, null));
        assertEquals(0, BreakReconciliation.confirmedRemovedAmount(0, 0, false, null));
    }
}
