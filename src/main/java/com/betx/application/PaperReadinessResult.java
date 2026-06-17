package com.betx.application;

import java.math.BigDecimal;
import java.util.List;

/** Evidence summary used to allow or block unattended real betting. */
public record PaperReadinessResult(
    PaperReadinessStatus status,
    String strategy,
    int settledTrades,
    int requiredSettledTrades,
    BigDecimal executableRoi,
    BigDecimal requiredExecutableRoi,
    BigDecimal medianClv,
    BigDecimal requiredMedianClv,
    BigDecimal rollingRoi,
    BigDecimal requiredRollingRoi,
    String evidenceStatus,
    List<String> reasons
) {
    public PaperReadinessResult {
        status = status == null ? PaperReadinessStatus.DISABLED : status;
        strategy = strategy == null || strategy.isBlank() ? PaperReadinessService.STRATEGY : strategy;
        executableRoi = executableRoi == null ? BigDecimal.ZERO : executableRoi;
        requiredExecutableRoi = requiredExecutableRoi == null ? BigDecimal.ZERO : requiredExecutableRoi;
        requiredMedianClv = requiredMedianClv == null ? BigDecimal.ZERO : requiredMedianClv;
        rollingRoi = rollingRoi == null ? BigDecimal.ZERO : rollingRoi;
        requiredRollingRoi = requiredRollingRoi == null ? BigDecimal.ZERO : requiredRollingRoi;
        evidenceStatus = evidenceStatus == null || evidenceStatus.isBlank() ? "UNKNOWN" : evidenceStatus;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public boolean allowsAutomaticBetting() {
        return status == PaperReadinessStatus.READY || status == PaperReadinessStatus.DISABLED;
    }
}
