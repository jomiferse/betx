package com.betx.application;

/** Coverage of exact prospective execution fields persisted on real bet intents. */
public record DiagnosticsExecutionDataCoverage(
    long totalOrders,
    long withEvaluationId,
    long withRecommendationId,
    long withOrderSubmittedAt,
    long withOrderResponseAt,
    long withOrderAcceptedAt,
    long withExecutedAt,
    long withRequestedOdds,
    long withAverageExecutedOdds,
    long withRequestedStake,
    long withMatchedStake,
    long withRemainingStake,
    long withExecutionStatus
) {
}
