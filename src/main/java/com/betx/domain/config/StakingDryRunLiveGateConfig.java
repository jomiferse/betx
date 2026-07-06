package com.betx.domain.config;

import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Runtime dry-run live gate validation config. It observes only and never applies stake. */
public record StakingDryRunLiveGateConfig(
    Boolean enabled,
    StakeSizingMode policy,
    @JsonProperty("risk_profile") StakeSizingRiskProfile riskProfile,
    @JsonProperty("min_settled_joined_required") Integer minSettledJoinedRequired,
    @JsonProperty("representative_scenario") String representativeScenario,
    @JsonProperty("fallback_stake") BigDecimal fallbackStake,
    @JsonProperty("fixed_stake") BigDecimal fixedStake,
    @JsonProperty("emit_logs") Boolean emitLogs,
    @JsonProperty("persist_decisions") Boolean persistDecisions
) {
    public StakingDryRunLiveGateConfig {
        enabled = enabled == null || enabled;
        policy = policy == null ? StakeSizingMode.RISK_ADJUSTED : policy;
        riskProfile = riskProfile == null ? StakeSizingRiskProfile.CONSERVATIVE : riskProfile;
        minSettledJoinedRequired = minSettledJoinedRequired == null ? 100 : Math.max(0, minSettledJoinedRequired);
        representativeScenario = representativeScenario == null || representativeScenario.isBlank()
            ? "SCENARIO_BASE_5_MIN_1"
            : representativeScenario.strip();
        fallbackStake = fallbackStake == null ? BigDecimal.ONE : fallbackStake;
        fixedStake = fixedStake == null ? BigDecimal.ONE : fixedStake;
        emitLogs = emitLogs == null || emitLogs;
        persistDecisions = persistDecisions != null && persistDecisions;
    }

    public static StakingDryRunLiveGateConfig defaults() {
        return new StakingDryRunLiveGateConfig(true, null, null, null, null, null, null, null, null);
    }
}
