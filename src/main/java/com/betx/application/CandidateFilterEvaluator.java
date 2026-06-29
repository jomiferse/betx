package com.betx.application;

import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CandidateFilterEvaluator {
    private static final BigDecimal ODDS_1_50 = new BigDecimal("1.50");
    private static final BigDecimal ODDS_4_00 = new BigDecimal("4.00");

    public List<CandidateFilterEvaluation> evaluateAll(
        BetRecommendation recommendation,
        CandidateFilterSource source,
        Instant evaluatedAt
    ) {
        if (recommendation == null) {
            return List.of();
        }
        Instant timestamp = evaluatedAt == null ? recommendation.lastSeenAt() : evaluatedAt;
        return List.of(
            CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS,
            CandidateFilterName.EXCLUDE_ODDS_4_PLUS,
            CandidateFilterName.ONLY_ODDS_1_50_TO_3_99,
            CandidateFilterName.EXCLUDE_DRAW_AND_AWAY,
            CandidateFilterName.EXCLUDE_NEGATIVE_SEGMENTS_CURRENT
        ).stream()
            .map(filterName -> evaluation(recommendation, filterName, source, timestamp))
            .toList();
    }

    public CandidateFilterDecisionResult evaluate(
        CandidateFilterName filterName,
        SelectionSide selectionSide,
        BigDecimal odds
    ) {
        SelectionSide side = selectionSide == null ? SelectionSide.UNKNOWN : selectionSide;
        return switch (filterName) {
            case EXCLUDE_DRAW_AND_ODDS_4_PLUS -> {
                if (side == SelectionSide.DRAW) {
                    yield filter(CandidateFilterDecisionReason.SELECTION_SIDE_DRAW);
                }
                if (oddsAtLeast4(odds)) {
                    yield filter(CandidateFilterDecisionReason.ODDS_4_PLUS);
                }
                yield pass();
            }
            case EXCLUDE_ODDS_4_PLUS -> oddsAtLeast4(odds)
                ? filter(CandidateFilterDecisionReason.ODDS_4_PLUS)
                : pass();
            case ONLY_ODDS_1_50_TO_3_99 -> {
                if (odds == null) {
                    yield filter(CandidateFilterDecisionReason.MISSING_ODDS);
                }
                if (odds.compareTo(ODDS_1_50) < 0) {
                    yield filter(CandidateFilterDecisionReason.ODDS_BELOW_1_50);
                }
                if (odds.compareTo(ODDS_4_00) >= 0) {
                    yield filter(CandidateFilterDecisionReason.ODDS_OUTSIDE_1_50_TO_3_99);
                }
                yield pass();
            }
            case EXCLUDE_DRAW_AND_AWAY -> {
                if (side == SelectionSide.DRAW) {
                    yield filter(CandidateFilterDecisionReason.SELECTION_SIDE_DRAW);
                }
                if (side == SelectionSide.AWAY) {
                    yield filter(CandidateFilterDecisionReason.SELECTION_SIDE_AWAY);
                }
                yield pass();
            }
            case EXCLUDE_NEGATIVE_SEGMENTS_CURRENT -> {
                if (side == SelectionSide.DRAW) {
                    yield filter(CandidateFilterDecisionReason.HISTORICAL_NEGATIVE_SEGMENT);
                }
                if (side == SelectionSide.AWAY) {
                    yield filter(CandidateFilterDecisionReason.HISTORICAL_NEGATIVE_SEGMENT);
                }
                if (oddsAtLeast4(odds)) {
                    yield filter(CandidateFilterDecisionReason.HISTORICAL_NEGATIVE_SEGMENT);
                }
                yield pass();
            }
        };
    }

    private CandidateFilterEvaluation evaluation(
        BetRecommendation recommendation,
        CandidateFilterName filterName,
        CandidateFilterSource source,
        Instant evaluatedAt
    ) {
        CandidateFilterDecisionResult result = evaluate(
            filterName,
            recommendation.selectionSide(),
            recommendation.latestRecommendedOdds()
        );
        return new CandidateFilterEvaluation(
            UUID.randomUUID().toString(),
            recommendation.id(),
            recommendation.canonicalKey(),
            filterName,
            result.decision(),
            result.reason(),
            recommendation.selectionSide(),
            recommendation.latestRecommendedOdds(),
            recommendation.strategyName(),
            source == null ? CandidateFilterSource.RECOMMENDATION : source,
            evaluatedAt,
            evaluatedAt,
            evaluatedAt,
            1
        );
    }

    private static CandidateFilterDecisionResult pass() {
        return new CandidateFilterDecisionResult(CandidateFilterDecision.WOULD_PASS, CandidateFilterDecisionReason.PASSED);
    }

    private static CandidateFilterDecisionResult filter(CandidateFilterDecisionReason reason) {
        return new CandidateFilterDecisionResult(CandidateFilterDecision.WOULD_FILTER, reason);
    }

    private static boolean oddsAtLeast4(BigDecimal odds) {
        return odds != null && odds.compareTo(ODDS_4_00) >= 0;
    }
}
