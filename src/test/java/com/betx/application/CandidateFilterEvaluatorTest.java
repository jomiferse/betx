package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CandidateFilterEvaluatorTest {
    private final CandidateFilterEvaluator evaluator = new CandidateFilterEvaluator();

    @Test
    void excludeDrawAndOdds4PlusFiltersDraw() {
        CandidateFilterDecisionResult result = evaluator.evaluate(
            CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS,
            SelectionSide.DRAW,
            new BigDecimal("2.50")
        );

        assertThat(result.decision()).isEqualTo(CandidateFilterDecision.WOULD_FILTER);
        assertThat(result.reason()).isEqualTo(CandidateFilterDecisionReason.SELECTION_SIDE_DRAW);
    }

    @Test
    void excludeDrawAndOdds4PlusFiltersOddsGreaterThanOrEqual4() {
        CandidateFilterDecisionResult result = evaluator.evaluate(
            CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS,
            SelectionSide.HOME,
            new BigDecimal("4.00")
        );

        assertThat(result.decision()).isEqualTo(CandidateFilterDecision.WOULD_FILTER);
        assertThat(result.reason()).isEqualTo(CandidateFilterDecisionReason.ODDS_4_PLUS);
    }

    @Test
    void excludeDrawAndOdds4PlusPassesHomeAt250() {
        CandidateFilterDecisionResult result = evaluator.evaluate(
            CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS,
            SelectionSide.HOME,
            new BigDecimal("2.50")
        );

        assertThat(result.decision()).isEqualTo(CandidateFilterDecision.WOULD_PASS);
        assertThat(result.reason()).isEqualTo(CandidateFilterDecisionReason.PASSED);
    }

    @Test
    void excludeOdds4PlusFilters400AndPasses399() {
        assertThat(evaluator.evaluate(CandidateFilterName.EXCLUDE_ODDS_4_PLUS, SelectionSide.HOME, new BigDecimal("4.00")).decision())
            .isEqualTo(CandidateFilterDecision.WOULD_FILTER);
        assertThat(evaluator.evaluate(CandidateFilterName.EXCLUDE_ODDS_4_PLUS, SelectionSide.HOME, new BigDecimal("3.99")).decision())
            .isEqualTo(CandidateFilterDecision.WOULD_PASS);
    }

    @Test
    void onlyOdds150To399PassesBoundsAndFilters400() {
        assertThat(evaluator.evaluate(CandidateFilterName.ONLY_ODDS_1_50_TO_3_99, SelectionSide.HOME, new BigDecimal("1.50")).decision())
            .isEqualTo(CandidateFilterDecision.WOULD_PASS);
        assertThat(evaluator.evaluate(CandidateFilterName.ONLY_ODDS_1_50_TO_3_99, SelectionSide.HOME, new BigDecimal("3.99")).decision())
            .isEqualTo(CandidateFilterDecision.WOULD_PASS);
        assertThat(evaluator.evaluate(CandidateFilterName.ONLY_ODDS_1_50_TO_3_99, SelectionSide.HOME, new BigDecimal("4.00")).decision())
            .isEqualTo(CandidateFilterDecision.WOULD_FILTER);
    }

    @Test
    void excludeDrawAndAwayFiltersDrawAndAwayButPassesHome() {
        assertThat(evaluator.evaluate(CandidateFilterName.EXCLUDE_DRAW_AND_AWAY, SelectionSide.DRAW, new BigDecimal("2.50")).decision())
            .isEqualTo(CandidateFilterDecision.WOULD_FILTER);
        assertThat(evaluator.evaluate(CandidateFilterName.EXCLUDE_DRAW_AND_AWAY, SelectionSide.AWAY, new BigDecimal("2.50")).decision())
            .isEqualTo(CandidateFilterDecision.WOULD_FILTER);
        assertThat(evaluator.evaluate(CandidateFilterName.EXCLUDE_DRAW_AND_AWAY, SelectionSide.HOME, new BigDecimal("2.50")).decision())
            .isEqualTo(CandidateFilterDecision.WOULD_PASS);
    }
}
