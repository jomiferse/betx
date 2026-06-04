package com.betx.application;

import com.betx.domain.signal.MarketSnapshot;
import java.util.List;

/** Normalized exchange market data plus discovery counters. */
public record ExchangeMarketDataResult(
    List<MarketSnapshot> snapshots,
    int eventsRead,
    int ignoredEvents
) {
    public ExchangeMarketDataResult {
        snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
    }
}
