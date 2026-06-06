package com.betx.domain.config;

import com.betx.common.ConfigException;
import java.math.BigDecimal;

public class BetxConfigValidator {
    public void validate(BetxConfig config) {
        if (!config.app().mode().equals("dry-run") && !config.app().mode().equals("live")) {
            throw new ConfigException("app.mode must be dry-run or live.");
        }
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
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConfigException(field + " must be greater than zero.");
        }
    }
}
