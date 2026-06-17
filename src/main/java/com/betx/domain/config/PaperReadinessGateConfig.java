package com.betx.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Conservative evidence gate for allowing fully automatic real betting. */
public record PaperReadinessGateConfig(
    Boolean enabled,
    @JsonProperty("minimum_settled_trades") Integer minimumSettledTrades,
    @JsonProperty("required_evidence_status") String requiredEvidenceStatus,
    @JsonProperty("minimum_executable_roi") BigDecimal minimumExecutableRoi,
    @JsonProperty("minimum_median_clv") BigDecimal minimumMedianClv,
    @JsonProperty("rolling_window_size") Integer rollingWindowSize,
    @JsonProperty("minimum_rolling_roi") BigDecimal minimumRollingRoi,
    @JsonProperty("block_on_execution_failure") Boolean blockOnExecutionFailure
) {
    public PaperReadinessGateConfig {
        enabled = enabled != null && enabled;
        minimumSettledTrades = minimumSettledTrades == null ? 100 : minimumSettledTrades;
        requiredEvidenceStatus = requiredEvidenceStatus == null || requiredEvidenceStatus.isBlank()
            ? "CANDIDATE_EDGE"
            : requiredEvidenceStatus.strip().toUpperCase(java.util.Locale.ROOT);
        minimumExecutableRoi = minimumExecutableRoi == null ? new BigDecimal("0.01") : minimumExecutableRoi;
        minimumMedianClv = minimumMedianClv == null ? new BigDecimal("0.00") : minimumMedianClv;
        rollingWindowSize = rollingWindowSize == null ? 100 : rollingWindowSize;
        minimumRollingRoi = minimumRollingRoi == null ? new BigDecimal("0.00") : minimumRollingRoi;
        blockOnExecutionFailure = blockOnExecutionFailure == null || blockOnExecutionFailure;
    }

    public static PaperReadinessGateConfig defaults() {
        return new PaperReadinessGateConfig(null, null, null, null, null, null, null, null);
    }
}
