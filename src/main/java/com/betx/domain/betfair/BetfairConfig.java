package com.betx.domain.betfair;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BetfairConfig(
    String username,
    String password,
    @JsonProperty("app_key") String appKey,
    BetfairCountry country
) {
    public BetfairConfig(String username, String password, String appKey) {
        this(username, password, appKey, null);
    }

    public BetfairConfig {
        username = blankToNull(username);
        password = blankToNull(password);
        appKey = blankToNull(appKey);
        country = country == null ? BetfairCountry.SPAIN : country;
    }

    public boolean isConfigured() {
        return username != null && password != null && appKey != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
