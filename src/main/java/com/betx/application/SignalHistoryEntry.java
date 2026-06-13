package com.betx.application;

import com.betx.domain.signal.RecommendationType;
import java.math.BigDecimal;
import java.time.Instant;

/** Compact durable history row for useful signal decisions. */
public record SignalHistoryEntry(
    Instant observedAt,
    String exchange,
    String marketId,
    long selectionId,
    String eventName,
    String marketName,
    String runnerName,
    String competitionName,
    Instant marketStartTime,
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
    MatchIntelligenceDecision intelligenceDecision,
    Integer intelligenceConfidence,
    String intelligenceSummary,
    String betIntentId,
    String externalOrderId,
    String orderStage,
    BigDecimal selectedStake,
    String resultMessage,
    BigDecimal realizedProfitLoss
) {
    public SignalHistoryEntry {
        if (observedAt == null || exchange == null || exchange.isBlank() || marketId == null || marketId.isBlank() || selectionId <= 0) {
            throw new IllegalArgumentException("observedAt, exchange, marketId, and selectionId are required.");
        }
        if (recommendation == null) {
            throw new IllegalArgumentException("recommendation is required.");
        }
        confidenceLabel = confidenceLabel == null || confidenceLabel.isBlank() ? "Low confidence" : confidenceLabel;
        reason = reason == null || reason.isBlank() ? "unspecified" : reason;
    }

    public SignalHistoryKey key() {
        return new SignalHistoryKey(exchange, marketId, selectionId, observedAt);
    }
}
