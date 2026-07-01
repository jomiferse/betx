package com.betx.application;

/** Persisted stake sizing shadow decision and the logical upsert action. */
public record StakeSizingShadowDecisionUpsertResult(
    StakeSizingShadowDecision decision,
    StakeSizingShadowDecisionUpsertAction action
) {
    public StakeSizingShadowDecisionUpsertResult {
        if (decision == null || action == null) {
            throw new IllegalArgumentException("decision and action are required.");
        }
    }
}
