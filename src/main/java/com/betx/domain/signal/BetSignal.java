package com.betx.domain.signal;

import java.math.BigDecimal;

/** Candidate betting signal emitted by a strategy. */
public record BetSignal(
    String exchange,
    String marketId,
    long selectionId,
    BetSide side,
    BigDecimal odds,
    BigDecimal stake,
    String reason,
    String mode,
    String evaluationId
) {
    public BetSignal {
        exchange = exchange == null || exchange.isBlank() ? "betfair" : exchange.strip().toLowerCase();
        if (marketId == null || marketId.isBlank()) {
            throw new IllegalArgumentException("marketId is required.");
        }
        if (side == null) {
            throw new IllegalArgumentException("side is required.");
        }
        if (odds == null || odds.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("odds must be greater than zero.");
        }
        if (stake == null || stake.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("stake must be greater than zero.");
        }
        reason = reason == null ? "" : reason;
        mode = mode == null || mode.isBlank() ? "dry-run" : mode;
        evaluationId = evaluationId == null || evaluationId.isBlank() ? null : evaluationId.strip();
    }

    public BetSignal(
        String exchange,
        String marketId,
        long selectionId,
        BetSide side,
        BigDecimal odds,
        BigDecimal stake,
        String reason,
        String mode
    ) {
        this(exchange, marketId, selectionId, side, odds, stake, reason, mode, null);
    }
}
