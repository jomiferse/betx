package com.betx.domain.config;

import com.betx.domain.betfair.BetfairConfig;

/** Configuration for one betting exchange adapter. */
public record ExchangeConfig(
    String name,
    Boolean enabled,
    BetfairConfig betfair,
    MarketDataConfig marketData
) {
    public ExchangeConfig {
        name = name == null ? "" : name.strip().toLowerCase();
        enabled = enabled != null && enabled;
        betfair = betfair == null ? new BetfairConfig(null, null, null, null) : betfair;
        marketData = marketData == null ? new MarketDataConfig(null, null, null, null) : marketData;
    }

    public ExchangeConfig(String name, Boolean enabled, BetfairConfig betfair) {
        this(name, enabled, betfair, null);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
