package com.betx.application;

public enum CandidateFilterDecisionReason {
    PASSED,
    SELECTION_SIDE_DRAW,
    SELECTION_SIDE_AWAY,
    ODDS_4_PLUS,
    ODDS_BELOW_1_50,
    ODDS_OUTSIDE_1_50_TO_3_99,
    HISTORICAL_NEGATIVE_SEGMENT,
    MISSING_ODDS
}
