package com.betx.domain.config;

import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Shadow-only staking evaluation configuration. */
public record StakingShadowConfig(
    Boolean enabled,
    List<StakeSizingMode> policies,
    @JsonProperty("risk_profiles") List<StakeSizingRiskProfile> riskProfiles
) {
    public StakingShadowConfig {
        enabled = enabled == null || enabled;
        policies = policies == null || policies.isEmpty()
            ? List.of(
                StakeSizingMode.FLAT,
                StakeSizingMode.RISK_ADJUSTED,
                StakeSizingMode.TIERED_CONFIDENCE,
                StakeSizingMode.FRACTIONAL_KELLY_SHADOW
            )
            : List.copyOf(policies);
        riskProfiles = riskProfiles == null || riskProfiles.isEmpty()
            ? List.of(StakeSizingRiskProfile.CONSERVATIVE, StakeSizingRiskProfile.BALANCED)
            : List.copyOf(riskProfiles);
    }

    public static StakingShadowConfig defaults() {
        return new StakingShadowConfig(true, null, null);
    }
}
