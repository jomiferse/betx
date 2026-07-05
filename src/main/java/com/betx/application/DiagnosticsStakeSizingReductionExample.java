package com.betx.application;

import java.math.BigDecimal;

/** Strong reduction example for diagnostics-only risk-adjusted review. */
public record DiagnosticsStakeSizingReductionExample(
    String recommendationId,
    String eventName,
    String runnerName,
    String selectionSide,
    BigDecimal odds,
    BigDecimal baseStake,
    BigDecimal calculatedStake,
    BigDecimal finalStake,
    String adjustments
) {
}
