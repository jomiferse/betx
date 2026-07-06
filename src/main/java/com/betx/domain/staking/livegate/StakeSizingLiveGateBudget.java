package com.betx.domain.staking.livegate;

import java.math.BigDecimal;

/** Budget snapshot used by the pure live gate evaluator. */
public record StakeSizingLiveGateBudget(
    BigDecimal currentDrawdown,
    BigDecimal dailyLossBudgetRemaining,
    BigDecimal totalExposureRemaining,
    BigDecimal marketExposureRemaining,
    boolean budgetSnapshotAvailable
) {
    public StakeSizingLiveGateBudget {
        currentDrawdown = valueOrZero(currentDrawdown);
        dailyLossBudgetRemaining = valueOrZero(dailyLossBudgetRemaining);
        totalExposureRemaining = valueOrZero(totalExposureRemaining);
        marketExposureRemaining = valueOrZero(marketExposureRemaining);
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
