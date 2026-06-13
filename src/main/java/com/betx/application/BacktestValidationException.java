package com.betx.application;

/** Raised when historical backtest input cannot be used safely. */
public class BacktestValidationException extends RuntimeException {
    public BacktestValidationException(String message) {
        super(message);
    }

    public BacktestValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
