package com.betx.domain.order;

/** Exact exchange execution lifecycle state when BetX has reliable evidence. */
public enum BetExecutionStatus {
    SUBMITTED,
    ACCEPTED,
    UNMATCHED,
    PARTIALLY_MATCHED,
    FULLY_MATCHED,
    REJECTED,
    CANCELLED,
    SETTLED
}
