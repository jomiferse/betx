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
