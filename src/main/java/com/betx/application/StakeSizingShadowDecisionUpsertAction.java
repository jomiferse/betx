package com.betx.application;

/** Outcome of upserting a stake sizing shadow decision. */
public enum StakeSizingShadowDecisionUpsertAction {
    CREATED,
    UPDATED_DECISION_CHANGED,
    UPDATED_STAKE_CHANGED,
    UPDATED_REASON_CHANGED,
    OBSERVED_UNCHANGED
}
