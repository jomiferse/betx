package com.betx.application;

/** Durable lifecycle state for a prospective paper trade. */
public enum PaperTradeStatus {
    RECOMMENDED,
    EXECUTED,
    CLOSED,
    SETTLED,
    EXECUTION_FAILED
}
