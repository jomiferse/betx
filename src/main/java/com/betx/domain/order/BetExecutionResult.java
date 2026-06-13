package com.betx.domain.order;

/** Result from an order execution adapter. */
public record BetExecutionResult(boolean accepted, String message, String externalOrderId) {
    public BetExecutionResult {
        message = message == null ? "" : message;
        externalOrderId = externalOrderId == null ? null : externalOrderId.strip();
    }

    public BetExecutionResult(boolean accepted, String message) {
        this(accepted, message, null);
    }

    public static BetExecutionResult rejected(String message) {
        return new BetExecutionResult(false, message, null);
    }
}
