package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record StrategyConfig(
    String name,
    Boolean enabled,
    @JsonProperty("min_edge") BigDecimal minEdge,
    @JsonProperty("min_liquidity") BigDecimal minLiquidity
) {
    public StrategyConfig {
        enabled = enabled == null || enabled;
    }
}
