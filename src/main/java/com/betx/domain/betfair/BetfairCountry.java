package com.betx.domain.betfair;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Betfair login jurisdictions supported by the interactive API endpoint. */
public enum BetfairCountry {
    GLOBAL("global", "https://identitysso.betfair.com/api/login", "https://identitysso.betfair.com/api/keepAlive"),
    AUSTRALIA_NEW_ZEALAND("australia_new_zealand", "https://identitysso.betfair.com.au/api/login", "https://identitysso.betfair.com.au/api/keepAlive"),
    ITALY("italy", "https://identitysso.betfair.it/api/login", "https://identitysso.betfair.it/api/keepAlive"),
    SPAIN("spain", "https://identitysso.betfair.es/api/login", "https://identitysso.betfair.es/api/keepAlive"),
    ROMANIA("romania", "https://identitysso.betfair.ro/api/login", "https://identitysso.betfair.ro/api/keepAlive");

    private final String configValue;
    private final String loginUrl;
    private final String keepAliveUrl;

    BetfairCountry(String configValue, String loginUrl, String keepAliveUrl) {
        this.configValue = configValue;
        this.loginUrl = loginUrl;
        this.keepAliveUrl = keepAliveUrl;
    }

    public String loginUrl() {
        return loginUrl;
    }

    public String keepAliveUrl() {
        return keepAliveUrl;
    }

    @JsonValue
    public String configValue() {
        return configValue;
    }

    @JsonCreator
    public static BetfairCountry fromConfigValue(String value) {
        if (value == null || value.isBlank()) {
            return SPAIN;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (BetfairCountry country : values()) {
            if (country.configValue.equals(normalized)) {
                return country;
            }
        }
        throw new IllegalArgumentException("Unsupported Betfair country: " + value);
    }
}
