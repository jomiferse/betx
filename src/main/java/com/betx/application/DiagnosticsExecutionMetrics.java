package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import java.math.BigDecimal;
import java.time.Duration;

public record DiagnosticsExecutionMetrics(
    long ordersSubmitted,
    long fullyMatched,
    long partiallyMatched,
    long unmatched,
    long rejected,
    long cancelled,
    Duration averageExecutionLatency,
    Duration medianExecutionLatency,
    Duration p95ExecutionLatency,
    DiagnosticsDataProvenance latencyProvenance,
    BigDecimal averageRealRecordedVsPaperOddsDifference,
    DiagnosticsDataProvenance oddsProvenance,
    long missingRecordedOdds,
    long missingExchangeOrderId
) {
}
