package com.betx.application;

import java.math.BigDecimal;

/** Execution degradation model used after recommendations are generated. */
public enum BacktestSlippageModel {
    PROFIT_HAIRCUT,
    TOTAL_ODDS_MULTIPLIER;

    public BigDecimal adjustedOdds(BigDecimal originalOdds, BigDecimal slippageRate) {
        BigDecimal rate = slippageRate == null ? BigDecimal.ZERO : slippageRate;
        return switch (this) {
            case PROFIT_HAIRCUT -> BigDecimal.ONE.add(originalOdds.subtract(BigDecimal.ONE).multiply(BigDecimal.ONE.subtract(rate)));
            case TOTAL_ODDS_MULTIPLIER -> originalOdds.multiply(BigDecimal.ONE.subtract(rate));
        };
    }

    public static BacktestSlippageModel fromId(String value) {
        if (value == null || value.isBlank()) {
            return PROFIT_HAIRCUT;
        }
        String normalized = value.strip().replace("-", "_").toUpperCase();
        for (BacktestSlippageModel model : values()) {
            if (model.name().equals(normalized)) {
                return model;
            }
        }
        throw new BacktestValidationException("--slippage-model must be PROFIT_HAIRCUT or TOTAL_ODDS_MULTIPLIER.");
    }
}
