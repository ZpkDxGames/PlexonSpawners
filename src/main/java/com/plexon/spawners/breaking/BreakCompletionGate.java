package com.plexon.spawners.breaking;

import java.util.concurrent.atomic.AtomicReference;

public final class BreakCompletionGate {
    public enum Outcome {
        RECOVERED,
        REWARDED,
        NO_REWARD_POLICY,
        DENIED_OR_UNCHANGED,
        ABORTED
    }

    private final AtomicReference<Outcome> outcome = new AtomicReference<>();

    public boolean complete(final Outcome terminalOutcome) {
        if (terminalOutcome == null) throw new IllegalArgumentException("terminalOutcome");
        return outcome.compareAndSet(null, terminalOutcome);
    }

    public boolean isComplete() {
        return outcome.get() != null;
    }

    public Outcome outcome() {
        return outcome.get();
    }
}
