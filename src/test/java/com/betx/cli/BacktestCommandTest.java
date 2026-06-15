package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betx.application.BacktestResult;
import com.betx.application.BacktestComparisonReport;
import com.betx.application.BacktestSlippageModel;
import com.betx.application.BacktestStrategyLeagueReport;
import com.betx.application.BacktestStrategyReport;
import com.betx.application.BacktestRobustnessReport;
import com.betx.application.BacktestTrade;
import com.betx.application.RunBacktestService;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.signal.BetSide;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BacktestCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void printsStrategyComparisonByDefault() {
        RecordingRunBacktestService service = new RecordingRunBacktestService(result());
        BacktestCommand command = new BacktestCommand(service);
        command.configPath = Path.of("betx.yml");
        command.inputPath = Path.of("history.csv");

        String output = captureOutput(command::run);

        assertThat(service.comparisonConfigPaths()).containsExactly(new ConfigPath(Path.of("betx.yml")));
        assertThat(service.comparisonInputPaths()).containsExactly(Path.of("history.csv"));
        assertThat(service.randomSeeds()).containsExactly(42L);
        assertThat(service.commissionRates()).containsExactly((BigDecimal) null);
        assertThat(output)
            .contains("Strategy comparison | randomSeed=42 | pricingMode=exchange | commissionRate=0 | oddsSlippageRate=0 | slippageModel=PROFIT_HAIRCUT")
            .contains("STRATEGY | strategy=favorite | trades=2 | wins=1 | losses=1 | roi=25.00% | grossPnl=2.50 | commission=0.00 | netPnl=2.50 | netRoi=25.00% | maxDrawdown=5.00 | strikeRate=50.00%")
            .contains("League comparison")
            .contains("LEAGUE_STRATEGY | strategy=favorite | league=La Liga | trades=2 | wins=1 | losses=1 | roi=25.00% | grossPnl=2.50 | commission=0.00 | netPnl=2.50 | netRoi=25.00% | maxDrawdown=5.00 | strikeRate=50.00%");
    }

    @Test
    void passesRandomSeedOverrideAndExportsComparisonCsv() throws Exception {
        RecordingRunBacktestService service = new RecordingRunBacktestService(result());
        BacktestCommand command = new BacktestCommand(service);
        command.configPath = Path.of("betx.yml");
        command.inputPath = Path.of("history.csv");
        command.randomSeed = 7L;
        command.commissionRate = new BigDecimal("0.02");
        command.exportCsvPath = tempDir.resolve("comparison.csv");

        String output = captureOutput(command::run);

        assertThat(service.randomSeeds()).containsExactly(7L);
        assertThat(service.commissionRates()).containsExactly(new BigDecimal("0.02"));
        assertThat(service.oddsSlippageRates()).containsExactly(BigDecimal.ZERO);
        assertThat(output).contains("CSV exported | path=" + command.exportCsvPath);
        assertThat(Files.readAllLines(command.exportCsvPath)).contains(
            "strategy,1,favorite,,,,unknown,exchange,EXCHANGE_SNAPSHOTS,0,2,1,1,25.00,25.00,2.50,0.00,2.50,5.00,5.00,50.00,7,0.02,0,PROFIT_HAIRCUT"
        );
    }

    @Test
    void rejectsInvalidCommissionRate() {
        RecordingRunBacktestService service = new RecordingRunBacktestService(result());
        BacktestCommand command = new BacktestCommand(service);
        command.configPath = Path.of("betx.yml");
        command.inputPath = Path.of("history.csv");
        command.commissionRate = new BigDecimal("1.01");

        assertThatThrownBy(command::run)
            .isInstanceOf(picocli.CommandLine.ParameterException.class)
            .hasMessageContaining("--commission-rate must be between 0.0 and 1.0");
    }

    @Test
    void passesOddsSlippageOverrideAndExportsEquityCurveCsv() throws Exception {
        RecordingRunBacktestService service = new RecordingRunBacktestService(result());
        BacktestCommand command = new BacktestCommand(service);
        command.configPath = Path.of("betx.yml");
        command.inputPath = Path.of("history.csv");
        command.oddsSlippageRate = new BigDecimal("0.02");
        command.slippageModel = "TOTAL_ODDS_MULTIPLIER";
        command.exportEquityCsvPath = tempDir.resolve("equity.csv");
        command.exportPaperCsvPath = tempDir.resolve("paper.csv");

        String output = captureOutput(command::run);

        assertThat(service.oddsSlippageRates()).containsExactly(new BigDecimal("0.02"));
        assertThat(service.slippageModels()).containsExactly(BacktestSlippageModel.TOTAL_ODDS_MULTIPLIER);
        assertThat(output).contains("Equity curve CSV exported | path=" + command.exportEquityCsvPath);
        assertThat(output).contains("Paper trades CSV exported | path=" + command.exportPaperCsvPath);
        assertThat(Files.readAllLines(command.exportEquityCsvPath).getFirst())
            .isEqualTo("observedAt,league,season,event,odds,result,pnl,cumulativePnl,drawdown");
        assertThat(Files.readAllLines(command.exportPaperCsvPath).getFirst())
            .isEqualTo("event_id,market_id,league,season,event,runner,recommendation_timestamp,execution_timestamp,closing_timestamp,available_back_odds,requested_odds,execution_odds,closing_odds,result,gross_pnl,commission,net_pnl,decimal_clv_ratio,implied_probability_change,movement_bucket,slippage_model");
    }

    @Test
    void rejectsInvalidOddsSlippageRate() {
        RecordingRunBacktestService service = new RecordingRunBacktestService(result());
        BacktestCommand command = new BacktestCommand(service);
        command.configPath = Path.of("betx.yml");
        command.inputPath = Path.of("history.csv");
        command.oddsSlippageRate = new BigDecimal("1.01");

        assertThatThrownBy(command::run)
            .isInstanceOf(picocli.CommandLine.ParameterException.class)
            .hasMessageContaining("--odds-slippage-rate must be between 0.0 and 1.0");
    }

    @Test
    void rejectsInvalidSlippageModel() {
        RecordingRunBacktestService service = new RecordingRunBacktestService(result());
        BacktestCommand command = new BacktestCommand(service);
        command.configPath = Path.of("betx.yml");
        command.inputPath = Path.of("history.csv");
        command.slippageModel = "unknown";

        assertThatThrownBy(command::run)
            .isInstanceOf(picocli.CommandLine.ParameterException.class)
            .hasMessageContaining("--slippage-model must be PROFIT_HAIRCUT or TOTAL_ODDS_MULTIPLIER");
    }

    @Test
    void printsRobustnessReportWhenRequested() {
        RecordingRunBacktestService service = new RecordingRunBacktestService(result());
        BacktestCommand command = new BacktestCommand(service);
        command.configPath = Path.of("betx.yml");
        command.inputPath = Path.of("history.csv");
        command.robustness = true;

        String output = captureOutput(command::run);

        assertThat(service.robustnessInputPaths()).containsExactly(Path.of("history.csv"));
        assertThat(output)
            .contains("Robustness validation")
            .contains("LEAGUE | SP1 | trades=2 | roi=25.00% | maxDrawdown=5.00 | flag=LOW SAMPLE SIZE")
            .contains("LEAGUE | E0 | status=NO DATA")
            .contains("WALK_FORWARD | SP1 | status=insufficient_seasons")
            .contains("SENSITIVITY | SP1 | threshold=-1% | trades=2 | roi=25.00% | flag=LOW SAMPLE SIZE");
    }

    private static BacktestResult result() {
        List<BacktestTrade> trades = List.of(
            new BacktestTrade(
                Instant.parse("2026-06-01T10:01:00Z"),
                "betfair",
                "1.1",
                "Team A v Team B",
                "Match Odds",
                42L,
                "Team A",
                BetSide.BACK,
                new BigDecimal("2.50"),
                new BigDecimal("5"),
                com.betx.application.BacktestOutcome.WIN,
                new BigDecimal("7.50")
            ),
            new BacktestTrade(
                Instant.parse("2026-06-01T10:03:00Z"),
                "betfair",
                "1.2",
                "Team C v Team D",
                "Match Odds",
                43L,
                "Team C",
                BetSide.BACK,
                new BigDecimal("3.00"),
                new BigDecimal("5"),
                com.betx.application.BacktestOutcome.LOSE,
                new BigDecimal("-5")
            )
        );
        return BacktestResult.from(4, 4, trades);
    }

    private static String captureOutput(Runnable runnable) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runnable.run();
            return output.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(originalOut);
        }
    }

    private static final class RecordingRunBacktestService extends RunBacktestService {
        private final BacktestResult result;
        private final List<ConfigPath> configPaths = new java.util.ArrayList<>();
        private final List<Path> inputPaths = new java.util.ArrayList<>();
        private final List<ConfigPath> comparisonConfigPaths = new java.util.ArrayList<>();
        private final List<Path> comparisonInputPaths = new java.util.ArrayList<>();
        private final List<Long> randomSeeds = new java.util.ArrayList<>();
        private final List<BigDecimal> commissionRates = new java.util.ArrayList<>();
        private final List<BigDecimal> oddsSlippageRates = new java.util.ArrayList<>();
        private final List<BacktestSlippageModel> slippageModels = new java.util.ArrayList<>();
        private final List<Path> robustnessInputPaths = new java.util.ArrayList<>();

        private RecordingRunBacktestService(BacktestResult result) {
            super(new StaticConfigRepository(), ignored -> List.of());
            this.result = result;
        }

        @Override
        public BacktestResult run(ConfigPath configPath, Path inputPath) {
            configPaths.add(configPath);
            inputPaths.add(inputPath);
            return result;
        }

        @Override
        public BacktestComparisonReport runComparison(ConfigPath configPath, Path inputPath, long randomSeed) {
            return runComparison(configPath, inputPath, randomSeed, null);
        }

        @Override
        public BacktestComparisonReport runComparison(
            ConfigPath configPath,
            Path inputPath,
            long randomSeed,
            BigDecimal commissionRate
        ) {
            return runComparison(configPath, inputPath, randomSeed, commissionRate, BigDecimal.ZERO);
        }

        @Override
        public BacktestComparisonReport runComparison(
            ConfigPath configPath,
            Path inputPath,
            long randomSeed,
            BigDecimal commissionRate,
            BigDecimal oddsSlippageRate
        ) {
            return runComparison(
                configPath,
                inputPath,
                randomSeed,
                commissionRate,
                oddsSlippageRate,
                BacktestSlippageModel.PROFIT_HAIRCUT
            );
        }

        @Override
        public BacktestComparisonReport runComparison(
            ConfigPath configPath,
            Path inputPath,
            long randomSeed,
            BigDecimal commissionRate,
            BigDecimal oddsSlippageRate,
            BacktestSlippageModel slippageModel
        ) {
            comparisonConfigPaths.add(configPath);
            comparisonInputPaths.add(inputPath);
            randomSeeds.add(randomSeed);
            commissionRates.add(commissionRate);
            oddsSlippageRates.add(oddsSlippageRate);
            slippageModels.add(slippageModel);
            return new BacktestComparisonReport(
                randomSeed,
                commissionRate,
                List.of(new BacktestStrategyReport("favorite", 1, result)),
                List.of(new BacktestStrategyLeagueReport("favorite", "La Liga", result))
            );
        }

        @Override
        public BacktestRobustnessReport runRobustness(
            ConfigPath configPath,
            Path inputPath,
            List<String> competitions,
            List<BigDecimal> thresholds
        ) {
            robustnessInputPaths.add(inputPath);
            return BacktestRobustnessReport.from(
                competitions,
                List.of("SP1"),
                Map.of("SP1", result),
                thresholds
            );
        }

        private List<ConfigPath> configPaths() {
            return configPaths;
        }

        private List<Path> inputPaths() {
            return inputPaths;
        }

        private List<ConfigPath> comparisonConfigPaths() {
            return comparisonConfigPaths;
        }

        private List<Path> comparisonInputPaths() {
            return comparisonInputPaths;
        }

        private List<Long> randomSeeds() {
            return randomSeeds;
        }

        private List<BigDecimal> commissionRates() {
            return commissionRates;
        }

        private List<BigDecimal> oddsSlippageRates() {
            return oddsSlippageRates;
        }

        private List<BacktestSlippageModel> slippageModels() {
            return slippageModels;
        }

        private List<Path> robustnessInputPaths() {
            return robustnessInputPaths;
        }
    }

    private record StaticConfigRepository() implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return BetxConfig.defaults();
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
