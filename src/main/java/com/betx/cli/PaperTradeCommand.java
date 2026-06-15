package com.betx.cli;

import com.betx.application.BacktestPaperTradeCsvExporter;
import com.betx.application.BacktestSlippageModel;
import com.betx.application.BacktestValidationException;
import com.betx.application.PaperTradeAnalyzerRejectionReason;
import com.betx.application.PaperTradeHistoryDiagnostics;
import com.betx.application.PaperTradingResult;
import com.betx.application.RunPaperTradingService;
import com.betx.domain.config.PaperConfig;
import com.betx.domain.config.ConfigPath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "paper-trade", description = "Record read-only value-football-draw-only paper recommendations.")
public class PaperTradeCommand implements Runnable {
    private final RunPaperTradingService paperTradingService;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--output", description = "Optional path to export paper-trading CSV output.")
    Path outputPath;

    @Option(names = "--odds-slippage-rate", defaultValue = "0", description = "Execution odds degradation rate applied to paper execution odds.")
    BigDecimal oddsSlippageRate = BigDecimal.ZERO;

    @Option(names = "--commission-rate", defaultValue = "0.05", description = "Commission rate deducted from positive paper market profit.")
    BigDecimal commissionRate = new BigDecimal("0.05");

    @Option(names = "--slippage-model", defaultValue = "PROFIT_HAIRCUT", description = "Slippage model: PROFIT_HAIRCUT or TOTAL_ODDS_MULTIPLIER.")
    String slippageModel = "PROFIT_HAIRCUT";

    @Option(names = "--continuous", description = "Run paper trading continuously until Ctrl+C.")
    boolean continuous;

    @Option(names = "--poll-interval", description = "Continuous paper-trading poll interval, for example 60s or 5m.")
    String pollInterval;

    @Autowired
    public PaperTradeCommand(RunPaperTradingService paperTradingService) {
        this.paperTradingService = paperTradingService;
    }

    @Override
    public void run() {
        if (oddsSlippageRate == null || oddsSlippageRate.compareTo(BigDecimal.ZERO) < 0 || oddsSlippageRate.compareTo(BigDecimal.ONE) > 0) {
            throw new picocli.CommandLine.ParameterException(
                new picocli.CommandLine(this),
                "--odds-slippage-rate must be between 0.0 and 1.0"
            );
        }
        if (commissionRate == null || commissionRate.compareTo(BigDecimal.ZERO) < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new picocli.CommandLine.ParameterException(
                new picocli.CommandLine(this),
                "--commission-rate must be between 0.0 and 1.0"
            );
        }
        BacktestSlippageModel selectedModel = selectedSlippageModel();
        PaperConfig configuredPaper = configuredPaper();
        if (continuous || configuredPaper.continuous()) {
            runContinuous(selectedModel, configuredPaper);
            return;
        }
        PaperTradingResult result = paperTradingService.run(new ConfigPath(configPath), oddsSlippageRate, selectedModel, commissionRate);
        printResult("Paper trading complete", result, selectedModel, true);
    }

    private PaperConfig configuredPaper() {
        PaperConfig config = paperTradingService.paperConfig(new ConfigPath(configPath));
        return config == null ? PaperConfig.defaults() : config;
    }

    private void runContinuous(BacktestSlippageModel selectedModel, PaperConfig configuredPaper) {
        Duration selectedPollInterval = pollInterval == null || pollInterval.isBlank()
            ? configuredPaper.pollInterval()
            : PaperConfig.parseDuration(pollInterval, Duration.ofSeconds(60));
        System.out.println("PAPER MODE | strategy=value-football-draw-only | realOrders=false | telegram=false | intelligence=false");
        System.out.println("Paper trading continuous mode started | pollInterval=" + durationLabel(selectedPollInterval)
            + " | gracefulShutdown=Ctrl+C");
        paperTradingService.runContinuous(
            new ConfigPath(configPath),
            oddsSlippageRate,
            selectedModel,
            commissionRate,
            selectedPollInterval,
            null,
            (cycle, result) -> printResult("Paper trading cycle complete | cycle=" + cycle, result, selectedModel, false)
        );
    }

    private void printResult(String prefix, PaperTradingResult result, BacktestSlippageModel selectedModel, boolean printModeHeader) {
        com.betx.application.BacktestComparisonReport report = report(result, selectedModel);
        if (outputPath != null) {
            new BacktestPaperTradeCsvExporter().write(outputPath, report);
        }
        if (printModeHeader) {
            System.out.println("PAPER MODE | strategy=value-football-draw-only | realOrders=false | telegram=false | intelligence=false");
        }
        System.out.println(prefix
            + " | marketsScanned=" + result.marketsScanned()
            + " | runnersAnalyzed=" + result.runnersAnalyzed()
            + " | recommendationsGenerated=" + result.recommendationsGenerated()
            + " | duplicatesSkipped=" + result.duplicatesSkipped()
            + " | executionFailures=" + result.executionFailures()
            + " | missingClosingPrices=" + result.missingClosingPrices()
            + " | unsettledMarkets=" + result.unsettledMarkets()
            + " | settledTrades=" + result.settledTrades()
            + " | snapshotsSaved=" + result.snapshotsSaved()
            + " | failures=" + result.failures().size()
            + " | commissionRate=" + commissionRate
            + " | slippageModel=" + selectedModel
            + (outputPath == null ? "" : " | output=" + outputPath));
        printHistoryDiagnostics(result.historyDiagnostics());
        printPaperMetrics(report);
        result.failures().forEach(System.out::println);
    }

    private void printHistoryDiagnostics(PaperTradeHistoryDiagnostics diagnostics) {
        PaperTradeHistoryDiagnostics safeDiagnostics = diagnostics == null
            ? PaperTradeHistoryDiagnostics.empty()
            : diagnostics;
        System.out.println("PAPER_HISTORY | previousSnapshotsLoaded=" + safeDiagnostics.previousSnapshotsLoaded()
            + " | runnersWithoutPreviousSnapshot=" + safeDiagnostics.runnersWithoutPreviousSnapshot()
            + " | runnersWithPreviousSnapshot=" + safeDiagnostics.runnersWithPreviousSnapshot()
            + " | runnersWithSufficientHistory=" + safeDiagnostics.runnersWithSufficientHistory()
            + " | runnersWithChangedOdds=" + safeDiagnostics.runnersWithChangedOdds()
            + " | runnersWithUnchangedOdds=" + safeDiagnostics.runnersWithUnchangedOdds()
            + " | oldestPreviousSnapshot=" + instantOrUnavailable(safeDiagnostics.oldestPreviousSnapshot())
            + " | newestPreviousSnapshot=" + instantOrUnavailable(safeDiagnostics.newestPreviousSnapshot())
            + " | stableMarketKeys=" + safeDiagnostics.stableMarketKeys()
            + " | stableSelectionKeys=" + safeDiagnostics.stableSelectionKeys());
        for (PaperTradeAnalyzerRejectionReason reason : PaperTradeAnalyzerRejectionReason.values()) {
            System.out.println("PAPER_ANALYZER | reason=" + reason
                + " | count=" + safeDiagnostics.analyzerRejectionCounts().getOrDefault(reason, 0));
        }
        safeDiagnostics.runnerClassificationSample().forEach(diagnostic -> System.out.println(
            "PAPER_DRAW_CLASSIFICATION"
                + " | marketId=" + diagnostic.marketId()
                + " | marketName=" + diagnostic.marketName()
                + " | selectionId=" + diagnostic.selectionId()
                + " | runnerName=" + diagnostic.runnerName()
                + " | normalizedRunnerName=" + diagnostic.normalizedRunnerName()
                + " | inferredRunnerType=" + diagnostic.inferredRunnerType()
                + " | isDraw=" + diagnostic.draw()
        ));
        safeDiagnostics.warnings().forEach(System.out::println);
    }

    private com.betx.application.BacktestComparisonReport report(PaperTradingResult result, BacktestSlippageModel selectedModel) {
        List<com.betx.application.BacktestPaperTrade> settled = result.paperTrades().stream()
            .filter(trade -> trade.result() != null)
            .toList();
        List<com.betx.application.BacktestPaperTrade> validClvTrades = settled.stream()
            .filter(trade -> trade.decimalClvRatio() != null)
            .toList();
        List<com.betx.application.BacktestClvBreakdownReport> leagueBreakdowns = validClvTrades.stream()
            .collect(Collectors.groupingBy(com.betx.application.BacktestPaperTrade::league, java.util.TreeMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> com.betx.application.BacktestClvBreakdownReport.from("league", entry.getKey(), entry.getValue()))
            .toList();
        return new com.betx.application.BacktestComparisonReport(
            42L,
            commissionRate,
            oddsSlippageRate,
            selectedModel,
            "exchange",
            "live",
            com.betx.application.BacktestDatasetCapability.EXCHANGE_SNAPSHOTS,
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            new com.betx.application.BacktestLeakageDiagnostics(0, 0),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            result.paperTrades(),
            com.betx.application.BacktestClvSummary.from(com.betx.application.BacktestClvStatus.VALID_PROSPECTIVE, validClvTrades),
            leagueBreakdowns,
            rollingWindows(settled)
        );
    }

    private void printPaperMetrics(com.betx.application.BacktestComparisonReport report) {
        new com.betx.application.BacktestResultFormatter().formatComparison(report).stream()
            .filter(line -> line.startsWith("CLV ")
                || line.startsWith("CLV_BREAKDOWN ")
                || line.startsWith("PAPER_VALIDATION ")
                || line.startsWith("ROLLING_PAPER "))
            .forEach(System.out::println);
        leagueRoiLines(report.paperTrades()).forEach(System.out::println);
    }

    private List<String> leagueRoiLines(List<com.betx.application.BacktestPaperTrade> trades) {
        return trades.stream()
            .filter(trade -> trade.result() != null)
            .collect(Collectors.groupingBy(com.betx.application.BacktestPaperTrade::league, java.util.TreeMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> "PAPER_LEAGUE | league=" + entry.getKey()
                + " | settledTrades=" + entry.getValue().size()
                + " | executableRoi=" + twoDecimal(roi(entry.getValue())) + "%"
                + " | medianClv=" + medianClv(entry.getValue()))
            .toList();
    }

    private List<com.betx.application.BacktestRollingPaperWindow> rollingWindows(List<com.betx.application.BacktestPaperTrade> settled) {
        return List.of(100, 250, 500).stream()
            .filter(window -> settled.size() >= window)
            .map(window -> rollingWindow(settled, window))
            .toList();
    }

    private com.betx.application.BacktestRollingPaperWindow rollingWindow(
        List<com.betx.application.BacktestPaperTrade> settled,
        int window
    ) {
        List<com.betx.application.BacktestPaperTrade> trades = settled.stream()
            .sorted(Comparator.comparing(com.betx.application.BacktestPaperTrade::recommendationTimestamp))
            .skip(Math.max(0, settled.size() - window))
            .toList();
        BigDecimal averageClv = trades.stream()
            .map(com.betx.application.BacktestPaperTrade::decimalClvRatio)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long clvCount = trades.stream().map(com.betx.application.BacktestPaperTrade::decimalClvRatio).filter(Objects::nonNull).count();
        if (clvCount > 0) {
            averageClv = averageClv.divide(BigDecimal.valueOf(clvCount), 8, RoundingMode.HALF_UP);
        } else {
            averageClv = null;
        }
        return new com.betx.application.BacktestRollingPaperWindow(
            window,
            trades.size(),
            roi(trades),
            averageClv,
            maxDrawdown(trades),
            longestLosingStreak(trades)
        );
    }

    private BigDecimal roi(List<com.betx.application.BacktestPaperTrade> trades) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal pnl = trades.stream().map(com.betx.application.BacktestPaperTrade::netPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        return pnl.divide(BigDecimal.valueOf(trades.size()).multiply(BigDecimal.valueOf(5)), 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal maxDrawdown(List<com.betx.application.BacktestPaperTrade> trades) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal drawdown = BigDecimal.ZERO;
        for (com.betx.application.BacktestPaperTrade trade : trades) {
            equity = equity.add(trade.netPnl());
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            BigDecimal current = peak.subtract(equity);
            if (current.compareTo(drawdown) > 0) {
                drawdown = current;
            }
        }
        return drawdown.setScale(2, RoundingMode.HALF_UP);
    }

    private int longestLosingStreak(List<com.betx.application.BacktestPaperTrade> trades) {
        int longest = 0;
        int current = 0;
        for (com.betx.application.BacktestPaperTrade trade : trades) {
            if (trade.netPnl().compareTo(BigDecimal.ZERO) < 0) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private String medianClv(List<com.betx.application.BacktestPaperTrade> trades) {
        com.betx.application.BacktestClvSummary summary = com.betx.application.BacktestClvSummary.from(
            com.betx.application.BacktestClvStatus.VALID_PROSPECTIVE,
            trades.stream().filter(trade -> trade.decimalClvRatio() != null).toList()
        );
        return summary.medianClv() == null ? "n/a" : summary.medianClv().toPlainString();
    }

    private String twoDecimal(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String instantOrUnavailable(java.time.Instant instant) {
        return instant == null ? "n/a" : instant.toString();
    }

    private BacktestSlippageModel selectedSlippageModel() {
        try {
            return BacktestSlippageModel.fromId(slippageModel);
        } catch (BacktestValidationException exc) {
            throw new picocli.CommandLine.ParameterException(new picocli.CommandLine(this), exc.getMessage(), exc);
        }
    }

    private String durationLabel(Duration duration) {
        if (duration.toMillis() % 1000 != 0) {
            return duration.toMillis() + "ms";
        }
        if (duration.toHours() > 0 && duration.toMinutes() % 60 == 0) {
            return duration.toHours() + "h";
        }
        if (duration.toMinutes() > 0 && duration.toSeconds() % 60 == 0 && duration.toSeconds() > 60) {
            return duration.toMinutes() + "m";
        }
        if (duration.toSeconds() > 0) {
            return duration.toSeconds() + "s";
        }
        return "0s";
    }
}
