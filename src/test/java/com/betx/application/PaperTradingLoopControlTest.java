package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PaperTradingLoopControlTest {

    @Test
    void sleepingControlTreatsExternalInterruptAsWakeupWithoutStoppingLoop() {
        PaperTradingLoopControl control = PaperTradingLoopControl.sleeping();

        Thread.currentThread().interrupt();
        control.waitBeforeNextCycle(Duration.ofSeconds(60));

        assertThat(control.stopRequested()).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void sleepingControlPreservesRequestedStopWhenInterruptedDuringShutdown() {
        PaperTradingLoopControl control = PaperTradingLoopControl.sleeping();

        control.requestStop();
        Thread.currentThread().interrupt();
        control.waitBeforeNextCycle(Duration.ofSeconds(60));

        assertThat(control.stopRequested()).isTrue();
        assertThat(Thread.interrupted()).isTrue();
    }
}
