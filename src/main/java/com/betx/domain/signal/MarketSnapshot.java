package com.betx.domain.signal;

import java.math.BigDecimal;
import java.time.Instant;

/** Normalized market and runner price data used by strategies. */
public record MarketSnapshot(
    String exchange,
    String marketId,
    String marketName,
    String eventName,
    String competitionName,
    Instant marketStartTime,
    long selectionId,
    String runnerName,
    RunnerType runnerType,
    BigDecimal bestBackPrice,
    BigDecimal bestLayPrice,
    BigDecimal spread,
    BigDecimal liquidity
) {
    public MarketSnapshot {
        exchange = exchange == null || exchange.isBlank() ? "betfair" : exchange.strip().toLowerCase();
        if (marketId == null || marketId.isBlank()) {
            throw new IllegalArgumentException("marketId is required.");
        }
        if (selectionId <= 0) {
            throw new IllegalArgumentException("selectionId must be greater than zero.");
        }
        runnerName = runnerName == null || runnerName.isBlank() ? null : runnerName.strip();
        runnerType = runnerType == null ? RunnerType.UNKNOWN : runnerType;
        liquidity = liquidity == null ? BigDecimal.ZERO : liquidity;
    }

    public MarketSnapshot(
        String exchange,
        String marketId,
        String marketName,
        String eventName,
        String competitionName,
        Instant marketStartTime,
        long selectionId,
        String runnerName,
        BigDecimal bestBackPrice,
        BigDecimal bestLayPrice,
        BigDecimal spread,
        BigDecimal liquidity
    ) {
        this(
            exchange,
            marketId,
            marketName,
            eventName,
            competitionName,
            marketStartTime,
            selectionId,
            runnerName,
            RunnerType.UNKNOWN,
            bestBackPrice,
            bestLayPrice,
            spread,
            liquidity
        );
    }

    public MarketSnapshot(
        String exchange,
        String marketId,
        String marketName,
        String eventName,
        String competitionName,
        Instant marketStartTime,
        long selectionId,
        BigDecimal bestBackPrice,
        BigDecimal bestLayPrice,
        BigDecimal spread,
        BigDecimal liquidity
    ) {
        this(
            exchange,
            marketId,
            marketName,
            eventName,
            competitionName,
            marketStartTime,
            selectionId,
            null,
            RunnerType.UNKNOWN,
            bestBackPrice,
            bestLayPrice,
            spread,
            liquidity
        );
    }
}
