package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelegramBetAlertFormatterTest {
    private final TelegramBetAlertFormatter formatter = new TelegramBetAlertFormatter();

    @Test
    void formatsReadableDryRunBetAlertWithOddsMovement() {
        RunnerAnalysis analysis = RunnerAnalysis.from(currentSnapshot(), RecommendationType.BET, "liquidity_ok, spread_ok, favorable_odds_movement, dry_run_only");
        MarketSnapshot previous = new MarketSnapshot(
            "betfair",
            "1.258354692",
            "Match Odds",
            "Cruzeiro MG v Fluminense",
            "Brazil Serie A",
            Instant.parse("2026-06-01T18:00:00Z"),
            10901767L,
            "Cruzeiro MG",
            BigDecimal.valueOf(1.94),
            BigDecimal.valueOf(2.04),
            BigDecimal.valueOf(0.10),
            BigDecimal.valueOf(12_000)
        );

        String message = formatter.format(analysis, Optional.of(previous));

        assertThat(message)
            .contains("<b>BETX SIGNAL</b>")
            .contains("DRY-RUN ONLY")
            .contains("Trigger: Odds moved favourably (-3.09%)")
            .contains("<b>Cruzeiro MG v Fluminense</b>")
            .contains("Bet: Cruzeiro MG to win @ 1.88")
            .contains("Action: BACK on betfair")
            .contains("Previous odds: 1.94 -> 1.88 (-3.09%)")
            .contains("Kickoff: 01 Jun 2026 20:00 CEST")
            .contains("Market: Match Odds")
            .contains("Why this signal:")
            .contains("- Liquidity OK")
            .contains("- Spread OK")
            .contains("DRY-RUN ONLY. No real bet placed.")
            .doesNotContain("Market ID")
            .doesNotContain("Selection ID")
            .doesNotContain("1.258354692")
            .doesNotContain("10901767")
            .doesNotContain("liquidity_ok")
            .doesNotContain("favorable_odds_movement");
    }

    @Test
    void formatsTheDrawAsDrawWithLiquidityTrigger() {
        RunnerAnalysis analysis = RunnerAnalysis.from(
            new MarketSnapshot(
                "betfair",
                "1.258354693",
                "Match Odds",
                "Cruzeiro MG v Fluminense",
                "Brazil Serie A",
                Instant.parse("2026-06-01T18:00:00Z"),
                10901768L,
                "The Draw",
                BigDecimal.valueOf(3.20),
                BigDecimal.valueOf(3.30),
                BigDecimal.valueOf(0.05),
                BigDecimal.valueOf(1_300)
            ),
            RecommendationType.BET,
            "liquidity_ok, spread_ok, favorable_liquidity_movement, dry_run_only"
        );
        MarketSnapshot previous = new MarketSnapshot(
            "betfair",
            "1.258354693",
            "Match Odds",
            "Cruzeiro MG v Fluminense",
            "Brazil Serie A",
            Instant.parse("2026-06-01T18:00:00Z"),
            10901768L,
            "The Draw",
            BigDecimal.valueOf(3.20),
            BigDecimal.valueOf(3.30),
            BigDecimal.valueOf(0.05),
            BigDecimal.valueOf(1_000)
        );

        String message = formatter.format(analysis, Optional.of(previous));

        assertThat(message)
            .contains("Trigger: Liquidity improved (+30.00%)")
            .contains("Bet: Draw @ 3.20")
            .contains("- Liquidity OK")
            .contains("- Spread OK")
            .doesNotContain("The Draw")
            .doesNotContain("favorable_liquidity_movement");
    }

    @Test
    void escapesHtmlValues() {
        RunnerAnalysis analysis = RunnerAnalysis.from(
            new MarketSnapshot(
                "betfair",
                "1.1",
                "Match <Odds>",
                "AFC <Home> & Away",
                "Cup",
                Instant.parse("2026-06-01T18:00:00Z"),
                42L,
                "Runner & Sons",
                BigDecimal.valueOf(2.50),
                BigDecimal.valueOf(2.60),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_200)
            ),
            RecommendationType.BET,
            "dry_run_only"
        );

        String message = formatter.format(analysis, Optional.empty());

        assertThat(message)
            .contains("AFC &lt;Home&gt; &amp; Away")
            .contains("Bet: Runner &amp; Sons to win @ 2.50")
            .contains("Market: Match &lt;Odds&gt;");
    }

    @Test
    void handlesMissingValuesWithoutInventingDelta() {
        RunnerAnalysis analysis = missingValueAnalysis();

        String message = formatter.format(analysis, Optional.empty());

        assertThat(message)
            .contains("<b>unknown event</b>")
            .contains("Bet: 42 to win @ n/a")
            .contains("Kickoff: n/a")
            .contains("Market: n/a")
            .contains("Why this signal:\n- n/a")
            .doesNotContain("dry_run_only");
    }

    private RunnerAnalysis missingValueAnalysis() {
        return new RunnerAnalysis(
            "betfair",
            "1.1",
            null,
            null,
            null,
            null,
            42L,
            null,
            null,
            null,
            null,
            null,
            RecommendationType.BET,
            "dry_run_only"
        );
    }

    private MarketSnapshot currentSnapshot() {
        return new MarketSnapshot(
            "betfair",
            "1.258354692",
            "Match Odds",
            "Cruzeiro MG v Fluminense",
            "Brazil Serie A",
            Instant.parse("2026-06-01T18:00:00Z"),
            10901767L,
            "Cruzeiro MG",
            BigDecimal.valueOf(1.88),
            BigDecimal.valueOf(2.04),
            BigDecimal.valueOf(0.10),
            BigDecimal.valueOf(14_486.60)
        );
    }
}
