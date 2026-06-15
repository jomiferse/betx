package com.betx.application;

import java.math.BigDecimal;

/** Market-level metrics grouped by how many mutually exclusive runners were selected. */
public record BacktestMarketSelectionReport(
    String strategyId,
    int selectedRunners,
    int markets,
    BigDecimal totalStake,
    BigDecimal grossPnl,
    BigDecimal commissionPaid,
    BigDecimal netPnl,
    BigDecimal netRoiPercent,
    BigDecimal maximumExposure
) {
    public BacktestMarketSelectionReport {
        strategyId = strategyId == null || strategyId.isBlank() ? "unknown" : strategyId;
        totalStake = totalStake == null ? BigDecimal.ZERO : totalStake;
        grossPnl = grossPnl == null ? BigDecimal.ZERO : grossPnl;
        commissionPaid = commissionPaid == null ? BigDecimal.ZERO : commissionPaid;
        netPnl = netPnl == null ? BigDecimal.ZERO : netPnl;
        netRoiPercent = netRoiPercent == null ? BigDecimal.ZERO : netRoiPercent;
        maximumExposure = maximumExposure == null ? BigDecimal.ZERO : maximumExposure;
    }
}
