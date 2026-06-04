package com.betx.domain.order;

/** Result from a future order execution adapter. */
public record BetExecutionResult(boolean accepted, String message) {
    public BetExecutionResult {
        message = message == null ? "" : message;
    }

    public static BetExecutionResult rejected(String message) {
        return new BetExecutionResult(false, message);
    }
}
