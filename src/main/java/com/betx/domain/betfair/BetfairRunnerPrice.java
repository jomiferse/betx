package com.betx.domain.betfair;

import java.math.BigDecimal;

public record BetfairRunnerPrice(
    long selectionId,
    BigDecimal lastPriceTraded,
    BigDecimal bestBackPrice,
    BigDecimal bestLayPrice,
    BigDecimal totalMatched
) {
}
