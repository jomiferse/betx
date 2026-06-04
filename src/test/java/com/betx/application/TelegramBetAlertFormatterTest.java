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
            .contains("<b>BETX DRY-RUN SIGNAL</b>")
            .contains("Trigger: odds movement -3.09%")
            .contains("<b>Cruzeiro MG v Fluminense</b>")
            .contains("Runner: Cruzeiro MG")
            .contains("Side: BACK")
            .contains("Odds: 1.94 -> current back 1.88 (-3.09%)")
            .contains("Lay: 2.04")
            .contains("Spread: 0.10")
            .contains("Liquidity: 14,486.60")
            .contains("Kickoff: 01 Jun 2026 20:00 CEST")
            .contains("Market: Match Odds")
            .contains("Exchange: betfair")
            .contains("Market ID: 1.258354692")
            .contains("Selection ID: 10901767")
            .contains("Why: liquidity ok, spread ok")
            .contains("Status: DRY-RUN ONLY. No real bet placed.");
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
            .contains("Trigger: liquidity movement +30.00%")
            .contains("Runner: Draw")
            .contains("Why: liquidity ok, spread ok");
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
            .contains("Runner: Runner &amp; Sons")
            .contains("Market: Match &lt;Odds&gt;");
    }

    @Test
    void handlesMissingValuesWithoutInventingDelta() {
        RunnerAnalysis analysis = missingValueAnalysis();

        String message = formatter.format(analysis, Optional.empty());

        assertThat(message)
            .contains("<b>unknown event</b>")
            .contains("Runner: 42")
            .contains("Odds: n/a")
            .contains("Lay: n/a")
            .contains("Spread: n/a")
            .contains("Liquidity: n/a")
            .contains("Kickoff: n/a")
            .contains("Market: n/a")
            .contains("Why: n/a")
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
