package com.betx.domain.betfair;

public record BetfairCredentials(String username, String password, String appKey, BetfairCountry country) {
    public BetfairCredentials(String username, String password, String appKey) {
        this(username, password, appKey, null);
    }

    public BetfairCredentials {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Betfair username is required.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Betfair password is required.");
        }
        if (appKey == null || appKey.isBlank()) {
            throw new IllegalArgumentException("Betfair app key is required.");
        }
        country = country == null ? BetfairCountry.SPAIN : country;
    }
}
