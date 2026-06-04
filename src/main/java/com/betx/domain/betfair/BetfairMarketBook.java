package com.betx.domain.betfair;

import java.math.BigDecimal;
import java.util.List;

public record BetfairMarketBook(
    String marketId,
    String status,
    boolean inPlay,
    BigDecimal totalMatched,
    List<BetfairRunnerPrice> runners
) {
    public BetfairMarketBook {
        runners = runners == null ? List.of() : List.copyOf(runners);
    }
}
