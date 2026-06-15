package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Persistable paper-trading recommendation with execution and closing-line fields. */
public record BacktestPaperTrade(
    String eventId,
    String marketId,
    String league,
    String season,
    String eventName,
    String runner,
    Instant recommendationTimestamp,
    Instant executionTimestamp,
    Instant closingTimestamp,
    BigDecimal availableBackOdds,
    BigDecimal requestedOdds,
    BigDecimal executionOdds,
    BigDecimal closingOdds,
    BacktestOutcome result,
    BigDecimal grossPnl,
    BigDecimal commission,
    BigDecimal netPnl,
    BigDecimal decimalClvRatio,
    BigDecimal impliedProbabilityChange,
    String movementBucket
) {
    public BacktestPaperTrade {
        eventId = eventId == null || eventId.isBlank() ? marketId : eventId;
        league = league == null || league.isBlank() ? "unknown" : league;
        season = season == null || season.isBlank() ? "unknown" : season;
        runner = runner == null || runner.isBlank() ? "unknown" : runner;
        executionTimestamp = executionTimestamp == null ? recommendationTimestamp : executionTimestamp;
        commission = commission == null ? BigDecimal.ZERO : commission;
        movementBucket = movementBucket == null || movementBucket.isBlank() ? "unknown" : movementBucket;
    }

    public static BigDecimal clvRatio(BigDecimal executionOdds, BigDecimal closingOdds) {
        if (executionOdds == null || closingOdds == null || closingOdds.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return executionOdds.divide(closingOdds, 10, RoundingMode.HALF_UP)
            .subtract(BigDecimal.ONE)
            .setScale(8, RoundingMode.HALF_UP);
    }

    public static BigDecimal impliedProbabilityChange(BigDecimal executionOdds, BigDecimal closingOdds) {
        if (executionOdds == null || closingOdds == null
            || executionOdds.compareTo(BigDecimal.ZERO) == 0
            || closingOdds.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        BigDecimal executionProbability = BigDecimal.ONE.divide(executionOdds, 10, RoundingMode.HALF_UP);
        BigDecimal closingProbability = BigDecimal.ONE.divide(closingOdds, 10, RoundingMode.HALF_UP);
        return closingProbability.subtract(executionProbability).setScale(8, RoundingMode.HALF_UP);
    }
}
