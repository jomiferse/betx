package com.betx.domain.betfair;

import java.math.BigDecimal;

public record BetfairRunnerPrice(
    long selectionId,
    String status,
    BigDecimal lastPriceTraded,
    BigDecimal bestBackPrice,
    BigDecimal bestLayPrice,
    BigDecimal totalMatched
) {
    public BetfairRunnerPrice(
        long selectionId,
        BigDecimal lastPriceTraded,
        BigDecimal bestBackPrice,
        BigDecimal bestLayPrice,
        BigDecimal totalMatched
    ) {
        this(selectionId, null, lastPriceTraded, bestBackPrice, bestLayPrice, totalMatched);
    }
}
