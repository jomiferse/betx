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
    BigDecimal profitLoss
) {
}
