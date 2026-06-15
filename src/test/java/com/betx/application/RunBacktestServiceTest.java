package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BacktestHistoryReader;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.StrategyConfig;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunBacktestServiceTest {
    private static final ConfigPath CONFIG_PATH = new ConfigPath(Path.of("betx.yml"));
    private static final Path INPUT_PATH = Path.of("history.csv");

    @Test
    void replaysRowsChronologicallyAndPlacesFirstSignalPerRunner() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            row("2026-06-01T10:02:00Z", 42L, BigDecimal.valueOf(2.50), BigDecimal.valueOf(1_200), BacktestOutcome.WIN),
            row("2026-06-01T10:01:00Z", 42L, BigDecimal.valueOf(2.60), BigDecimal.valueOf(1_000), BacktestOutcome.WIN),
            row("2026-06-01T10:03:00Z", 42L, BigDecimal.valueOf(2.45), BigDecimal.valueOf(1_300), BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestResult result = service.run(CONFIG_PATH, INPUT_PATH);

        assertThat(reader.paths()).containsExactly(INPUT_PATH);
        assertThat(result.rowsRead()).isEqualTo(3);
        assertThat(result.runnersAnalyzed()).isEqualTo(3);
        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.observedAt()).isEqualTo(Instant.parse("2026-06-01T10:02:00Z"));
            assertThat(trade.selectionId()).isEqualTo(42L);
            assertThat(trade.odds()).isEqualByComparingTo("2.50");
            assertThat(trade.stake()).isEqualByComparingTo("5");
            assertThat(trade.profitLoss()).isEqualByComparingTo("7.50");
            assertThat(trade.competitionName()).isEqualTo("La Liga");
            assertThat(trade.confidenceLabel()).isEqualTo("High confidence");
            assertThat(trade.oddsMovementPercent()).isEqualByComparingTo("-3.84615385");
            assertThat(trade.runnerType()).isEqualTo(BacktestRunnerType.UNKNOWN);
        });
        assertThat(result.wins()).isEqualTo(1);
        assertThat(result.losses()).isZero();
        assertThat(result.totalStaked()).isEqualByComparingTo("5");
        assertThat(result.profitLoss()).isEqualByComparingTo("7.50");
        assertThat(result.roiPercent()).isEqualByComparingTo("150.00");
    }

    @Test
    void settlesWinningAndLosingBackTradesAndCalculatesDrawdown() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            row("2026-06-01T10:00:00Z", 42L, BigDecimal.valueOf(2.60), BigDecimal.valueOf(1_000), BacktestOutcome.WIN),
            row("2026-06-01T10:01:00Z", 42L, BigDecimal.valueOf(2.50), BigDecimal.valueOf(1_200), BacktestOutcome.WIN),
            row("2026-06-01T10:02:00Z", 43L, BigDecimal.valueOf(3.20), BigDecimal.valueOf(900), BacktestOutcome.LOSE),
            row("2026-06-01T10:03:00Z", 43L, BigDecimal.valueOf(3.00), BigDecimal.valueOf(1_100), BacktestOutcome.LOSE)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestResult result = service.run(CONFIG_PATH, INPUT_PATH);

        assertThat(result.trades()).hasSize(2);
        assertThat(result.wins()).isEqualTo(1);
        assertThat(result.losses()).isEqualTo(1);
        assertThat(result.strikeRatePercent()).isEqualByComparingTo("50.00");
        assertThat(result.totalStaked()).isEqualByComparingTo("10");
        assertThat(result.profitLoss()).isEqualByComparingTo("2.50");
        assertThat(result.roiPercent()).isEqualByComparingTo("25.00");
        assertThat(result.maxDrawdown()).isEqualByComparingTo("5.00");
    }

    @Test
    void disabledValueFootballStrategyProducesNoTrades() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            defaults.storage(),
            defaults.risk(),
            List.of(new StrategyConfig("value-football", false, new BigDecimal("0.06"), BigDecimal.valueOf(500))),
            defaults.ml(),
            defaults.intelligence()
        );
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            row("2026-06-01T10:00:00Z", 42L, BigDecimal.valueOf(2.60), BigDecimal.valueOf(1_000), BacktestOutcome.WIN),
            row("2026-06-01T10:01:00Z", 42L, BigDecimal.valueOf(2.50), BigDecimal.valueOf(1_200), BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(config), reader);

        BacktestResult result = service.run(CONFIG_PATH, INPUT_PATH);

        assertThat(result.rowsRead()).isEqualTo(2);
        assertThat(result.runnersAnalyzed()).isZero();
        assertThat(result.trades()).isEmpty();
    }

    @Test
    void infersFootballDataRunnerTypeFromSelectionId() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            row("2026-06-01T10:00:00Z", 1L, BigDecimal.valueOf(2.60), BigDecimal.valueOf(1_000), BacktestOutcome.WIN),
            row("2026-06-01T10:01:00Z", 1L, BigDecimal.valueOf(2.50), BigDecimal.valueOf(1_200), BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestResult result = service.run(CONFIG_PATH, INPUT_PATH);

        assertThat(result.trades()).singleElement()
            .satisfies(trade -> assertThat(trade.runnerType()).isEqualTo(BacktestRunnerType.HOME));
    }

    @Test
    void validatesRequestedLeaguesAndRunsWalkForwardOnNextSeasonOnly() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            row("2025-06-01T10:00:00Z", "SP1", "SP1-2025-a", 42L, "Runner 42", "2.50", "1200", BacktestOutcome.WIN),
            row("2025-06-01T10:01:00Z", "SP1", "SP1-2025-a", 42L, "Runner 42", "2.45", "1200", BacktestOutcome.WIN),
            row("2026-06-01T10:00:00Z", "SP1", "SP1-2026-a", 43L, "Runner 43", "2.50", "1200", BacktestOutcome.LOSE),
            row("2026-06-01T10:01:00Z", "SP1", "SP1-2026-a", 43L, "Runner 43", "2.45", "1200", BacktestOutcome.LOSE),
            row("2026-06-01T10:00:00Z", "E0", "E0-2026-a", 44L, "Runner 44", "2.50", "1200", BacktestOutcome.WIN),
            row("2026-06-01T10:01:00Z", "E0", "E0-2026-a", 44L, "Runner 44", "2.45", "1200", BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestRobustnessReport report = service.runRobustness(
            CONFIG_PATH,
            INPUT_PATH,
            List.of("SP1", "E0", "D1"),
            List.of(new BigDecimal("-1"), new BigDecimal("-3"))
        );

        assertThat(report.leagueReports())
            .extracting(BacktestLeagueReport::competitionName)
            .containsExactly("SP1", "E0", "D1");
        assertThat(report.leagueReports().get(0).result().trades()).hasSize(2);
        assertThat(report.leagueReports().get(1).result().trades()).hasSize(1);
        assertThat(report.leagueReports().get(2).hasData()).isFalse();
        assertThat(report.walkForwardValidations())
            .filteredOn(validation -> validation.competitionName().equals("SP1"))
            .singleElement()
            .satisfies(validation -> {
                assertThat(validation.status()).isEqualTo(BacktestWalkForwardStatus.EVALUATED);
                assertThat(validation.trainSeason()).isEqualTo(2025);
                assertThat(validation.evaluationSeason()).isEqualTo(2026);
                assertThat(validation.selectedThreshold()).isEqualByComparingTo("-1");
                assertThat(validation.trainResult().roiPercent()).isGreaterThan(BigDecimal.ZERO);
                assertThat(validation.evaluationResult().roiPercent()).isLessThan(BigDecimal.ZERO);
            });
        assertThat(report.walkForwardValidations())
            .filteredOn(validation -> validation.competitionName().equals("E0"))
            .singleElement()
            .satisfies(validation -> assertThat(validation.status()).isEqualTo(BacktestWalkForwardStatus.INSUFFICIENT_SEASONS));
        assertThat(report.sensitivityReports())
            .filteredOn(sensitivity -> sensitivity.competitionName().equals("SP1"))
            .extracting(BacktestSensitivityReport::threshold)
            .containsExactly(new BigDecimal("-1"), new BigDecimal("-3"));
    }

    @Test
    void comparesStrategiesAndRanksThemByPerformance() {
        RecordingHistoryReader reader = new RecordingHistoryReader(comparisonRows());
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L);

        assertThat(report.strategyReports())
            .extracting(BacktestStrategyReport::strategyId)
            .containsExactly("favorite", "home-favorite", "draw", "random", "value-football", "value-football-draw-only", "away-underdog");
        assertThat(report.strategyReports())
            .extracting(BacktestStrategyReport::rank)
            .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(report.strategyReports().getFirst().result().roiPercent()).isGreaterThan(report.strategyReports().getLast().result().roiPercent());
        assertThat(report.strategyReports())
            .filteredOn(strategy -> strategy.strategyId().equals("favorite"))
            .singleElement()
            .satisfies(strategy -> {
                assertThat(strategy.result().trades()).hasSize(3);
                assertThat(strategy.result().roiPercent()).isEqualByComparingTo("40.00");
                assertThat(strategy.result().strikeRatePercent()).isEqualByComparingTo("66.67");
            });
    }

    @Test
    void reportsLeagueMetricsPerStrategy() {
        RecordingHistoryReader reader = new RecordingHistoryReader(comparisonRows());
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L);

        assertThat(report.leagueReports())
            .filteredOn(league -> league.strategyId().equals("favorite"))
            .extracting(BacktestStrategyLeagueReport::competitionName)
            .containsExactly("E0", "SP1");
        assertThat(report.leagueReports())
            .filteredOn(league -> league.strategyId().equals("favorite") && league.competitionName().equals("SP1"))
            .singleElement()
            .satisfies(league -> {
                assertThat(league.result().trades()).hasSize(2);
                assertThat(league.result().roiPercent()).isEqualByComparingTo("0.00");
                assertThat(league.result().strikeRatePercent()).isEqualByComparingTo("50.00");
            });
    }

    @Test
    void randomStrategyIsDeterministicForSameSeed() {
        RecordingHistoryReader reader = new RecordingHistoryReader(comparisonRows());
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport first = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L);
        BacktestComparisonReport second = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L);

        List<BacktestTrade> firstRandomTrades = first.strategyReports().stream()
            .filter(strategy -> strategy.strategyId().equals("random"))
            .findFirst()
            .orElseThrow()
            .result()
            .trades();
        List<BacktestTrade> secondRandomTrades = second.strategyReports().stream()
            .filter(strategy -> strategy.strategyId().equals("random"))
            .findFirst()
            .orElseThrow()
            .result()
            .trades();

        assertThat(secondRandomTrades).isEqualTo(firstRandomTrades);
        assertThat(second.strategyReports()).isEqualTo(first.strategyReports());
        assertThat(second.leagueReports()).isEqualTo(first.leagueReports());
    }

    @Test
    void settlesMarketsWithMultipleBackedRunnersAndAppliesCommissionOnce() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T10:00:00Z", "SP1", "sp1-1", 1L, "Team A", "2.00", BacktestOutcome.LOSE),
            comparisonRow("2026-06-01T10:00:00Z", "SP1", "sp1-1", 2L, "Draw", "3.20", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T10:00:00Z", "SP1", "sp1-1", 3L, "Team B", "4.00", BacktestOutcome.LOSE),
            comparisonRow("2026-06-01T10:01:00Z", "SP1", "sp1-1", 1L, "Team A", "1.95", BacktestOutcome.LOSE),
            comparisonRow("2026-06-01T10:01:00Z", "SP1", "sp1-1", 2L, "Draw", "3.20", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T10:01:00Z", "SP1", "sp1-1", 3L, "Team B", "3.90", BacktestOutcome.LOSE)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, new BigDecimal("0.05"));

        BacktestStrategyReport valueFootball = report.strategyReports().stream()
            .filter(strategy -> strategy.strategyId().equals("value-football"))
            .findFirst()
            .orElseThrow();
        assertThat(valueFootball.marketResults()).singleElement().satisfies(market -> {
            assertThat(market.selectedRunners()).isEqualTo(3);
            assertThat(market.totalStake()).isEqualByComparingTo("15");
            assertThat(market.grossPnl()).isEqualByComparingTo("1.00");
            assertThat(market.commissionPaid()).isEqualByComparingTo("0.0500");
            assertThat(market.netPnl()).isEqualByComparingTo("0.9500");
            assertThat(market.maximumExposure()).isEqualByComparingTo("15");
        });
        assertThat(valueFootball.marketsWithMultipleSelectionsPercent()).isEqualByComparingTo("100.00");
        assertThat(valueFootball.netProfitLoss()).isEqualByComparingTo("0.9500");
        assertThat(valueFootball.netRoiPercent()).isEqualByComparingTo("6.33");
    }

    @Test
    void losingAndBreakEvenMarketsPayNoCommission() {
        List<BacktestTrade> losingTrades = List.of(new BacktestTrade(
            Instant.parse("2026-06-01T10:00:00Z"),
            "football-data",
            "sp1-1",
            "Team A v Team B",
            "Match Odds",
            1L,
            "Team A",
            com.betx.domain.signal.BetSide.BACK,
            new BigDecimal("2.00"),
            new BigDecimal("5"),
            BacktestOutcome.LOSE,
            new BigDecimal("-5"),
            "SP1",
            "Benchmark",
            null,
            BacktestRunnerType.HOME
        ));

        BacktestMarketResult result = BacktestMarketResult.from("favorite", losingTrades, new BigDecimal("0.05"));

        assertThat(result.grossPnl()).isEqualByComparingTo("-5");
        assertThat(result.commissionPaid()).isEqualByComparingTo("0");
        assertThat(result.netPnl()).isEqualByComparingTo("-5");
    }

    @Test
    void winningMarketsPayCommissionOnceOnPositiveGrossMarketProfit() {
        List<BacktestTrade> winningTrades = List.of(new BacktestTrade(
            Instant.parse("2026-06-01T10:00:00Z"),
            "football-data",
            "sp1-1",
            "Team A v Team B",
            "Match Odds",
            1L,
            "Team A",
            com.betx.domain.signal.BetSide.BACK,
            new BigDecimal("3.00"),
            new BigDecimal("5"),
            BacktestOutcome.WIN,
            new BigDecimal("10"),
            "SP1",
            "2025/26",
            "closing-average",
            "Benchmark",
            null,
            BacktestRunnerType.HOME
        ));

        BacktestMarketResult result = BacktestMarketResult.from("favorite", winningTrades, new BigDecimal("0.05"));

        assertThat(result.grossPnl()).isEqualByComparingTo("10");
        assertThat(result.commissionPaid()).isEqualByComparingTo("0.5000");
        assertThat(result.netPnl()).isEqualByComparingTo("9.5000");
    }

    @Test
    void comparisonReportIsDeterministicForSameSeedAndCommissionRate() {
        RecordingHistoryReader reader = new RecordingHistoryReader(comparisonRows());
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport first = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, new BigDecimal("0.05"));
        BacktestComparisonReport second = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, new BigDecimal("0.05"));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void pairedOpeningClosingRowsFeedOpeningHistoryIntoClosingAnalysis() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "sp1-1", 1L, "Team A", "2.60", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-1", 1L, "Team A", "2.50", BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO);

        assertThat(report.datasetCapability()).isEqualTo(BacktestDatasetCapability.OPENING_CLOSING);
        assertThat(report.oddsSource()).isEqualTo("opening-closing");
        assertThat(report.pricingMode()).isEqualTo("bookmaker");
        assertThat(report.strategyReports())
            .filteredOn(strategy -> strategy.strategyId().equals("value-football"))
            .singleElement()
            .satisfies(strategy -> assertThat(strategy.result().trades()).singleElement()
                .satisfies(trade -> {
                    assertThat(trade.observedAt()).isEqualTo(Instant.parse("2026-06-01T16:00:00Z"));
                    assertThat(trade.oddsMovementPercent()).isEqualByComparingTo("-3.84615385");
                }));
    }

    @Test
    void slippageScenariosDegradeDrawOnlyExecutionOddsWithoutChangingRecommendations() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "sp1-draw", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-draw", 2L, "Draw", "3.70", BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO, new BigDecimal("0.02"));

        assertThat(report.oddsSlippageRate()).isEqualByComparingTo("0.02");
        assertThat(report.strategyReports())
            .filteredOn(strategy -> strategy.strategyId().equals("value-football-draw-only"))
            .singleElement()
            .satisfies(strategy -> assertThat(strategy.result().trades()).singleElement()
                .satisfies(trade -> {
                    assertThat(trade.odds()).isEqualByComparingTo("3.6460000000");
                    assertThat(trade.profitLoss()).isEqualByComparingTo("13.2300000000");
                }));
        assertThat(report.slippageReports())
            .filteredOn(scenario -> scenario.strategyId().equals("value-football-draw-only"))
            .extracting(BacktestSlippageReport::slippageRate, BacktestSlippageReport::trades, BacktestSlippageReport::netPnl, BacktestSlippageReport::netRoiPercent)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(new BigDecimal("0"), 1, new BigDecimal("13.5000"), new BigDecimal("270.00")),
                org.assertj.core.groups.Tuple.tuple(new BigDecimal("0.01"), 1, new BigDecimal("13.3650"), new BigDecimal("267.30")),
                org.assertj.core.groups.Tuple.tuple(new BigDecimal("0.02"), 1, new BigDecimal("13.2300"), new BigDecimal("264.60")),
                org.assertj.core.groups.Tuple.tuple(new BigDecimal("0.03"), 1, new BigDecimal("13.0950"), new BigDecimal("261.90"))
            );
    }

    @Test
    void historicalOpeningClosingMarksClvUnavailableAndUsesExecutionValidationGates() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "sp1-draw", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-draw", 2L, "Draw", "3.70", BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO, new BigDecimal("0.02"));

        assertThat(report.slippageModel()).isEqualTo(BacktestSlippageModel.PROFIT_HAIRCUT);
        assertThat(report.clvSummary().status()).isEqualTo(BacktestClvStatus.NOT_AVAILABLE);
        assertThat(report.paperTrades())
            .singleElement()
            .satisfies(trade -> {
                assertThat(trade.eventId()).isEqualTo("sp1-draw");
                assertThat(trade.marketId()).isEqualTo("sp1-draw");
                assertThat(trade.league()).isEqualTo("SP1");
                assertThat(trade.runner()).isEqualTo("Draw");
                assertThat(trade.recommendationTimestamp()).isEqualTo(Instant.parse("2026-06-01T16:00:00Z"));
                assertThat(trade.executionTimestamp()).isEqualTo(Instant.parse("2026-06-01T16:00:00Z"));
                assertThat(trade.closingTimestamp()).isEqualTo(Instant.parse("2026-06-01T16:00:00Z"));
                assertThat(trade.availableBackOdds()).isEqualByComparingTo("3.70");
                assertThat(trade.requestedOdds()).isEqualByComparingTo("3.70");
                assertThat(trade.executionOdds()).isEqualByComparingTo("3.646");
                assertThat(trade.closingOdds()).isEqualByComparingTo("3.70");
                assertThat(trade.result()).isEqualTo(BacktestOutcome.WIN);
                assertThat(trade.grossPnl()).isEqualByComparingTo("13.230");
                assertThat(trade.netPnl()).isEqualByComparingTo("13.230");
                assertThat(trade.decimalClvRatio()).isNull();
                assertThat(trade.impliedProbabilityChange()).isNull();
            });
        assertThat(report.clvSummary()).satisfies(summary -> {
            assertThat(summary.trades()).isEqualTo(1);
            assertThat(summary.averageClv()).isNull();
            assertThat(summary.medianClv()).isNull();
            assertThat(summary.positiveClvPercent()).isNull();
        });
        assertThat(report.paperValidation(BigDecimal.ZERO)).satisfies(validation -> {
            assertThat(validation.clvStatus()).isEqualTo(BacktestClvStatus.NOT_AVAILABLE);
            assertThat(validation.status()).isEqualTo(BacktestPaperValidationStatus.HISTORICAL_CANDIDATE);
            assertThat(validation.theoreticalRoiPercent()).isEqualByComparingTo("270.00");
            assertThat(validation.executableRoiPercent()).isEqualByComparingTo("264.60");
            assertThat(validation.executionLossPercentagePoints()).isEqualByComparingTo("5.40");
        });
    }

    @Test
    void totalOddsMultiplierSlippageModelDegradesTheWholeDecimalOdds() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "sp1-draw", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T12:00:00Z", "SP1", "2025/26", "closing-average", "sp1-draw", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-draw", 2L, "Draw", "3.60", BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(
            CONFIG_PATH,
            INPUT_PATH,
            42L,
            BigDecimal.ZERO,
            new BigDecimal("0.02"),
            BacktestSlippageModel.TOTAL_ODDS_MULTIPLIER
        );

        assertThat(report.slippageModel()).isEqualTo(BacktestSlippageModel.TOTAL_ODDS_MULTIPLIER);
        assertThat(report.paperTrades())
            .singleElement()
            .satisfies(trade -> {
                assertThat(trade.executionOdds()).isEqualByComparingTo("3.626");
                assertThat(trade.grossPnl()).isEqualByComparingTo("13.130");
            });
    }

    @Test
    void paperValidationGateReportsCandidateEdgeExecutionFailureAndWeakEvidence() {
        RecordingHistoryReader candidateReader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "candidate", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T12:00:00Z", "SP1", "2025/26", "closing-average", "candidate", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "candidate", 2L, "Draw", "3.60", BacktestOutcome.WIN)
        ));
        RecordingHistoryReader failureReader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "failure", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T12:00:00Z", "SP1", "2025/26", "closing-average", "failure", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "failure", 2L, "Draw", "3.60", BacktestOutcome.WIN)
        ));
        RecordingHistoryReader weakReader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "weak", 2L, "Draw", "3.50", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "weak", 2L, "Draw", "3.50", BacktestOutcome.WIN)
        ));

        BacktestComparisonReport candidate = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), candidateReader)
            .runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO, BigDecimal.ZERO);
        BacktestComparisonReport failure = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), failureReader)
            .runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO, new BigDecimal("1"));
        BacktestComparisonReport weak = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), weakReader)
            .runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(candidate.paperValidation(BigDecimal.ZERO).status()).isEqualTo(BacktestPaperValidationStatus.HISTORICAL_CANDIDATE);
        assertThat(failure.paperValidation(BigDecimal.ZERO).status()).isEqualTo(BacktestPaperValidationStatus.NEGATIVE_EXECUTABLE_ROI);
        assertThat(weak.paperValidation(BigDecimal.ZERO).status()).isEqualTo(BacktestPaperValidationStatus.HISTORICAL_CANDIDATE);
    }

    @Test
    void paperValidationGateReportsInsufficientSampleBelowThreeHundredSettledTrades() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "small", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "small", 2L, "Draw", "3.60", BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestPaperValidationReport validation = service
            .runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO)
            .paperValidation(new BigDecimal("300"));

        assertThat(validation.status()).isEqualTo(BacktestPaperValidationStatus.INSUFFICIENT_SAMPLE);
    }

    @Test
    void rollingPaperWindowsReportRoiClvDrawdownAndLosingStreak() {
        List<BacktestInputRow> rows = new ArrayList<>();
        Instant firstOpening = Instant.parse("2026-01-01T06:00:00Z");
        for (int index = 0; index < 100; index++) {
            BacktestOutcome outcome = index == 99 ? BacktestOutcome.WIN : BacktestOutcome.LOSE;
            rows.add(comparisonRow(firstOpening.plusSeconds(index * 86_400L).toString(), "SP1", "2025/26", "opening-bookmaker", "roll-" + index, 2L, "Draw", "3.70", outcome));
            rows.add(comparisonRow(firstOpening.plusSeconds(index * 86_400L + 21_600L).toString(), "SP1", "2025/26", "closing-average", "roll-" + index, 2L, "Draw", "3.70", outcome));
            rows.add(comparisonRow(firstOpening.plusSeconds(index * 86_400L + 36_000L).toString(), "SP1", "2025/26", "closing-average", "roll-" + index, 2L, "Draw", "3.60", outcome));
        }
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), new RecordingHistoryReader(rows));

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO);

        assertThat(report.rollingPaperWindows())
            .filteredOn(window -> window.windowSize() == 100)
            .singleElement()
            .satisfies(window -> {
                assertThat(window.trades()).isEqualTo(100);
                assertThat(window.roiPercent()).isEqualByComparingTo("-96.30");
                assertThat(window.averageClv()).isNull();
                assertThat(window.maxDrawdown()).isEqualByComparingTo("495");
                assertThat(window.longestLosingStreak()).isEqualTo(99);
            });
    }

    @Test
    void drawOnlySeasonLeagueReportIncludesAverageOddsAndLongestLosingStreak() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "sp1-draw-1", 2L, "Draw", "3.70", BacktestOutcome.LOSE),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-draw-1", 2L, "Draw", "3.70", BacktestOutcome.LOSE),
            comparisonRow("2026-06-02T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "sp1-draw-2", 2L, "Draw", "3.90", BacktestOutcome.LOSE),
            comparisonRow("2026-06-02T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-draw-2", 2L, "Draw", "3.90", BacktestOutcome.LOSE),
            comparisonRow("2026-06-03T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "sp1-draw-3", 2L, "Draw", "3.80", BacktestOutcome.WIN),
            comparisonRow("2026-06-03T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-draw-3", 2L, "Draw", "3.80", BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO);

        assertThat(report.drawOnlySeasonLeagueReports())
            .singleElement()
            .satisfies(season -> {
                assertThat(season.league()).isEqualTo("SP1");
                assertThat(season.season()).isEqualTo("2025/26");
                assertThat(season.trades()).isEqualTo(3);
                assertThat(season.wins()).isEqualTo(1);
                assertThat(season.averageOdds()).isEqualByComparingTo("3.8000");
                assertThat(season.grossProfitLoss()).isEqualByComparingTo("4.00");
                assertThat(season.roiPercent()).isEqualByComparingTo("26.67");
                assertThat(season.maxDrawdown()).isEqualByComparingTo("10");
                assertThat(season.longestLosingStreak()).isEqualTo(2);
            });
    }

    @Test
    void movementDiagnosticsBucketOpeningToClosingMovementForDrawOnlyTrades() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-03T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "stable", 2L, "Draw", "4.00", BacktestOutcome.LOSE),
            comparisonRow("2026-06-03T16:00:00Z", "SP1", "2025/26", "closing-average", "stable", 2L, "Draw", "4.00", BacktestOutcome.LOSE)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO);

        assertThat(report.movementReports())
            .filteredOn(movement -> movement.strategyId().equals("value-football-draw-only"))
            .extracting(BacktestMovementReport::movementBucket, BacktestMovementReport::trades)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("stable", 1));
    }

    @Test
    void equityCurveRowsAreChronologicalAndContainCumulativeDrawdown() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "sp1-draw-1", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-draw-1", 2L, "Draw", "3.70", BacktestOutcome.WIN),
            comparisonRow("2026-06-02T06:00:00Z", "F1", "2025/26", "opening-bookmaker", "f1-draw-1", 2L, "Draw", "3.70", BacktestOutcome.LOSE),
            comparisonRow("2026-06-02T16:00:00Z", "F1", "2025/26", "closing-average", "f1-draw-1", 2L, "Draw", "3.70", BacktestOutcome.LOSE)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO);

        assertThat(report.equityCurveRows())
            .extracting(BacktestEquityCurveRow::observedAt, BacktestEquityCurveRow::league, BacktestEquityCurveRow::pnl, BacktestEquityCurveRow::cumulativePnl, BacktestEquityCurveRow::drawdown)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(Instant.parse("2026-06-01T16:00:00Z"), "SP1", new BigDecimal("13.50"), new BigDecimal("13.50"), new BigDecimal("0.00")),
                org.assertj.core.groups.Tuple.tuple(Instant.parse("2026-06-02T16:00:00Z"), "F1", new BigDecimal("-5"), new BigDecimal("8.50"), new BigDecimal("5.00"))
            );
    }

    @Test
    void singlePriceDatasetsReportAnalyzerIncompatibilityInsteadOfSilentZeroTrades() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-1", 1L, "Team A", "2.50", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-1", 2L, "Draw", "3.40", BacktestOutcome.LOSE)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO);

        assertThat(report.datasetCapability()).isEqualTo(BacktestDatasetCapability.SINGLE_PRICE);
        assertThat(report.analyzerDiagnostics())
            .filteredOn(diagnostic -> diagnostic.strategyId().equals("value-football"))
            .extracting(BacktestAnalyzerDiagnostic::reason)
            .contains("insufficient_history");
        assertThat(report.analyzerDiagnostics())
            .filteredOn(diagnostic -> diagnostic.strategyId().equals("value-football-draw-only"))
            .singleElement()
            .satisfies(diagnostic -> {
                assertThat(diagnostic.oddsSource()).isEqualTo("closing-average");
                assertThat(diagnostic.reason()).isEqualTo("insufficient_history");
                assertThat(diagnostic.count()).isEqualTo(1);
            });
        assertThat(new BacktestResultFormatter().formatComparison(report))
            .anySatisfy(line -> assertThat(line).contains("ANALYZER_DIAGNOSTIC | strategy=value-football-draw-only | oddsSource=closing-average | reason=insufficient_history | count=1"));
    }

    @Test
    void pairedOpeningClosingRecommendationsDoNotDependOnResultField() {
        List<BacktestInputRow> winningRows = List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "sp1-result", 1L, "Team A", "2.60", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-result", 1L, "Team A", "2.50", BacktestOutcome.WIN)
        );
        List<BacktestInputRow> losingRows = List.of(
            comparisonRow("2026-06-01T06:00:00Z", "SP1", "2025/26", "opening-bookmaker", "sp1-result", 1L, "Team A", "2.60", BacktestOutcome.LOSE),
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-result", 1L, "Team A", "2.50", BacktestOutcome.LOSE)
        );

        BacktestComparisonReport winningReport = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), new RecordingHistoryReader(winningRows))
            .runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO);
        BacktestComparisonReport losingReport = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), new RecordingHistoryReader(losingRows))
            .runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO);

        assertThat(tradeSelections(winningReport, "value-football")).isEqualTo(tradeSelections(losingReport, "value-football"));
    }

    @Test
    void formatterPrintsDatasetCapabilityPricingModeAndAnalyzerDiagnostics() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2026-06-01T16:00:00Z", "SP1", "2025/26", "closing-average", "sp1-1", 1L, "Team A", "2.50", BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, BigDecimal.ZERO);

        assertThat(new BacktestResultFormatter().formatComparison(report))
            .anySatisfy(line -> assertThat(line)
                .contains("Strategy comparison | randomSeed=42 | pricingMode=bookmaker | commissionRate=0 | oddsSlippageRate=0 | slippageModel=PROFIT_HAIRCUT | oddsSource=closing-average | datasetCapability=SINGLE_PRICE"))
            .anySatisfy(line -> assertThat(line)
                .contains("ANALYZER_DIAGNOSTIC | strategy=value-football | oddsSource=closing-average | reason=insufficient_history | count=1"));
    }

    @Test
    void reportsSeasonMetricsFromMarketSettlementUsingExplicitSeasonLabels() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2020-09-01T10:00:00Z", "SP1", "2020/21", "closing-average", "sp1-2020", 1L, "Team A", "2.00", BacktestOutcome.WIN),
            comparisonRow("2021-09-01T10:00:00Z", "SP1", "2021/22", "closing-average", "sp1-2021", 1L, "Team A", "2.00", BacktestOutcome.LOSE)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, new BigDecimal("0.05"));

        assertThat(report.seasonReports())
            .filteredOn(season -> season.strategyId().equals("favorite"))
            .extracting(BacktestSeasonReport::season)
            .containsExactly("2020/21", "2021/22");
        assertThat(report.seasonReports())
            .filteredOn(season -> season.strategyId().equals("favorite") && season.season().equals("2020/21"))
            .singleElement()
            .satisfies(season -> {
                assertThat(season.markets()).isEqualTo(1);
                assertThat(season.trades()).isEqualTo(1);
                assertThat(season.grossProfitLoss()).isEqualByComparingTo("5.00");
                assertThat(season.commissionPaid()).isEqualByComparingTo("0.2500");
                assertThat(season.netProfitLoss()).isEqualByComparingTo("4.7500");
                assertThat(season.grossRoiPercent()).isEqualByComparingTo("100.00");
                assertThat(season.netRoiPercent()).isEqualByComparingTo("95.00");
                assertThat(season.grossMaxDrawdown()).isEqualByComparingTo("0");
                assertThat(season.netMaxDrawdown()).isEqualByComparingTo("0");
                assertThat(season.strikeRatePercent()).isEqualByComparingTo("100.00");
            });
    }

    @Test
    void usesFixedChronologicalOutOfSampleSplits() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            comparisonRow("2020-09-01T10:00:00Z", "SP1", "2020/21", "closing-average", "sp1-2020", 1L, "Team A", "2.00", BacktestOutcome.WIN),
            comparisonRow("2021-09-01T10:00:00Z", "SP1", "2021/22", "closing-average", "sp1-2021", 1L, "Team A", "2.00", BacktestOutcome.WIN),
            comparisonRow("2022-09-01T10:00:00Z", "SP1", "2022/23", "closing-average", "sp1-2022", 1L, "Team A", "2.00", BacktestOutcome.WIN),
            comparisonRow("2023-09-01T10:00:00Z", "SP1", "2023/24", "closing-average", "sp1-2023", 1L, "Team A", "2.00", BacktestOutcome.WIN),
            comparisonRow("2024-09-01T10:00:00Z", "SP1", "2024/25", "closing-average", "sp1-2024", 1L, "Team A", "2.00", BacktestOutcome.WIN),
            comparisonRow("2025-09-01T10:00:00Z", "SP1", "2025/26", "closing-average", "sp1-2025", 1L, "Team A", "2.00", BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, new BigDecimal("0.05"));

        assertThat(report.outOfSampleReports())
            .filteredOn(period -> period.strategyId().equals("favorite"))
            .extracting(period -> period.period() + ":" + period.startSeason() + "-" + period.endSeason())
            .containsExactly("development:2020/21-2022/23", "validation:2023/24-2024/25", "test:2025/26-2025/26");
    }

    @Test
    void ignoresRowsObservedAtOrAfterMarketStartForRecommendations() {
        Instant start = Instant.parse("2026-06-01T18:00:00Z");
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            new BacktestInputRow(
                start,
                "football-data",
                "sp1-start",
                "Match Odds",
                "Team A v Team B",
                "SP1",
                start,
                1L,
                "Team A",
                new BigDecimal("2.50"),
                new BigDecimal("2.60"),
                new BigDecimal("0.04"),
                new BigDecimal("1200"),
                BacktestOutcome.WIN,
                "2025/26",
                "closing-average"
            )
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, new BigDecimal("0.05"));

        assertThat(report.leakageDiagnostics().rowsIgnoredAtOrAfterMarketStart()).isEqualTo(1);
        assertThat(report.strategyReports())
            .allSatisfy(strategy -> assertThat(strategy.result().trades()).isEmpty());
    }

    @Test
    void currentMatchResultDoesNotInfluenceRecommendation() {
        List<BacktestInputRow> winningRows = List.of(
            row("2026-06-01T10:00:00Z", "SP1", "sp1-result", 1L, "Team A", "2.60", "1200", BacktestOutcome.WIN),
            row("2026-06-01T10:01:00Z", "SP1", "sp1-result", 1L, "Team A", "2.50", "1200", BacktestOutcome.WIN)
        );
        List<BacktestInputRow> losingRows = List.of(
            row("2026-06-01T10:00:00Z", "SP1", "sp1-result", 1L, "Team A", "2.60", "1200", BacktestOutcome.LOSE),
            row("2026-06-01T10:01:00Z", "SP1", "sp1-result", 1L, "Team A", "2.50", "1200", BacktestOutcome.LOSE)
        );

        BacktestComparisonReport winningReport = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), new RecordingHistoryReader(winningRows))
            .runComparison(CONFIG_PATH, INPUT_PATH, 42L, new BigDecimal("0.05"));
        BacktestComparisonReport losingReport = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), new RecordingHistoryReader(losingRows))
            .runComparison(CONFIG_PATH, INPUT_PATH, 42L, new BigDecimal("0.05"));

        assertThat(tradeSelections(winningReport, "value-football")).isEqualTo(tradeSelections(losingReport, "value-football"));
    }

    @Test
    void duplicateRunnerRowsCannotCreateDuplicateTrades() {
        BacktestInputRow first = row("2026-06-01T10:00:00Z", "SP1", "sp1-duplicate", 1L, "Team A", "2.60", "1200", BacktestOutcome.WIN);
        BacktestInputRow signal = row("2026-06-01T10:01:00Z", "SP1", "sp1-duplicate", 1L, "Team A", "2.50", "1200", BacktestOutcome.WIN);
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(first, signal, signal));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, new BigDecimal("0.05"));

        assertThat(report.leakageDiagnostics().duplicateRunnerRowsIgnored()).isEqualTo(1);
        assertThat(report.strategyReports())
            .filteredOn(strategy -> strategy.strategyId().equals("value-football"))
            .singleElement()
            .satisfies(strategy -> assertThat(strategy.result().trades()).hasSize(1));
    }

    @Test
    void seasonReportsRunWithoutBorrowingHistoryFromPreviousSeasons() {
        RecordingHistoryReader reader = new RecordingHistoryReader(List.of(
            row("2025-06-01T10:00:00Z", "SP1", "shared-market", 1L, "Team A", "2.50", "1200", BacktestOutcome.WIN),
            row("2026-06-01T10:00:00Z", "SP1", "shared-market", 1L, "Team A", "2.40", "1300", BacktestOutcome.WIN)
        ));
        RunBacktestService service = new RunBacktestService(new StaticConfigRepository(BetxConfig.defaults()), reader);

        BacktestComparisonReport report = service.runComparison(CONFIG_PATH, INPUT_PATH, 42L, new BigDecimal("0.05"));

        assertThat(report.strategyReports())
            .filteredOn(strategy -> strategy.strategyId().equals("value-football"))
            .singleElement()
            .satisfies(strategy -> assertThat(strategy.result().trades()).hasSize(1));
        assertThat(report.seasonReports())
            .filteredOn(season -> season.strategyId().equals("value-football"))
            .extracting(BacktestSeasonReport::trades)
            .containsExactly(0, 0);
    }

    private static BacktestInputRow row(
        String observedAt,
        long selectionId,
        BigDecimal bestBackPrice,
        BigDecimal liquidity,
        BacktestOutcome outcome
    ) {
        return new BacktestInputRow(
            Instant.parse(observedAt),
            "betfair",
            "1.1",
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            selectionId,
            "Runner " + selectionId,
            bestBackPrice,
            bestBackPrice.add(new BigDecimal("0.10")),
            new BigDecimal("0.04"),
            liquidity,
            outcome
        );
    }

    private static BacktestInputRow row(
        String observedAt,
        String competition,
        String marketId,
        long selectionId,
        String runnerName,
        String bestBackPrice,
        String liquidity,
        BacktestOutcome outcome
    ) {
        BigDecimal back = new BigDecimal(bestBackPrice);
        return new BacktestInputRow(
            Instant.parse(observedAt),
            "football-data",
            marketId,
            "Match Odds",
            "Team A v Team B",
            competition,
            Instant.parse(observedAt).plusSeconds(3600),
            selectionId,
            runnerName,
            back,
            back.add(new BigDecimal("0.10")),
            new BigDecimal("0.04"),
            new BigDecimal(liquidity),
            outcome
        );
    }

    private static List<BacktestInputRow> comparisonRows() {
        return List.of(
            comparisonRow("2026-06-01T10:00:00Z", "SP1", "sp1-1", 1L, "Team A", "2.00", BacktestOutcome.WIN),
            comparisonRow("2026-06-01T10:00:00Z", "SP1", "sp1-1", 2L, "Draw", "3.20", BacktestOutcome.LOSE),
            comparisonRow("2026-06-01T10:00:00Z", "SP1", "sp1-1", 3L, "Team B", "4.00", BacktestOutcome.LOSE),
            comparisonRow("2026-06-02T10:00:00Z", "SP1", "sp1-2", 1L, "Team A", "1.80", BacktestOutcome.LOSE),
            comparisonRow("2026-06-02T10:00:00Z", "SP1", "sp1-2", 2L, "Draw", "3.40", BacktestOutcome.WIN),
            comparisonRow("2026-06-02T10:00:00Z", "SP1", "sp1-2", 3L, "Team B", "4.50", BacktestOutcome.LOSE),
            comparisonRow("2026-06-03T10:00:00Z", "E0", "e0-1", 1L, "Team A", "2.20", BacktestOutcome.WIN),
            comparisonRow("2026-06-03T10:00:00Z", "E0", "e0-1", 2L, "Draw", "3.00", BacktestOutcome.LOSE),
            comparisonRow("2026-06-03T10:00:00Z", "E0", "e0-1", 3L, "Team B", "3.80", BacktestOutcome.LOSE)
        );
    }

    private static BacktestInputRow comparisonRow(
        String observedAt,
        String competition,
        String marketId,
        long selectionId,
        String runnerName,
        String bestBackPrice,
        BacktestOutcome outcome
    ) {
        BigDecimal back = new BigDecimal(bestBackPrice);
        return new BacktestInputRow(
            Instant.parse(observedAt),
            "football-data",
            marketId,
            "Match Odds",
            "Team A v Team B",
            competition,
            Instant.parse(observedAt).plusSeconds(3600),
            selectionId,
            runnerName,
            back,
            back.add(new BigDecimal("0.10")),
            new BigDecimal("0.04"),
            new BigDecimal("1200"),
            outcome
        );
    }

    private static BacktestInputRow comparisonRow(
        String observedAt,
        String competition,
        String season,
        String oddsSource,
        String marketId,
        long selectionId,
        String runnerName,
        String bestBackPrice,
        BacktestOutcome outcome
    ) {
        BigDecimal back = new BigDecimal(bestBackPrice);
        return new BacktestInputRow(
            Instant.parse(observedAt),
            "football-data",
            marketId,
            "Match Odds",
            "Team A v Team B",
            competition,
            Instant.parse(observedAt).plusSeconds(3600),
            selectionId,
            runnerName,
            back,
            back.add(new BigDecimal("0.10")),
            new BigDecimal("0.04"),
            new BigDecimal("1200"),
            outcome,
            season,
            oddsSource
        );
    }

    private static List<String> tradeSelections(BacktestComparisonReport report, String strategyId) {
        return report.strategyReports().stream()
            .filter(strategy -> strategy.strategyId().equals(strategyId))
            .findFirst()
            .orElseThrow()
            .result()
            .trades()
            .stream()
            .map(trade -> trade.marketId() + "|" + trade.selectionId() + "|" + trade.observedAt())
            .toList();
    }

    private record RecordingHistoryReader(List<BacktestInputRow> rows, List<Path> paths) implements BacktestHistoryReader {
        RecordingHistoryReader(List<BacktestInputRow> rows) {
            this(rows, new ArrayList<>());
        }

        @Override
        public List<BacktestInputRow> read(Path inputPath) {
            paths.add(inputPath);
            return rows;
        }
    }

    private record StaticConfigRepository(BetxConfig config) implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return config;
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }
}
