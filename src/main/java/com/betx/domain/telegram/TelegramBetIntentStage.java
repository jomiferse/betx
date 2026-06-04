package com.betx.domain.telegram;

/** Current state of a pending live bet confirmation. */
public enum TelegramBetIntentStage {
    AWAITING_CONFIRMATION,
    AWAITING_STAKE,
    EXECUTED,
    CANCELLED,
    FAILED;

    public boolean isActive() {
        return this == AWAITING_CONFIRMATION || this == AWAITING_STAKE;
    }
}
