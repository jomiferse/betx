package com.betx.domain.config;

public record ResilienceConfig(
    ResilienceDependencyConfig betfair,
    ResilienceDependencyConfig telegram,
    ResilienceDependencyConfig openrouter
) {
    public ResilienceConfig {
        betfair = betfair == null ? ResilienceDependencyConfig.defaults() : betfair;
        telegram = telegram == null ? ResilienceDependencyConfig.defaults() : telegram;
        openrouter = openrouter == null ? ResilienceDependencyConfig.defaults() : openrouter;
    }

    public static ResilienceConfig defaults() {
        return new ResilienceConfig(null, null, null);
    }
}
