package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelegramBetAlertPolicyTest {
    private final TelegramBetAlertPolicy policy = new TelegramBetAlertPolicy();

    @Test
    void sendsAllOddsMovementAlerts() {
        List<TelegramBetAlertCandidate> candidates = List.of(
            candidate("Team A", 42L, "1.1", "liquidity_ok, spread_ok, favorable_odds_movement, dry_run_only", BigDecimal.valueOf(2.60), BigDecimal.valueOf(2.50), BigDecimal.valueOf(1_000), BigDecimal.valueOf(1_000), BigDecimal.valueOf(0.04)),
            candidate("Team B", 43L, "1.2", "liquidity_ok, spread_ok, favorable_odds_movement, dry_run_only", BigDecimal.valueOf(3.20), BigDecimal.valueOf(3.05), BigDecimal.valueOf(1_100), BigDecimal.valueOf(1_100), BigDecimal.valueOf(0.03))
        );

        TelegramBetAlertSelection selection = policy.select(candidates);

        assertThat(selection.alertsToSend()).hasSize(2);
        assertThat(selection.skippedAlerts()).isEmpty();
    }

    @Test
    void limitsLiquidityMovementAlertsToOnePerMarket() {
        List<TelegramBetAlertCandidate> candidates = List.of(
            candidate("Team A", 42L, "1.1", "liquidity_ok, spread_ok, favorable_liquidity_movement, dry_run_only", BigDecimal.valueOf(2.60), BigDecimal.valueOf(2.60), BigDecimal.valueOf(1_000), BigDecimal.valueOf(1_300), BigDecimal.valueOf(0.03)),
            candidate("Team B", 43L, "1.1", "liquidity_ok, spread_ok, favorable_liquidity_movement, dry_run_only", BigDecimal.valueOf(2.80), BigDecimal.valueOf(2.80), BigDecimal.valueOf(1_000), BigDecimal.valueOf(1_250), BigDecimal.valueOf(0.04))
        );

        TelegramBetAlertSelection selection = policy.select(candidates);

        assertThat(selection.alertsToSend()).singleElement()
            .satisfies(candidate -> assertThat(candidate.analysis().selectionId()).isEqualTo(42L));
        assertThat(selection.skippedAlerts()).singleElement()
            .satisfies(skip -> assertThat(skip.reason()).isEqualTo("liquidity_market_limit"));
    }

    @Test
    void prefersTeamRunnerOverDrawForLiquidityAlerts() {
        List<TelegramBetAlertCandidate> candidates = List.of(
            candidate("The Draw", 43L, "1.1", "liquidity_ok, spread_ok, favorable_liquidity_movement, dry_run_only", BigDecimal.valueOf(3.50), BigDecimal.valueOf(3.50), BigDecimal.valueOf(1_000), BigDecimal.valueOf(1_500), BigDecimal.valueOf(0.02)),
            candidate("Team A", 42L, "1.1", "liquidity_ok, spread_ok, favorable_liquidity_movement, dry_run_only", BigDecimal.valueOf(2.10), BigDecimal.valueOf(2.10), BigDecimal.valueOf(1_000), BigDecimal.valueOf(1_200), BigDecimal.valueOf(0.05))
        );

        TelegramBetAlertSelection selection = policy.select(candidates);

        assertThat(selection.alertsToSend()).singleElement()
            .satisfies(candidate -> assertThat(candidate.displayRunner()).isEqualTo("Team A"));
    }

    @Test
    void doesNotDropOddsMovementAlertsWhenLiquidityAlertExistsInSameMarket() {
        List<TelegramBetAlertCandidate> candidates = List.of(
            candidate("Team A", 42L, "1.1", "liquidity_ok, spread_ok, favorable_odds_movement, dry_run_only", BigDecimal.valueOf(2.60), BigDecimal.valueOf(2.50), BigDecimal.valueOf(1_000), BigDecimal.valueOf(1_000), BigDecimal.valueOf(0.04)),
            candidate("Team B", 43L, "1.1", "liquidity_ok, spread_ok, favorable_liquidity_movement, dry_run_only", BigDecimal.valueOf(2.80), BigDecimal.valueOf(2.80), BigDecimal.valueOf(1_000), BigDecimal.valueOf(1_300), BigDecimal.valueOf(0.03))
        );

        TelegramBetAlertSelection selection = policy.select(candidates);

        assertThat(selection.alertsToSend()).hasSize(2);
        assertThat(selection.alertsToSend()).extracting(candidate -> candidate.trigger().displayLabel())
            .containsExactly("odds movement", "liquidity movement");
        assertThat(selection.skippedAlerts()).isEmpty();
    }

    private TelegramBetAlertCandidate candidate(
        String runnerName,
        long selectionId,
        String marketId,
        String reason,
        BigDecimal previousBack,
        BigDecimal currentBack,
        BigDecimal previousLiquidity,
        BigDecimal currentLiquidity,
        BigDecimal spread
    ) {
        MarketSnapshot previous = new MarketSnapshot(
            "betfair",
            marketId,
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            selectionId,
            runnerName,
            previousBack,
            previousBack.add(BigDecimal.valueOf(0.10)),
            spread,
            previousLiquidity
        );
        MarketSnapshot current = new MarketSnapshot(
            "betfair",
            marketId,
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            selectionId,
            runnerName,
            currentBack,
            currentBack.add(BigDecimal.valueOf(0.10)),
            spread,
            currentLiquidity
        );
        RunnerAnalysis analysis = RunnerAnalysis.from(current, RecommendationType.BET, reason);
        return TelegramBetAlertCandidate.from(analysis, Optional.of(previous));
    }
}
