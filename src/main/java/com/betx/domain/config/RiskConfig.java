package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record RiskConfig(
    @JsonProperty("max_stake") BigDecimal maxStake,
    @JsonProperty("max_daily_loss") BigDecimal maxDailyLoss,
    @JsonProperty("max_open_positions") Integer maxOpenPositions,
    @JsonProperty("live_betting_enabled") Boolean liveBettingEnabled
) {
    public RiskConfig {
        maxStake = maxStake == null ? BigDecimal.valueOf(5) : maxStake;
        maxDailyLoss = maxDailyLoss == null ? BigDecimal.valueOf(25) : maxDailyLoss;
        maxOpenPositions = maxOpenPositions == null ? 3 : maxOpenPositions;
        liveBettingEnabled = liveBettingEnabled != null && liveBettingEnabled;
    }
}
