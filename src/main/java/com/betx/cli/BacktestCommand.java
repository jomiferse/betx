package com.betx.cli;

import com.betx.application.BacktestComparisonCsvExporter;
import com.betx.application.BacktestComparisonReport;
import com.betx.application.BacktestEquityCurveCsvExporter;
import com.betx.application.BacktestPaperTradeCsvExporter;
import com.betx.application.BacktestResultFormatter;
import com.betx.application.BacktestSlippageModel;
import com.betx.application.BacktestStrategyFactory;
import com.betx.application.BacktestValidationException;
import com.betx.application.RunBacktestService;
import com.betx.domain.config.ConfigPath;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(
    name = "backtest",
    description = "Replay normalized historical market data and print simulated performance.",
    subcommands = {
        FootballDataBacktestConvertCommand.class
    }
)
public class BacktestCommand implements Runnable {
    private static final List<String> DEFAULT_ROBUSTNESS_LEAGUES = List.of("SP1", "E0", "D1", "I1", "F1");
    private static final List<BigDecimal> DEFAULT_MOVEMENT_THRESHOLDS = List.of(
        new BigDecimal("-1"),
        new BigDecimal("-2"),
        new BigDecimal("-3"),
        new BigDecimal("-5"),
        new BigDecimal("-10")
    );

    private final RunBacktestService backtestService;
    private final BacktestResultFormatter formatter;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = {"--input", "-i"}, description = "Path to normalized historical backtest CSV.")
    Path inputPath;

    @Option(names = "--robustness", description = "Print league, walk-forward, and threshold sensitivity diagnostics.")
    boolean robustness;

    @Option(names = "--random-seed", defaultValue = "42", description = "Seed for deterministic random benchmark strategy.")
    long randomSeed = BacktestStrategyFactory.DEFAULT_RANDOM_SEED;

    @Option(names = "--commission-rate", description = "Commission rate applied to positive market-level gross profit. Defaults to 0 for bookmaker data and 0.05 for exchange snapshots.")
    BigDecimal commissionRate;

    @Option(names = "--odds-slippage-rate", defaultValue = "0", description = "Execution odds degradation rate applied after recommendations are generated.")
    BigDecimal oddsSlippageRate = BigDecimal.ZERO;

    @Option(names = "--slippage-model", defaultValue = "PROFIT_HAIRCUT", description = "Slippage model: PROFIT_HAIRCUT or TOTAL_ODDS_MULTIPLIER.")
    String slippageModel = "PROFIT_HAIRCUT";

    @Option(names = "--export-csv", description = "Path to write strategy comparison CSV output.")
    Path exportCsvPath;

    @Option(names = "--export-equity-csv", description = "Path to write value-football-draw-only cumulative equity curve CSV output.")
    Path exportEquityCsvPath;

    @Option(names = "--export-paper-csv", description = "Path to write value-football-draw-only paper trades CSV output.")
    Path exportPaperCsvPath;

    @Option(
        names = "--robustness-leagues",
        split = ",",
        defaultValue = "SP1,E0,D1,I1,F1",
        description = "Comma-separated competition codes for robustness diagnostics."
    )
    List<String> robustnessLeagues;

    @Option(
        names = "--movement-thresholds",
        split = ",",
        defaultValue = "-1,-2,-3,-5,-10",
        description = "Comma-separated odds movement thresholds for robustness diagnostics."
    )
    List<BigDecimal> movementThresholds;

    @Autowired
    public BacktestCommand(RunBacktestService backtestService) {
        this(backtestService, new BacktestResultFormatter());
    }

    BacktestCommand(RunBacktestService backtestService, BacktestResultFormatter formatter) {
        this.backtestService = backtestService;
        this.formatter = formatter;
    }

    @Override
    public void run() {
        if (inputPath == null) {
            throw new picocli.CommandLine.ParameterException(
                new picocli.CommandLine(this),
                "Missing required option: '--input=<inputPath>'"
            );
        }
        ConfigPath resolvedConfigPath = new ConfigPath(configPath);
        if (commissionRate != null && (commissionRate.compareTo(BigDecimal.ZERO) < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0)) {
            throw new picocli.CommandLine.ParameterException(
                new picocli.CommandLine(this),
                "--commission-rate must be between 0.0 and 1.0"
            );
        }
        if (oddsSlippageRate == null || oddsSlippageRate.compareTo(BigDecimal.ZERO) < 0 || oddsSlippageRate.compareTo(BigDecimal.ONE) > 0) {
            throw new picocli.CommandLine.ParameterException(
                new picocli.CommandLine(this),
                "--odds-slippage-rate must be between 0.0 and 1.0"
            );
        }
        BacktestSlippageModel selectedSlippageModel = selectedSlippageModel();
        if (robustness) {
            formatter.formatRobustness(backtestService.runRobustness(
                resolvedConfigPath,
                inputPath,
                robustnessLeagues == null || robustnessLeagues.isEmpty() ? DEFAULT_ROBUSTNESS_LEAGUES : robustnessLeagues,
                movementThresholds == null || movementThresholds.isEmpty() ? DEFAULT_MOVEMENT_THRESHOLDS : movementThresholds
            )).forEach(System.out::println);
            return;
        }
        BacktestComparisonReport report = backtestService.runComparison(
            resolvedConfigPath,
            inputPath,
            randomSeed,
            commissionRate,
            oddsSlippageRate,
            selectedSlippageModel
        );
        formatter.formatComparison(report).forEach(System.out::println);
        if (exportCsvPath != null) {
            new BacktestComparisonCsvExporter().write(exportCsvPath, report);
            System.out.println("CSV exported | path=" + exportCsvPath);
        }
        if (exportEquityCsvPath != null) {
            new BacktestEquityCurveCsvExporter().write(exportEquityCsvPath, report);
            System.out.println("Equity curve CSV exported | path=" + exportEquityCsvPath);
        }
        if (exportPaperCsvPath != null) {
            new BacktestPaperTradeCsvExporter().write(exportPaperCsvPath, report);
            System.out.println("Paper trades CSV exported | path=" + exportPaperCsvPath);
        }
    }

    private BacktestSlippageModel selectedSlippageModel() {
        try {
            return BacktestSlippageModel.fromId(slippageModel);
        } catch (BacktestValidationException exc) {
            throw new picocli.CommandLine.ParameterException(new picocli.CommandLine(this), exc.getMessage(), exc);
        }
    }
}
