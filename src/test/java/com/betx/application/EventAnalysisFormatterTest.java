package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.SignalScore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventAnalysisFormatterTest {
    private final EventAnalysisFormatter formatter = new EventAnalysisFormatter();

    @Test
    void hidesDryRunOnlyReasonForSignalOutput() {
        List<String> lines = formatter.format(List.of(analysis()), false, false);

        assertThat(lines)
            .anySatisfy(line -> assertThat(line)
                .contains("BET SIGNAL")
                .contains("reason=liquidity_ok, spread_ok, odds_range_ok")
                .doesNotContain("dry_run_only"));
    }

    @Test
    void marksBetConfirmationWhenAutoBettingRequiresConfirmation() {
        List<String> lines = formatter.format(List.of(analysis()), true, true);

        assertThat(lines)
            .anySatisfy(line -> assertThat(line)
                .contains("BET CONFIRMATION")
                .doesNotContain("BET AUTO")
                .contains("reason=liquidity_ok, spread_ok, odds_range_ok")
                .doesNotContain("dry_run_only"));
    }

    @Test
    void marksBetAutoWhenAutoBettingDoesNotRequireConfirmation() {
        List<String> lines = formatter.format(List.of(analysis()), true, false);

        assertThat(lines)
            .anySatisfy(line -> assertThat(line)
                .contains("BET AUTO")
                .contains("reason=liquidity_ok, spread_ok, odds_range_ok")
                .doesNotContain("dry_run_only"));
    }

    private RunnerAnalysis analysis() {
        return new RunnerAnalysis(
            "betfair",
            "1.1",
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-06T19:00:00Z"),
            42L,
            "Team A",
            BigDecimal.valueOf(2.50),
            BigDecimal.valueOf(2.54),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(1_500),
            RecommendationType.BET,
            "liquidity_ok, spread_ok, odds_range_ok, dry_run_only",
            new SignalScore(85, "High confidence", List.of("test"))
        );
    }
}
