package com.betx.application;

import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.time.Instant;

/** A simulated trade created during historical replay. */
public record BacktestTrade(
    Instant observedAt,
    String exchange,
    String marketId,
    String eventName,
    String marketName,
    long selectionId,
    String runnerName,
    BetSide side,
    BigDecimal odds,
    BigDecimal stake,
    BacktestOutcome outcome,
    BigDecimal profitLoss,
    String competitionName,
    String confidenceLabel,
    BigDecimal oddsMovementPercent,
    BacktestRunnerType runnerType
) {
    public BacktestTrade {
        competitionName = competitionName == null || competitionName.isBlank() ? "unknown" : competitionName;
        confidenceLabel = confidenceLabel == null || confidenceLabel.isBlank() ? "Unknown confidence" : confidenceLabel;
        runnerType = runnerType == null ? BacktestRunnerType.UNKNOWN : runnerType;
    }

    public BacktestTrade(
        Instant observedAt,
        String exchange,
        String marketId,
        String eventName,
        String marketName,
        long selectionId,
        String runnerName,
        BetSide side,
        BigDecimal odds,
        BigDecimal stake,
        BacktestOutcome outcome,
        BigDecimal profitLoss
    ) {
        this(
            observedAt,
            exchange,
            marketId,
            eventName,
            marketName,
            selectionId,
            runnerName,
            side,
            odds,
            stake,
            outcome,
            profitLoss,
            "unknown",
            "Unknown confidence",
            null,
            BacktestRunnerType.UNKNOWN
        );
    }
}
