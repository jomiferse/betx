package com.betx.domain.staking;

import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.time.Instant;

/** Immutable input for pure stake sizing decisions. */
public record StakeSizingContext(
    String recommendationId,
    String canonicalKey,
    String strategyName,
    SelectionSide selectionSide,
    BigDecimal odds,
    BigDecimal baseStake,
    BigDecimal minStake,
    BigDecimal maxStake,
    BigDecimal bankroll,
    StakeSizingRiskProfile riskProfile,
    StakeSizingSource source,
    BigDecimal estimatedProbability,
    Integer confidenceScore,
    BigDecimal currentDrawdown,
    BigDecimal openExposure,
    BigDecimal dailyLossSoFar,
    BigDecimal marketExposure,
    BigDecimal maxDailyLoss,
    BigDecimal maxTotalExposure,
    BigDecimal maxMarketExposure,
    Instant createdAt
) {
    public StakeSizingContext {
        recommendationId = blankToNull(recommendationId);
        canonicalKey = blankToNull(canonicalKey);
        strategyName = strategyName == null || strategyName.isBlank() ? "N/A" : strategyName.strip();
        selectionSide = selectionSide == null ? SelectionSide.UNKNOWN : selectionSide;
        requirePositive(odds, "odds");
        requirePositive(baseStake, "baseStake");
        requirePositive(minStake, "minStake");
        requirePositive(maxStake, "maxStake");
        requirePositive(bankroll, "bankroll");
        if (minStake.compareTo(maxStake) > 0) {
            throw new IllegalArgumentException("minStake must not be greater than maxStake.");
        }
        riskProfile = riskProfile == null ? StakeSizingRiskProfile.BALANCED : riskProfile;
        source = source == null ? StakeSizingSource.SHADOW : source;
        if (estimatedProbability != null
            && (estimatedProbability.compareTo(BigDecimal.ZERO) < 0 || estimatedProbability.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("estimatedProbability must be between 0 and 1.");
        }
        if (confidenceScore != null) {
            confidenceScore = Math.max(0, Math.min(100, confidenceScore));
        }
        openExposure = defaultZero(openExposure);
        dailyLossSoFar = defaultZero(dailyLossSoFar);
        marketExposure = defaultZero(marketExposure);
        requireNonNegative(maxDailyLoss, "maxDailyLoss");
        requireNonNegative(maxTotalExposure, "maxTotalExposure");
        requireNonNegative(maxMarketExposure, "maxMarketExposure");
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero.");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " must be zero or greater.");
        }
    }
}
