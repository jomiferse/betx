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
    BigDecimal risk,
    BigDecimal matchedStake,
    BigDecimal remainingStake,
    BigDecimal averageExecutedOdds
) {
    public ExchangeExposurePosition {
        externalOrderId = externalOrderId == null ? null : externalOrderId.strip();
        marketId = marketId == null ? "" : marketId.strip();
        stake = stake == null ? BigDecimal.ZERO : stake;
        risk = risk == null ? BigDecimal.ZERO : risk;
        matchedStake = matchedStake == null ? BigDecimal.ZERO : matchedStake;
        remainingStake = remainingStake == null ? BigDecimal.ZERO : remainingStake;
    }

    public ExchangeExposurePosition(
        String externalOrderId,
        String marketId,
        long selectionId,
        BetSide side,
        BigDecimal stake,
        BigDecimal risk
    ) {
        this(externalOrderId, marketId, selectionId, side, stake, risk, null, null, null);
    }
}
