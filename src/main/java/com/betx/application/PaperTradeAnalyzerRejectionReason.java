package com.betx.application;

/** Aggregate analyzer outcomes for prospective draw-only paper trading. */
public enum PaperTradeAnalyzerRejectionReason {
    INSUFFICIENT_HISTORY,
    NOT_DRAW,
    ODDS_UNCHANGED,
    MOVEMENT_BELOW_THRESHOLD,
    LIQUIDITY_BELOW_THRESHOLD,
    SPREAD_ABOVE_THRESHOLD,
    ODDS_OUT_OF_RANGE,
    CONFIDENCE_BELOW_THRESHOLD,
    MARKET_TOO_FAR_FROM_START,
    MARKET_TOO_CLOSE_TO_START,
    ACCEPTED
}
