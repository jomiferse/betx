package com.betx.domain.signal;

import java.math.BigDecimal;
import java.time.Instant;

/** Read-only analysis for one runner in one market snapshot. */
public record RunnerAnalysis(
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
    RecommendationType recommendation,
    String reason
) {
    public RunnerAnalysis {
        if (snapshotKeyMissing(exchange, marketId, selectionId)) {
            throw new IllegalArgumentException("exchange, marketId, and selectionId are required.");
        }
        if (recommendation == null) {
            throw new IllegalArgumentException("recommendation is required.");
        }
        reason = reason == null || reason.isBlank() ? "unspecified" : reason;
    }

    public static RunnerAnalysis from(MarketSnapshot snapshot, RecommendationType recommendation, String reason) {
        return new RunnerAnalysis(
            snapshot.exchange(),
            snapshot.marketId(),
            snapshot.marketName(),
            snapshot.eventName(),
            snapshot.competitionName(),
            snapshot.marketStartTime(),
            snapshot.selectionId(),
            snapshot.runnerName(),
            snapshot.bestBackPrice(),
            snapshot.bestLayPrice(),
            snapshot.spread(),
            snapshot.liquidity(),
            recommendation,
            reason
        );
    }

    public String displayRunner() {
        return runnerName == null ? String.valueOf(selectionId) : runnerName;
    }

    private static boolean snapshotKeyMissing(String exchange, String marketId, long selectionId) {
        return exchange == null || exchange.isBlank() || marketId == null || marketId.isBlank() || selectionId <= 0;
    }
}
