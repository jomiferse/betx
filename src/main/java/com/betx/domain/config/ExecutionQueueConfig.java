package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Duration;

/** Runtime controls for serializing real order execution per exchange. */
public record ExecutionQueueConfig(
    Boolean enabled,
    @JsonProperty("max_pending_per_exchange") Integer maxPendingPerExchange,
    @JsonProperty("order_ttl") String orderTtlValue,
    @JsonProperty("stale_balance_ttl") String staleBalanceTtlValue,
    @JsonProperty("revalidate_odds_after") String revalidateOddsAfterValue,
    @JsonProperty("min_effective_balance") BigDecimal minEffectiveBalance
) {
    public ExecutionQueueConfig {
        enabled = enabled == null || enabled;
        maxPendingPerExchange = maxPendingPerExchange == null ? 20 : maxPendingPerExchange;
        orderTtlValue = orderTtlValue == null || orderTtlValue.isBlank() ? "10s" : orderTtlValue.strip();
        staleBalanceTtlValue = staleBalanceTtlValue == null || staleBalanceTtlValue.isBlank() ? "5s" : staleBalanceTtlValue.strip();
        revalidateOddsAfterValue = revalidateOddsAfterValue == null || revalidateOddsAfterValue.isBlank()
            ? "3s"
            : revalidateOddsAfterValue.strip();
        minEffectiveBalance = minEffectiveBalance == null ? new BigDecimal("0.01") : minEffectiveBalance;
    }

    public static ExecutionQueueConfig defaults() {
        return new ExecutionQueueConfig(null, null, null, null, null, null);
    }

    public Duration orderTtl() {
        return DurationParser.parse(orderTtlValue);
    }

    public Duration staleBalanceTtl() {
        return DurationParser.parse(staleBalanceTtlValue);
    }

    public Duration revalidateOddsAfter() {
        return DurationParser.parse(revalidateOddsAfterValue);
    }
}
