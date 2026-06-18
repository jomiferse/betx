package com.betx.application;

import java.math.BigDecimal;
import java.time.Instant;

public record BetxInterfaceActivityItem(
    String id,
    String event,
    String selection,
    BigDecimal odds,
    BigDecimal amount,
    String status,
    String result,
    BigDecimal netPnl,
    Instant updatedAt
) {
}
