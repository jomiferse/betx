package com.betx.domain.order;

/** Current state of a live bet intent. */
public enum BetIntentStage {
    AWAITING_CONFIRMATION,
    AWAITING_STAKE,
    EXECUTED,
    SETTLED,
    CANCELLED,
    FAILED;

    public boolean isActive() {
        return this == AWAITING_CONFIRMATION || this == AWAITING_STAKE;
    }
}
