package com.betx.application;

import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.time.Instant;

/** Shadow-persisted actionable opportunity emitted by the current strategy flow. */
public record BetRecommendation(
    String id,
    String evaluationId,
    String exchange,
    String marketId,
    long selectionId,
    SelectionSide selectionSide,
    String eventName,
    String runnerName,
    String competitionName,
    Instant marketStartTime,
    String strategyName,
    BigDecimal recommendedOdds,
    Instant observedAt,
    Instant recommendedAt,
    BetRecommendationSource source,
    BetRecommendationStatus status,
    Instant createdAt,
    Integer confidence,
    BigDecimal edge,
    BigDecimal liquidity,
    String reason
) {
    public BetRecommendation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required.");
        }
        if (exchange == null || exchange.isBlank() || marketId == null || marketId.isBlank() || selectionId <= 0) {
            throw new IllegalArgumentException("exchange, marketId, and selectionId are required.");
        }
        if (observedAt == null || recommendedAt == null || createdAt == null) {
            throw new IllegalArgumentException("observedAt, recommendedAt, and createdAt are required.");
        }
        id = id.strip();
        evaluationId = evaluationId == null || evaluationId.isBlank() ? null : evaluationId.strip();
        exchange = exchange.strip().toLowerCase(java.util.Locale.ROOT);
        marketId = marketId.strip();
        selectionSide = selectionSide == null ? SelectionSide.UNKNOWN : selectionSide;
        strategyName = strategyName == null || strategyName.isBlank() ? "N/A" : strategyName.strip();
        source = source == null ? BetRecommendationSource.SHADOW : source;
        status = status == null ? BetRecommendationStatus.CREATED : status;
        reason = reason == null || reason.isBlank() ? null : reason;
    }
}
