package com.betx.domain.staking;

import java.math.BigDecimal;

/** One explainable multiplier or cap applied while sizing a stake. */
public record StakeSizingAdjustment(
    String name,
    BigDecimal multiplier,
    String reason
) {
    public StakeSizingAdjustment {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required.");
        }
        if (multiplier == null) {
            throw new IllegalArgumentException("multiplier is required.");
        }
        name = name.strip();
        reason = reason == null || reason.isBlank() ? "N/A" : reason.strip();
    }
}
