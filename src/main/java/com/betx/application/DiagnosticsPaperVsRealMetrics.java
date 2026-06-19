package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import java.math.BigDecimal;

public record DiagnosticsPaperVsRealMetrics(
    int matchedPairs,
    int settledMatchedPairs,
    BigDecimal averageRealVsPaperOddsDifference,
    BigDecimal medianRealVsPaperOddsDifference,
    BigDecimal averageSlippage,
    BigDecimal medianSlippage,
    BigDecimal matchedPaperPnl,
    BigDecimal matchedRealPnl,
    BigDecimal executionPnlDifference,
    BigDecimal paperPnlPerUnitStake,
    BigDecimal realPnlPerUnitStake,
    BigDecimal paperRoi,
    BigDecimal realRoi,
    BigDecimal normalizedExecutionDifference,
    long resultMismatches,
    DiagnosticsDataProvenance oddsProvenance,
    DiagnosticsDataProvenance pnlComparisonProvenance
) {
}
