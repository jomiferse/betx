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
    RunnerType runnerType,
    String strategyName,
    BigDecimal bestBackPrice,
    BigDecimal bestLayPrice,
    BigDecimal spread,
    BigDecimal liquidity,
    RecommendationType recommendation,
    String reason,
    SignalScore score
) {
    public RunnerAnalysis {
        if (snapshotKeyMissing(exchange, marketId, selectionId)) {
            throw new IllegalArgumentException("exchange, marketId, and selectionId are required.");
        }
        if (recommendation == null) {
            throw new IllegalArgumentException("recommendation is required.");
        }
        runnerType = runnerType == null ? RunnerType.UNKNOWN : runnerType;
        strategyName = strategyName == null || strategyName.isBlank() ? "N/A" : strategyName.strip();
        reason = reason == null || reason.isBlank() ? "unspecified" : reason;
        score = score == null ? SignalScore.zero(reason) : score;
    }

    public RunnerAnalysis(
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
            "N/A",
            bestBackPrice,
            bestLayPrice,
            spread,
            liquidity,
            recommendation,
            reason,
            SignalScore.zero(reason)
        );
    }

    public RunnerAnalysis(
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
        String reason,
        SignalScore score
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
            "N/A",
            bestBackPrice,
            bestLayPrice,
            spread,
            liquidity,
            recommendation,
            reason,
            score
        );
    }

    public static RunnerAnalysis from(MarketSnapshot snapshot, RecommendationType recommendation, String reason) {
        return from(snapshot, recommendation, reason, SignalScore.zero(reason));
    }

    public static RunnerAnalysis from(MarketSnapshot snapshot, RecommendationType recommendation, String reason, SignalScore score) {
        return from(snapshot, recommendation, reason, score, "N/A");
    }

    public static RunnerAnalysis from(
        MarketSnapshot snapshot,
        RecommendationType recommendation,
        String reason,
        SignalScore score,
        String strategyName
    ) {
        return new RunnerAnalysis(
            snapshot.exchange(),
            snapshot.marketId(),
            snapshot.marketName(),
            snapshot.eventName(),
            snapshot.competitionName(),
            snapshot.marketStartTime(),
            snapshot.selectionId(),
            snapshot.runnerName(),
            snapshot.runnerType(),
            strategyName,
            snapshot.bestBackPrice(),
            snapshot.bestLayPrice(),
            snapshot.spread(),
            snapshot.liquidity(),
            recommendation,
            reason,
            score
        );
    }

    public String displayRunner() {
        return runnerName == null ? String.valueOf(selectionId) : runnerName;
    }

    private static boolean snapshotKeyMissing(String exchange, String marketId, long selectionId) {
        return exchange == null || exchange.isBlank() || marketId == null || marketId.isBlank() || selectionId <= 0;
    }
}
