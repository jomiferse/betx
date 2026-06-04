package com.betx.domain.order;

import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;

/** Candidate order prepared for a future exchange execution adapter. */
public record BetOrder(
    String exchange,
    String marketId,
    long selectionId,
    BetSide side,
    BigDecimal odds,
    BigDecimal stake
) {
    public BetOrder {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("exchange is required.");
        }
        if (marketId == null || marketId.isBlank()) {
            throw new IllegalArgumentException("marketId is required.");
        }
        if (selectionId <= 0) {
            throw new IllegalArgumentException("selectionId must be greater than zero.");
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
    }
}
