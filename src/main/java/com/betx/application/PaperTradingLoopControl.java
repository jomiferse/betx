package com.betx.application;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Controls the lifecycle of the read-only continuous paper-trading loop. */
public abstract class PaperTradingLoopControl {
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public boolean shouldRunNextCycle() {
        return !stopRequested.get();
    }

    public void waitBeforeNextCycle(Duration pollInterval) {
    }

    public void requestStop() {
        stopRequested.set(true);
    }

    public boolean stopRequested() {
        return stopRequested.get();
    }

    public static PaperTradingLoopControl fixedCycles(int cycles) {
        AtomicInteger remaining = new AtomicInteger(cycles);
        return new PaperTradingLoopControl() {
            @Override
            public boolean shouldRunNextCycle() {
                return !stopRequested() && remaining.getAndDecrement() > 0;
            }
        };
    }

    public static PaperTradingLoopControl sleeping() {
        return new PaperTradingLoopControl() {
            @Override
            public void waitBeforeNextCycle(Duration pollInterval) {
                try {
                    Thread.sleep(pollInterval.toMillis());
                } catch (InterruptedException exc) {
                    Thread.currentThread().interrupt();
                    requestStop();
                }
            }
        };
    }
}
