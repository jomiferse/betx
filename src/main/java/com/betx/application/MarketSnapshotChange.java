package com.betx.application;

import com.betx.domain.signal.MarketSnapshot;

/** Changes between two snapshots for the same runner. */
public record MarketSnapshotChange(
    MarketSnapshot previous,
    MarketSnapshot current,
    NumericChange back,
    NumericChange lay,
    NumericChange spread,
    NumericChange liquidity
) {
}
