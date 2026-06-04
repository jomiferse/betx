package com.betx.domain.betfair;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Betfair login jurisdictions supported by the interactive API endpoint. */
public enum BetfairCountry {
    GLOBAL("global", "https://identitysso.betfair.com/api/login"),
    AUSTRALIA_NEW_ZEALAND("australia_new_zealand", "https://identitysso.betfair.com.au/api/login"),
    ITALY("italy", "https://identitysso.betfair.it/api/login"),
    SPAIN("spain", "https://identitysso.betfair.es/api/login"),
    ROMANIA("romania", "https://identitysso.betfair.ro/api/login");

    private final String configValue;
    private final String loginUrl;

    BetfairCountry(String configValue, String loginUrl) {
        this.configValue = configValue;
        this.loginUrl = loginUrl;
    }

    public String loginUrl() {
        return loginUrl;
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
