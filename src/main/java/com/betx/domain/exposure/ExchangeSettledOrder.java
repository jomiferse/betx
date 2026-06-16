package com.betx.domain.exposure;

import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.time.Instant;

/** Real exchange order settlement reported by the exchange. */
public record ExchangeSettledOrder(
    String externalOrderId,
    String marketId,
    long selectionId,
    BetSide side,
    BigDecimal realizedProfitLoss,
    Instant settledAt
) {
    public ExchangeSettledOrder {
        externalOrderId = externalOrderId == null ? null : externalOrderId.strip();
        marketId = marketId == null ? "" : marketId.strip();
        side = side == null ? BetSide.BACK : side;
        realizedProfitLoss = realizedProfitLoss == null ? BigDecimal.ZERO : realizedProfitLoss;
    }
}
