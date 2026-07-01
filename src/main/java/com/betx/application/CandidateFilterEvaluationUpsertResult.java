package com.betx.application;

public record CandidateFilterEvaluationUpsertResult(
    CandidateFilterEvaluation evaluation,
    CandidateFilterEvaluationUpsertAction action
) {
    public CandidateFilterEvaluationUpsertResult {
        if (evaluation == null || action == null) {
            throw new IllegalArgumentException("evaluation and action are required.");
        }
    }
}
