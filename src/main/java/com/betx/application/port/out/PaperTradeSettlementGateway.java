package com.betx.application.port.out;

import com.betx.application.BacktestOutcome;
import com.betx.application.PaperTrade;
import com.betx.domain.config.BetxConfig;
import java.util.Optional;

/** Reads settled market outcomes for paper trades without placing orders. */
public interface PaperTradeSettlementGateway {
    String exchangeName();

    Optional<BacktestOutcome> outcome(BetxConfig config, PaperTrade trade);
}
