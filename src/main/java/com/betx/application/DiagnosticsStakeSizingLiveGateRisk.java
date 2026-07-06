package com.betx.application;

import java.math.BigDecimal;

/** Drawdown snapshot used by stake sizing live gate diagnostics. */
public record DiagnosticsStakeSizingLiveGateRisk(
    BigDecimal currentDrawdown,
    BigDecimal maxAllowedDrawdown
) {
}
