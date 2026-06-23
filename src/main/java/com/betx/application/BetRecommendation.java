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
    String reason,
    String canonicalKey,
    Instant firstSeenAt,
    Instant lastSeenAt,
    long observedCount,
    BigDecimal initialRecommendedOdds,
    BigDecimal latestRecommendedOdds,
    BigDecimal bestRecommendedOdds,
    Instant coveredAt,
    Instant expiredAt,
    String lastEvaluationId
) {
    public BetRecommendation(
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
        this(
            id,
            evaluationId,
            exchange,
            marketId,
            selectionId,
            selectionSide,
            eventName,
            runnerName,
            competitionName,
            marketStartTime,
            strategyName,
            recommendedOdds,
            observedAt,
            recommendedAt,
            source,
            status,
            createdAt,
            confidence,
            edge,
            liquidity,
            reason,
            null,
            observedAt,
            observedAt,
            1,
            recommendedOdds,
            recommendedOdds,
            recommendedOdds,
            null,
            null,
            evaluationId
        );
    }

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
        status = status == null ? BetRecommendationStatus.ACTIVE : status;
        reason = reason == null || reason.isBlank() ? null : reason;
        canonicalKey = canonicalKey == null || canonicalKey.isBlank()
            ? canonicalKey(exchange, marketId, selectionId, selectionSide, strategyName)
            : canonicalKey.strip();
        firstSeenAt = firstSeenAt == null ? observedAt : firstSeenAt;
        lastSeenAt = lastSeenAt == null ? observedAt : lastSeenAt;
        observedCount = Math.max(1, observedCount);
        initialRecommendedOdds = initialRecommendedOdds == null ? recommendedOdds : initialRecommendedOdds;
        latestRecommendedOdds = latestRecommendedOdds == null ? recommendedOdds : latestRecommendedOdds;
        bestRecommendedOdds = bestRecommendedOdds == null ? recommendedOdds : bestRecommendedOdds;
        lastEvaluationId = lastEvaluationId == null || lastEvaluationId.isBlank() ? evaluationId : lastEvaluationId.strip();
    }

    public static String canonicalKey(
        String exchange,
        String marketId,
        long selectionId,
        SelectionSide selectionSide,
        String strategyName
    ) {
        String normalizedExchange = exchange == null ? "" : exchange.strip().toLowerCase(java.util.Locale.ROOT);
        String normalizedMarketId = marketId == null ? "" : marketId.strip();
        SelectionSide normalizedSide = selectionSide == null ? SelectionSide.UNKNOWN : selectionSide;
        String normalizedStrategy = strategyName == null || strategyName.isBlank() ? "N/A" : strategyName.strip();
        return normalizedExchange + "|" + normalizedMarketId + "|" + selectionId + "|" + normalizedSide.name() + "|" + normalizedStrategy;
    }

    public BetRecommendation observedAgain(
        String newEvaluationId,
        BigDecimal newRecommendedOdds,
        Instant seenAt
    ) {
        BigDecimal latestOdds = newRecommendedOdds == null ? latestRecommendedOdds : newRecommendedOdds;
        BigDecimal bestOdds = bestBackOdds(bestRecommendedOdds, latestOdds);
        return new BetRecommendation(
            id,
            evaluationId,
            exchange,
            marketId,
            selectionId,
            selectionSide,
            eventName,
            runnerName,
            competitionName,
            marketStartTime,
            strategyName,
            recommendedOdds,
            observedAt,
            recommendedAt,
            source,
            status,
            createdAt,
            confidence,
            edge,
            liquidity,
            reason,
            canonicalKey,
            firstSeenAt,
            seenAt == null ? lastSeenAt : seenAt,
            observedCount + 1,
            initialRecommendedOdds,
            latestOdds,
            bestOdds,
            coveredAt,
            expiredAt,
            newEvaluationId
        );
    }

    public BetRecommendation covered(Instant coveredAt) {
        return new BetRecommendation(
            id,
            evaluationId,
            exchange,
            marketId,
            selectionId,
            selectionSide,
            eventName,
            runnerName,
            competitionName,
            marketStartTime,
            strategyName,
            recommendedOdds,
            observedAt,
            recommendedAt,
            source,
            BetRecommendationStatus.COVERED,
            createdAt,
            confidence,
            edge,
            liquidity,
            reason,
            canonicalKey,
            firstSeenAt,
            lastSeenAt,
            observedCount,
            initialRecommendedOdds,
            latestRecommendedOdds,
            bestRecommendedOdds,
            this.coveredAt == null ? coveredAt : this.coveredAt,
            expiredAt,
            lastEvaluationId
        );
    }

    private static BigDecimal bestBackOdds(BigDecimal currentBest, BigDecimal candidate) {
        if (currentBest == null) {
            return candidate;
        }
        if (candidate == null) {
            return currentBest;
        }
        return candidate.compareTo(currentBest) > 0 ? candidate : currentBest;
    }
}
