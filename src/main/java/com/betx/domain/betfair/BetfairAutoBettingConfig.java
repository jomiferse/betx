package com.betx.domain.betfair;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Betfair-specific automatic betting controls. */
public record BetfairAutoBettingConfig(
    Boolean enabled,
    @JsonProperty("request_confirmation") Boolean requestConfirmation,
    @JsonProperty("max_stake") BigDecimal maxStake,
    @JsonProperty("max_daily_loss") BigDecimal maxDailyLoss,
    @JsonProperty("max_open_positions") Integer maxOpenPositions
) {
    public BetfairAutoBettingConfig {
        enabled = enabled != null && enabled;
        requestConfirmation = requestConfirmation == null || requestConfirmation;
        maxStake = maxStake == null ? BigDecimal.valueOf(5) : maxStake;
        maxDailyLoss = maxDailyLoss == null ? BigDecimal.valueOf(25) : maxDailyLoss;
        maxOpenPositions = maxOpenPositions == null ? 3 : maxOpenPositions;
    }
}
