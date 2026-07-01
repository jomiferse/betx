package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Shadow stake sizing risk budget defaults. */
public record StakingLimitsConfig(
    @JsonProperty("max_daily_loss") BigDecimal maxDailyLoss,
    @JsonProperty("max_total_exposure") BigDecimal maxTotalExposure,
    @JsonProperty("max_market_exposure") BigDecimal maxMarketExposure,
    @JsonProperty("max_open_positions") Integer maxOpenPositions
) {
    public StakingLimitsConfig {
        maxDailyLoss = maxDailyLoss == null ? BigDecimal.valueOf(25) : maxDailyLoss;
        maxTotalExposure = maxTotalExposure == null ? BigDecimal.valueOf(50) : maxTotalExposure;
        maxMarketExposure = maxMarketExposure == null ? BigDecimal.valueOf(5) : maxMarketExposure;
        maxOpenPositions = maxOpenPositions == null ? 10 : maxOpenPositions;
    }

    public static StakingLimitsConfig defaults() {
        return new StakingLimitsConfig(null, null, null, null);
    }
}
