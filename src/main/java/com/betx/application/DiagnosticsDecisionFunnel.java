package com.betx.application;

import java.util.Map;

public record DiagnosticsDecisionFunnel(
    long marketsScanned,
    long runnersAnalyzed,
    long recommendationsGenerated,
    long strategyRejections,
    long riskRejections,
    long confirmationRequests,
    long ordersSubmitted,
    long ordersMatched,
    long ordersRejected,
    long betsSettled,
    Map<String, Long> rejectionReasons
) {
}
