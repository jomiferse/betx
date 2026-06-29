package com.betx.application;

import java.math.BigDecimal;

public record DiagnosticsCandidateFilterShadowResult(
    String filterName,
    String scope,
    long evaluations,
    long wouldPass,
    long wouldFilter,
    BigDecimal passRate,
    BigDecimal filterRate,
    long realBetsObserved,
    long paperTradesObserved,
    long settledIncluded,
    long settledExcluded,
    BigDecimal baselinePnl,
    BigDecimal shadowIncludedPnl,
    BigDecimal shadowExcludedPnl,
    BigDecimal baselineRoi,
    BigDecimal shadowIncludedRoi,
    BigDecimal shadowExcludedRoi,
    BigDecimal deltaPnl,
    BigDecimal deltaRoi,
    BigDecimal maxDrawdownIncluded,
    BigDecimal volumeRetentionPct,
    DiagnosticsCandidateFilterStatus status,
    String warning,
    boolean shouldApplyLive
) {
}
