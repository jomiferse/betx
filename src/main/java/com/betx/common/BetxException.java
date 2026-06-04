package com.betx.common;

public class BetxException extends RuntimeException {
    public BetxException(String message) {
        super(message);
    }

    public BetxException(String message, Throwable cause) {
        super(message, cause);
    }
}
