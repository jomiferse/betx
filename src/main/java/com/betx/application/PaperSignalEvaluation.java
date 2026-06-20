package com.betx.application;

import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerType;
import java.math.BigDecimal;
import java.time.Instant;

/** Per-runner paper-trading analyzer evaluation, including rejected candidates. */
public record PaperSignalEvaluation(
    Instant observedAt,
    String exchange,
    String marketId,
    String marketName,
    String eventName,
    String competitionName,
    Instant marketStartTime,
    long selectionId,
    String runnerName,
    RunnerType runnerType,
    RecommendationType recommendation,
    int score,
    String confidenceLabel,
    String reason,
    BigDecimal bestBackPrice,
    BigDecimal bestLayPrice,
    BigDecimal spread,
    BigDecimal liquidity,
    BigDecimal backPercentageDelta,
    BigDecimal layPercentageDelta,
    BigDecimal liquidityPercentageDelta,
    PaperTradeAnalyzerRejectionReason analyzerReason,
    String evaluationId,
    String recommendationId
) {
    public PaperSignalEvaluation {
        runnerType = runnerType == null ? RunnerType.UNKNOWN : runnerType;
        recommendation = recommendation == null ? RecommendationType.NO_BET : recommendation;
        analyzerReason = analyzerReason == null ? PaperTradeAnalyzerRejectionReason.CONFIDENCE_BELOW_THRESHOLD : analyzerReason;
        evaluationId = evaluationId == null || evaluationId.isBlank() ? null : evaluationId.strip();
        recommendationId = recommendationId == null || recommendationId.isBlank() ? null : recommendationId.strip();
    }

    public PaperSignalEvaluation(
        Instant observedAt,
        String exchange,
        String marketId,
        String marketName,
        String eventName,
        String competitionName,
        Instant marketStartTime,
        long selectionId,
        String runnerName,
        RunnerType runnerType,
        RecommendationType recommendation,
        int score,
        String confidenceLabel,
        String reason,
        BigDecimal bestBackPrice,
        BigDecimal bestLayPrice,
        BigDecimal spread,
        BigDecimal liquidity,
        BigDecimal backPercentageDelta,
        BigDecimal layPercentageDelta,
        BigDecimal liquidityPercentageDelta,
        PaperTradeAnalyzerRejectionReason analyzerReason
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
            runnerType,
            recommendation,
            score,
            confidenceLabel,
            reason,
            bestBackPrice,
            bestLayPrice,
            spread,
            liquidity,
            backPercentageDelta,
            layPercentageDelta,
            liquidityPercentageDelta,
            analyzerReason,
            null,
            null
        );
    }
}
