package com.betx.domain.staking;

/** Stake sizing policy supported by the pure domain engine. */
public enum StakeSizingMode {
    FLAT,
    TIERED_CONFIDENCE,
    RISK_ADJUSTED,
    FRACTIONAL_KELLY_SHADOW
}
