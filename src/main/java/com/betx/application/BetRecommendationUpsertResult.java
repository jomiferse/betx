package com.betx.application;

public record BetRecommendationUpsertResult(
    BetRecommendation recommendation,
    BetRecommendationUpsertAction action
) {
    public BetRecommendationUpsertResult {
        if (recommendation == null) {
            throw new IllegalArgumentException("recommendation is required.");
        }
        if (action == null) {
            throw new IllegalArgumentException("action is required.");
        }
    }
}
