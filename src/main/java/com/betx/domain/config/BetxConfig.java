package com.betx.domain.config;

import com.betx.domain.betfair.BetfairConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record BetxConfig(
    AppConfig app,
    TelegramConfig telegram,
    BetfairConfig betfair,
    List<ExchangeConfig> exchanges,
    @JsonProperty("market_data") MarketDataConfig marketData,
    StorageConfig storage,
    RiskConfig risk,
    List<StrategyConfig> strategies,
    MlConfig ml,
    IntelligenceConfig intelligence
) {
    public BetxConfig {
        app = app == null ? new AppConfig(null) : app;
        telegram = telegram == null ? new TelegramConfig(null, null, null, null, null, null, null, null, null, null) : telegram;
        betfair = betfair == null ? new BetfairConfig(null, null, null, null) : betfair;
        marketData = marketData == null ? new MarketDataConfig(null, null, null, null) : marketData;
        exchanges = normalizeExchanges(exchanges, betfair, marketData);
        storage = storage == null ? new StorageConfig(null, null) : storage;
        risk = risk == null ? new RiskConfig(null, null, null) : risk;
        strategies = strategies == null ? List.of() : List.copyOf(strategies);
        ml = ml == null ? new MlConfig(null, null, null) : ml;
        intelligence = intelligence == null ? new IntelligenceConfig(null, null, null, null, null, null, null) : intelligence;
    }

    public BetxConfig(
        AppConfig app,
        TelegramConfig telegram,
        BetfairConfig betfair,
        List<ExchangeConfig> exchanges,
        MarketDataConfig marketData,
        StorageConfig storage,
        RiskConfig risk,
        List<StrategyConfig> strategies,
        MlConfig ml
    ) {
        this(app, telegram, betfair, exchanges, marketData, storage, risk, strategies, ml, null);
    }

    public static BetxConfig defaults() {
        return new BetxConfig(
            new AppConfig("info"),
            new TelegramConfig(true, null, "TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID", null, null, null, null, null, null),
            new BetfairConfig(null, null, null, null),
            List.of(),
            new MarketDataConfig(60, 0, List.of("1"), List.of("MATCH_ODDS"), true, 50),
            new StorageConfig("sqlite", "./data/betx.db"),
            new RiskConfig(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3),
            List.of(new StrategyConfig("value-football", true, BigDecimal.valueOf(0.06), BigDecimal.valueOf(500))),
            new MlConfig(false, "./models/value_model.pkl", BigDecimal.valueOf(0.70)),
            new IntelligenceConfig(false, "openrouter", "x-ai/grok-4.3", "OPENROUTER_API_KEY", null, 20, 70)
        );
    }

    public BetxConfig withExchanges(List<ExchangeConfig> newExchanges) {
        return new BetxConfig(app, telegram, betfair, newExchanges, marketData, storage, risk, strategies, ml, intelligence);
    }

    public BetxConfig withIntelligence(IntelligenceConfig newIntelligence) {
        return new BetxConfig(app, telegram, betfair, exchanges, marketData, storage, risk, strategies, ml, newIntelligence);
    }

    public List<ExchangeConfig> enabledExchanges() {
        return exchanges.stream()
            .filter(ExchangeConfig::isEnabled)
            .toList();
    }

    private static List<ExchangeConfig> normalizeExchanges(List<ExchangeConfig> exchanges, BetfairConfig betfair, MarketDataConfig marketData) {
        if (exchanges != null) {
            return exchanges.stream()
                .map(exchange -> new ExchangeConfig(exchange.name(), exchange.enabled(), exchange.betfair(), marketData))
                .toList();
        }
        if (betfair != null && betfair.isConfigured()) {
            return List.of(new ExchangeConfig("betfair", true, betfair, marketData));
        }
        return List.of();
    }
}
