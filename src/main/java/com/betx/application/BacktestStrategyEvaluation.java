package com.betx.application;

import java.util.Optional;

/** Strategy evaluation result plus optional non-trade diagnostic reason. */
public record BacktestStrategyEvaluation(
    Optional<BacktestStrategyDecision> decision,
    String rejectionReason
) {
    public BacktestStrategyEvaluation {
        decision = decision == null ? Optional.empty() : decision;
        rejectionReason = rejectionReason == null || rejectionReason.isBlank() ? null : rejectionReason;
    }

    public static BacktestStrategyEvaluation decision(BacktestStrategyDecision decision) {
        return new BacktestStrategyEvaluation(Optional.of(decision), null);
    }

    public static BacktestStrategyEvaluation rejected(String reason) {
        return new BacktestStrategyEvaluation(Optional.empty(), reason);
    }

    public static BacktestStrategyEvaluation skipped() {
        return new BacktestStrategyEvaluation(Optional.empty(), null);
    }
}
