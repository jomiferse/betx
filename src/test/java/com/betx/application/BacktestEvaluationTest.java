package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestEvaluationTest {
    @Test
    void groupsTradesByOddsRunnerTypeConfidenceCompetitionAndMovement() {
        BacktestEvaluation evaluation = BacktestEvaluation.from(List.of(
            trade("SP1", 1L, "Team A", "2.50", "7.50", BacktestOutcome.WIN, "High confidence", "-4.00"),
            trade("SP1", 3L, "Team B", "3.20", "-5.00", BacktestOutcome.LOSE, "Medium confidence", "2.00")
        ));

        assertThat(evaluation.segments(BacktestSegmentType.ODDS_BAND))
            .extracting(BacktestSegment::name)
            .containsExactly("2.01-3.00", "3.01-6.00");
        assertThat(evaluation.segments(BacktestSegmentType.RUNNER_TYPE))
            .extracting(BacktestSegment::name)
            .containsExactly("HOME", "AWAY");
        assertThat(evaluation.segments(BacktestSegmentType.CONFIDENCE))
            .extracting(BacktestSegment::name)
            .containsExactly("High confidence", "Medium confidence");
        assertThat(evaluation.segments(BacktestSegmentType.COMPETITION))
            .singleElement()
            .satisfies(segment -> {
                assertThat(segment.name()).isEqualTo("SP1");
                assertThat(segment.trades()).isEqualTo(2);
                assertThat(segment.roiPercent()).isEqualByComparingTo("25.00");
                assertThat(segment.maxDrawdown()).isEqualByComparingTo("5.00");
            });
        assertThat(evaluation.segments(BacktestSegmentType.ODDS_MOVEMENT))
            .extracting(BacktestSegment::name)
            .containsExactly("drop -10% to -3%", "drift +1% to +5%");
    }

    @Test
    void backtestResultIncludesEvaluationForItsTrades() {
        BacktestResult result = BacktestResult.from(2, 2, List.of(
            trade("SP1", 1L, "Team A", "2.50", "7.50", BacktestOutcome.WIN, "High confidence", "-4.00")
        ));

        assertThat(result.evaluation().segments(BacktestSegmentType.ODDS_BAND))
            .singleElement()
            .satisfies(segment -> assertThat(segment.name()).isEqualTo("2.01-3.00"));
    }

    @Test
    void backtestResultHasEmptyEvaluationWhenThereAreNoTrades() {
        BacktestResult result = BacktestResult.from(2, 2, List.of());

        assertThat(result.evaluation().segments(BacktestSegmentType.ODDS_BAND)).isEmpty();
    }

    @Test
    void ranksSegmentsByTradeCountThenRoi() {
        BacktestEvaluation evaluation = BacktestEvaluation.from(List.of(
            trade("SP1", 1L, "Team A", "2.50", "7.50", BacktestOutcome.WIN, "High confidence", "-4.00"),
            trade("SP1", 1L, "Team A", "2.55", "-5.00", BacktestOutcome.LOSE, "High confidence", "-4.00"),
            trade("SP2", 3L, "Team B", "3.20", "11.00", BacktestOutcome.WIN, "Medium confidence", "2.00"),
            trade("SP3", 3L, "Team C", "3.30", "11.50", BacktestOutcome.WIN, "Medium confidence", "2.00")
        ));

        assertThat(evaluation.segments(BacktestSegmentType.COMPETITION))
            .extracting(BacktestSegment::name)
            .containsExactly("SP1", "SP3", "SP2");
    }

    private static BacktestTrade trade(
        String competition,
        long selectionId,
        String runnerName,
        String odds,
        String profitLoss,
        BacktestOutcome outcome,
        String confidenceLabel,
        String oddsMovementPercent
    ) {
        return new BacktestTrade(
            Instant.parse("2026-06-01T10:00:00Z").plusSeconds(selectionId),
            "football-data",
            "SP1-2026-06-01-team-a-team-b",
            "Team A v Team B",
            "Match Odds",
            selectionId,
            runnerName,
            BetSide.BACK,
            new BigDecimal(odds),
            new BigDecimal("5"),
            outcome,
            new BigDecimal(profitLoss),
            competition,
            confidenceLabel,
            new BigDecimal(oddsMovementPercent),
            BacktestRunnerType.fromSelectionId(selectionId)
        );
    }
}
