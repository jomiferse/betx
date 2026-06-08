package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.SignalScore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelegramBetAlertFormatterTest {
    private final TelegramBetAlertFormatter formatter = new TelegramBetAlertFormatter();

    @Test
    void formatsReadableDryRunBetAlertWithOddsMovement() {
        RunnerAnalysis analysis = RunnerAnalysis.from(
            currentSnapshot(),
            RecommendationType.BET,
            "liquidity_ok, spread_ok, favorable_odds_movement, movement_persisted, low_volatility, dry_run_only",
            new SignalScore(82, "High confidence", List.of(
                "Odds moved from 1.94 -> 1.88",
                "Liquidity increased +20.72%",
                "Movement persisted for 3 cycles",
                "Volatility is low"
            ))
        );
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
            .contains("SIGNAL ONLY")
            .contains("Market movement detected")
            .contains("Score: 82/100 🟢 High confidence")
            .contains("Trigger: Odds moved favourably (-3.09%)")
            .contains("<b>Cruzeiro MG vs Fluminense</b>")
            .contains("Bet: Cruzeiro MG to win @ 1.88")
            .contains("Action: BACK on betfair")
            .contains("Previous odds: 1.94 -> 1.88 (-3.09%)")
            .contains("Kickoff: 01 Jun 2026 20:00 CEST")
            .contains("Market: Match Odds")
            .contains("Why this signal:")
            .contains("- Odds moved from 1.94 -&gt; 1.88")
            .contains("- Liquidity increased +20.72%")
            .contains("- Movement persisted for 3 cycles")
            .contains("- Volatility is low")
            .contains("SIGNAL ONLY. No real bet placed.")
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
    void formatsOpenRouterWatchAsBetReviewRequired() {
        RunnerAnalysis analysis = RunnerAnalysis.from(
            new MarketSnapshot(
                "betfair",
                "1.258979050",
                "Match Odds",
                "Almeria v CD Castellon",
                "Spanish Segunda Division",
                Instant.parse("2026-06-08T19:00:00Z"),
                58805L,
                "The Draw",
                BigDecimal.valueOf(3.75),
                BigDecimal.valueOf(3.90),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_201)
            ),
            RecommendationType.BET,
            "liquidity_ok, spread_ok, favorable_liquidity_movement, low_volatility, dry_run_only",
            new SignalScore(70, "High confidence", List.of(
                "Base market quality is acceptable",
                "Liquidity increased +2.44%",
                "Volatility is low"
            ))
        );
        MarketSnapshot previous = new MarketSnapshot(
            "betfair",
            "1.258979050",
            "Match Odds",
            "Almeria v CD Castellon",
            "Spanish Segunda Division",
            Instant.parse("2026-06-08T19:00:00Z"),
            58805L,
            "The Draw",
            BigDecimal.valueOf(3.70),
            BigDecimal.valueOf(3.90),
            BigDecimal.valueOf(0.054),
            BigDecimal.valueOf(1_173)
        );

        String message = formatter.formatLiveConfirmation(
            analysis,
            Optional.of(previous),
            Optional.of(new MatchIntelligenceAssessment(
                "betfair",
                "1.258979050",
                58805L,
                MatchIntelligenceDecision.WATCH,
                60,
                "Almeria have home advantage, but Castellon created danger in the first leg.",
                List.of("First leg ended 1-1", "Castellon generated more attacking volume"),
                List.of("Lineups are not confirmed"),
                List.of(MatchIntelligenceSource.fromUrl("https://example.com/preview"))
            ))
        );

        assertThat(message)
            .contains("BET REVIEW REQUIRED")
            .contains("Market Signal: 70/100")
            .contains("Context Score: 60/100")
            .contains("Final Recommendation: WATCH — no automatic bet")
            .contains("<b>Almeria vs CD Castellon</b>")
            .contains("Selection: Draw")
            .contains("Current odds: 3.75")
            .contains("Previous odds: 3.70 -> 3.75 (+1.35%)")
            .contains("Break-even probability: 26.67%")
            .contains("Edge: uncertain / narrow")
            .contains("Context:")
            .contains("- First leg ended 1-1")
            .contains("- Castellon generated more attacking volume")
            .contains("- Lineups are not confirmed")
            .contains("Recommended action: WATCH until stronger price or confirmation signal.")
            .contains("Suggested rule:")
            .contains("Only consider BACK Draw if odds >= 3.85 or model probability >= 29%.")
            .doesNotContain("Confirm bet?");
    }

    @Test
    void formatsFixtureSeparatorAsVsInEventTitle() {
        RunnerAnalysis analysis = RunnerAnalysis.from(
            new MarketSnapshot(
                "betfair",
                "1.1",
                "Match Odds",
                "Australia v Switzerland",
                "International",
                Instant.parse("2026-06-06T19:00:00Z"),
                42L,
                "The Draw",
                BigDecimal.valueOf(3.90),
                BigDecimal.valueOf(4.00),
                BigDecimal.valueOf(0.025),
                BigDecimal.valueOf(2_000)
            ),
            RecommendationType.BET,
            "liquidity_ok, spread_ok, favorable_odds_movement, dry_run_only"
        );

        String message = formatter.format(analysis, Optional.empty());

        assertThat(message)
            .contains("<b>Australia vs Switzerland</b>")
            .doesNotContain("Australia v Switzerland");
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
