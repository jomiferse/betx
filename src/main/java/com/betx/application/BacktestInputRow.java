package com.betx.application;

import com.betx.domain.signal.MarketSnapshot;
import java.math.BigDecimal;
import java.time.Instant;

/** One historical runner observation plus the settled outcome for that runner. */
public record BacktestInputRow(
    Instant observedAt,
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
    BigDecimal liquidity,
    BacktestOutcome outcome
) {
    public BacktestInputRow {
        if (observedAt == null) {
            throw new BacktestValidationException("observed_at is required");
        }
        if (exchange == null || exchange.isBlank()) {
            throw new BacktestValidationException("exchange is required");
        }
        if (marketId == null || marketId.isBlank()) {
            throw new BacktestValidationException("market_id is required");
        }
        if (selectionId <= 0) {
            throw new BacktestValidationException("selection_id must be greater than zero");
        }
        if (bestBackPrice == null) {
            throw new BacktestValidationException("best_back_price is required");
        }
        if (bestLayPrice == null) {
            throw new BacktestValidationException("best_lay_price is required");
        }
        if (spread == null) {
            throw new BacktestValidationException("spread is required");
        }
        if (liquidity == null) {
            throw new BacktestValidationException("liquidity is required");
        }
        if (outcome == null) {
            throw new BacktestValidationException("result must be WIN or LOSE");
        }
    }

    public MarketSnapshot toMarketSnapshot() {
        return new MarketSnapshot(
            exchange,
            marketId,
            marketName,
            eventName,
            competitionName,
            marketStartTime,
            selectionId,
            runnerName,
            bestBackPrice,
            bestLayPrice,
            spread,
            liquidity
        );
    }
}
