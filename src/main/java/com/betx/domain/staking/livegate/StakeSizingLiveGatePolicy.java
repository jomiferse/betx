package com.betx.domain.staking.livegate;

import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;

/** Candidate policy/profile pair considered by the live gate. */
public record StakeSizingLiveGatePolicy(
    StakeSizingMode candidatePolicy,
    StakeSizingRiskProfile candidateRiskProfile
) {
}
