package com.betx.domain.betfair;

public record BetfairSession(String token, String appKey) {
    public BetfairSession {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Betfair session token is required.");
        }
        if (appKey == null || appKey.isBlank()) {
            throw new IllegalArgumentException("Betfair app key is required.");
        }
    }
}
