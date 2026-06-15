package com.betx.application;

import com.betx.domain.signal.MarketSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

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
    BacktestOutcome outcome,
    String season,
    String oddsSource
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
        season = season == null || season.isBlank() ? seasonLabel(marketStartTime == null ? observedAt : marketStartTime) : season.strip();
        oddsSource = oddsSource == null || oddsSource.isBlank() ? "unknown" : oddsSource.strip();
    }

    public BacktestInputRow(
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
        this(
            observedAt,
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
            liquidity,
            outcome,
            seasonLabel(marketStartTime == null ? observedAt : marketStartTime),
            "unknown"
        );
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

    private static String seasonLabel(Instant instant) {
        if (instant == null) {
            return "unknown";
        }
        int year = instant.atZone(ZoneOffset.UTC).getYear();
        int month = instant.atZone(ZoneOffset.UTC).getMonthValue();
        int startYear = month >= 7 ? year : year - 1;
        int endYear = startYear + 1;
        return startYear + "/" + String.format("%02d", endYear % 100);
    }
}
