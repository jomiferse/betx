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
    long withExecutionStatus,
    long prospectiveOrders,
    long prospectiveWithSelectionSide,
    long prospectiveMissingSelectionSide,
    long historicalUnknownSelectionSide,
    long prospectiveWithStrategyName,
    long prospectiveWithCompetitionName,
    long prospectiveWithRequestedOdds,
    long prospectiveWithRequestedStake,
    long prospectiveWithOrderSubmittedAt,
    long prospectiveWithOrderResponseAt
) {
    public DiagnosticsExecutionDataCoverage(
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
        this(
            totalOrders,
            withEvaluationId,
            withRecommendationId,
            withOrderSubmittedAt,
            withOrderResponseAt,
            withOrderAcceptedAt,
            withExecutedAt,
            withRequestedOdds,
            withAverageExecutedOdds,
            withRequestedStake,
            withMatchedStake,
            withRemainingStake,
            withExecutionStatus,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0
        );
    }
}
