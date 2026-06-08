package com.betx.domain.config;

import com.betx.common.ConfigException;
import com.betx.domain.betfair.BetfairAutoBettingConfig;
import java.math.BigDecimal;

public class BetxConfigValidator {
    public void validate(BetxConfig config) {
        requirePositive(config.risk().maxStake(), "risk.max_stake");
        requirePositive(config.risk().maxDailyLoss(), "risk.max_daily_loss");
        if (config.risk().maxOpenPositions() <= 0) {
            throw new ConfigException("risk.max_open_positions must be greater than zero.");
        }
        if (!config.storage().type().equals("sqlite")) {
            throw new ConfigException("storage.type must be sqlite.");
        }
        if (config.marketData().pollIntervalSeconds() <= 0) {
            throw new ConfigException("market_data.poll_interval_seconds must be greater than zero.");
        }
        if (config.marketData().maxMarkets() < 0) {
            throw new ConfigException("market_data.max_markets must be zero or greater.");
        }
        if (config.marketData().betfairEventBatchSize() <= 0) {
            throw new ConfigException("market_data.betfair_event_batch_size must be greater than zero.");
        }
        validateIntelligence(config.intelligence());
        config.exchanges().stream()
            .filter(exchange -> "betfair".equals(exchange.name()))
            .map(exchange -> exchange.betfair().autoBetting())
            .filter(BetfairAutoBettingConfig::enabled)
            .forEach(this::validateBetfairAutoBetting);
    }

    private void validateIntelligence(IntelligenceConfig intelligence) {
        if (!intelligence.enabled()) {
            return;
        }
        if (!"openrouter".equals(intelligence.provider())) {
            throw new ConfigException("intelligence.provider must be openrouter.");
        }
        if (intelligence.timeoutSeconds() <= 0) {
            throw new ConfigException("intelligence.timeout_seconds must be greater than zero.");
        }
        if (intelligence.minConfidence() < 0 || intelligence.minConfidence() > 100) {
            throw new ConfigException("intelligence.min_confidence must be between 0 and 100.");
        }
        if (intelligence.apiKeyEnv().startsWith("sk-")) {
            throw new ConfigException("intelligence.api_key_env must be an environment variable name, not an API key.");
        }
    }

    private void validateBetfairAutoBetting(BetfairAutoBettingConfig autoBetting) {
        requirePositive(autoBetting.maxStake(), "exchanges.betfair.auto_betting.max_stake");
        requirePositive(autoBetting.maxDailyLoss(), "exchanges.betfair.auto_betting.max_daily_loss");
        if (autoBetting.maxOpenPositions() <= 0) {
            throw new ConfigException("exchanges.betfair.auto_betting.max_open_positions must be greater than zero.");
        }
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConfigException(field + " must be greater than zero.");
        }
    }
}
