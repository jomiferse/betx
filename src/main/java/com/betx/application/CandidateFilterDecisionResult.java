package com.betx.application;

public record CandidateFilterDecisionResult(
    CandidateFilterDecision decision,
    CandidateFilterDecisionReason reason
) {
}
