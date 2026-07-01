package com.betx.domain.staking;

import java.math.BigDecimal;

/** Conservative profile multiplier applied before hard safety limits. */
public enum StakeSizingRiskProfile {
    CONSERVATIVE("0.75"),
    BALANCED("1.00"),
    AGGRESSIVE("1.25");

    private final BigDecimal multiplier;

    StakeSizingRiskProfile(String multiplier) {
        this.multiplier = new BigDecimal(multiplier);
    }

    public BigDecimal multiplier() {
        return multiplier;
    }
}
