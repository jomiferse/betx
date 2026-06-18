package com.betx.application;

import java.math.BigDecimal;
import java.time.Instant;

public record BetxInterfaceStatusView(
    InterfaceStatus status,
    String message,
    BigDecimal availableBalance,
    Instant lastUpdatedAt,
    Instant lastCycleAt,
    boolean manualConfirmationEnabled
) {
}
