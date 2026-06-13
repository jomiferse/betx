package com.betx.domain.exposure;

import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;

/** Real exchange position contributing to current exposure. */
public record ExchangeExposurePosition(
    String externalOrderId,
    String marketId,
    long selectionId,
    BetSide side,
    BigDecimal stake,
    BigDecimal risk
) {
    public ExchangeExposurePosition {
        externalOrderId = externalOrderId == null ? null : externalOrderId.strip();
        marketId = marketId == null ? "" : marketId.strip();
        stake = stake == null ? BigDecimal.ZERO : stake;
        risk = risk == null ? BigDecimal.ZERO : risk;
    }
}
