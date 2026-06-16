package com.betx.application;

import com.betx.application.port.out.BacktestHistoryReader;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.StrategyConfig;
import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.EventMarketAnalyzer;
import com.betx.domain.signal.ObservedMarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.domain.signal.ValueFootballSignalStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Replays normalized historical rows through the current signal analyzer. */
@Service
public class RunBacktestService {
    private static final int RECENT_SNAPSHOT_LIMIT = 10;
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.05");
    private static final List<BigDecimal> DEFAULT_SLIPPAGE_SCENARIOS = List.of(
        BigDecimal.ZERO,
        new BigDecimal("0.01"),
        new BigDecimal("0.02"),
        new BigDecimal("0.03")
    );

    private final BetxConfigRepository configRepository;
    private final BacktestHistoryReader historyReader;
    private final EventMarketAnalyzer analyzer;

    @Autowired
    public RunBacktestService(BetxConfigRepository configRepository, BacktestHistoryReader historyReader) {
        this.configRepository = configRepository;
        this.historyReader = historyReader;
        this.analyzer = new EventMarketAnalyzer();
    }

    public BacktestResult run(ConfigPath configPath, Path inputPath) {
        BetxConfig config = configRepository.load(configPath);
        List<BacktestInputRow> rows = prepareRows(historyReader.read(inputPath)).rows();
        return runRows(config, rows, analyzer);
    }

    public BacktestComparisonReport runComparison(ConfigPath configPath, Path inputPath, long randomSeed) {
        return runComparison(configPath, inputPath, randomSeed, null, BigDecimal.ZERO);
    }

    public BacktestComparisonReport runComparison(
        ConfigPath configPath,
        Path inputPath,
        long randomSeed,
        BigDecimal commissionRate
    ) {
        return runComparison(configPath, inputPath, randomSeed, commissionRate, BigDecimal.ZERO);
    }

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

    public BacktestComparisonReport runComparison(
        ConfigPath configPath,
        Path inputPath,
        long randomSeed,
        BigDecimal commissionRate,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel
    ) {
        BetxConfig config = configRepository.load(configPath);
        PreparedRows preparedRows = prepareRows(historyReader.read(inputPath));
        List<BacktestInputRow> rows = preparedRows.rows();
        DatasetProfile datasetProfile = datasetProfile(rows);
        BigDecimal effectiveCommissionRate = commissionRate == null ? datasetProfile.defaultCommissionRate() : commissionRate;
        BigDecimal effectiveSlippageRate = oddsSlippageRate == null ? BigDecimal.ZERO : oddsSlippageRate;
        BacktestSlippageModel effectiveSlippageModel = slippageModel == null ? BacktestSlippageModel.PROFIT_HAIRCUT : slippageModel;
        List<BacktestStrategy> strategies = BacktestStrategyFactory.all(randomSeed);
        List<StrategyResult> results = strategies.stream()
            .map(strategy -> strategyResult(config, rows, strategy, effectiveCommissionRate, effectiveSlippageRate, effectiveSlippageModel, datasetProfile.capability()))
            .toList();
        List<BacktestSeasonReport> seasonReports = seasonReports(config, rows, strategies, effectiveCommissionRate, effectiveSlippageRate, effectiveSlippageModel);
        List<BacktestOutOfSampleReport> outOfSampleReports = outOfSampleReports(config, rows, strategies, effectiveCommissionRate, effectiveSlippageRate, effectiveSlippageModel);
        List<BacktestStrategyReport> strategyReports = rankedReports(results);
        List<BacktestStrategyLeagueReport> leagueReports = leagueReports(strategyReports);
        List<BacktestPaperTrade> paperTrades = paperTrades(results, rows, effectiveCommissionRate, effectiveSlippageRate, effectiveSlippageModel);
        return new BacktestComparisonReport(
            randomSeed,
            effectiveCommissionRate,
            effectiveSlippageRate,
            effectiveSlippageModel,
            datasetProfile.pricingMode(),
            datasetProfile.oddsSource(),
            datasetProfile.capability(),
            strategyReports,
            leagueReports,
            marketSelectionReports(strategyReports),
            breakdownReports(strategyReports),
            seasonReports,
            seasonSummaries(seasonReports),
            outOfSampleReports,
            preparedRows.diagnostics(),
            analyzerDiagnostics(results),
            uncertaintyReports(strategyReports, randomSeed),
            slippageReports(results, effectiveCommissionRate),
            drawOnlySeasonLeagueReports(strategyReports),
            movementReports(strategyReports),
            equityCurveRows(strategyReports),
            paperTrades,
            BacktestClvSummary.unavailable(paperTrades),
            clvBreakdowns(paperTrades),
            rollingPaperWindows(paperTrades)
        );
    }

    private StrategyResult strategyResult(
        BetxConfig config,
        List<BacktestInputRow> rows,
        BacktestStrategy strategy,
        BigDecimal commissionRate,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel,
        BacktestDatasetCapability datasetCapability
    ) {
        RunRowsResult rawResult = runRowsDetailed(config, rows, strategy, datasetCapability);
        BacktestResult adjustedResult = applySlippage(rawResult.result(), oddsSlippageRate, slippageModel);
        return new StrategyResult(
            strategy.id(),
            rawResult.result(),
            adjustedResult,
            marketResults(strategy.id(), tradableRows(rows, datasetCapability), adjustedResult.trades(), commissionRate),
            rawResult.diagnostics()
        );
    }

    public BacktestRobustnessReport runRobustness(
        ConfigPath configPath,
        Path inputPath,
        List<String> competitions,
        List<BigDecimal> thresholds
    ) {
        BetxConfig config = configRepository.load(configPath);
        List<BacktestInputRow> rows = prepareRows(historyReader.read(inputPath)).rows();
        List<String> requestedCompetitions = competitions == null ? List.of() : List.copyOf(competitions);
        List<BigDecimal> thresholdCandidates = thresholds == null ? List.of() : List.copyOf(thresholds);
        Set<String> competitionsWithData = new HashSet<>();
        List<BacktestLeagueReport> leagueReports = new ArrayList<>();
        List<BacktestWalkForwardValidation> walkForwardValidations = new ArrayList<>();
        List<BacktestSensitivityReport> sensitivityReports = new ArrayList<>();

        for (String competition : requestedCompetitions) {
            List<BacktestInputRow> leagueRows = rows.stream()
                .filter(row -> competition.equals(row.competitionName()))
                .toList();
            boolean hasData = !leagueRows.isEmpty();
            if (hasData) {
                competitionsWithData.add(competition);
            }
            BacktestResult leagueResult = hasData
                ? runRows(config, leagueRows, analyzer)
                : BacktestResult.from(0, 0, List.of());
            leagueReports.add(new BacktestLeagueReport(competition, leagueResult, hasData));
            walkForwardValidations.addAll(walkForward(config, competition, leagueRows, thresholdCandidates));
            for (BigDecimal threshold : thresholdCandidates) {
                if (hasData) {
                    sensitivityReports.add(new BacktestSensitivityReport(
                        competition,
                        threshold,
                        runRows(config, leagueRows, new EventMarketAnalyzer(threshold))
                    ));
                }
            }
        }
        return new BacktestRobustnessReport(
            requestedCompetitions,
            leagueReports,
            walkForwardValidations,
            sensitivityReports
        );
    }

    private BacktestResult runRows(BetxConfig config, List<BacktestInputRow> rows, EventMarketAnalyzer marketAnalyzer) {
        return runRows(config, rows, BacktestStrategyFactory.valueFootball(marketAnalyzer));
    }

    private BacktestResult runRows(BetxConfig config, List<BacktestInputRow> rows, BacktestStrategy strategy) {
        return runRowsDetailed(config, rows, strategy, datasetProfile(rows).capability()).result();
    }

    private RunRowsResult runRowsDetailed(
        BetxConfig config,
        List<BacktestInputRow> rows,
        BacktestStrategy strategy,
        BacktestDatasetCapability datasetCapability
    ) {
        if (!strategy.enabled(config)) {
            return new RunRowsResult(BacktestResult.from(rows.size(), 0, List.of()), List.of());
        }

        List<BacktestInputRow> orderedRows = rows.stream()
            .sorted(Comparator.comparing(BacktestInputRow::observedAt))
            .toList();
        Map<String, List<BacktestInputRow>> rowsByObservation = orderedRows.stream()
            .collect(Collectors.groupingBy(this::marketObservationKey));
        Map<String, ArrayDeque<ObservedMarketSnapshot>> historyByRunner = new HashMap<>();
        Set<String> tradedKeys = new HashSet<>();
        List<BacktestTrade> trades = new ArrayList<>();
        List<BacktestAnalyzerDiagnostic> diagnostics = new ArrayList<>();
        int runnersAnalyzed = 0;

        for (BacktestInputRow row : orderedRows) {
            String key = runnerKey(row);
            ArrayDeque<ObservedMarketSnapshot> recent = historyByRunner.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            if (isTradableRow(row, datasetCapability)) {
                runnersAnalyzed++;
                List<BacktestInputRow> marketObservationRows = rowsByObservation.getOrDefault(marketObservationKey(row), List.of()).stream()
                    .sorted(Comparator.comparingLong(BacktestInputRow::selectionId))
                    .toList();
                BacktestStrategyEvaluation evaluation = strategy.evaluateWithDiagnostics(
                    row,
                    marketObservationRows,
                    List.copyOf(recent),
                    config,
                    datasetCapability
                );
                if (evaluation.decision().isPresent() && tradedKeys.add(strategy.tradeKey(row))) {
                    trades.add(toTrade(row, evaluation.decision().get(), config.risk().maxStake()));
                }
                if (evaluation.rejectionReason() != null) {
                    diagnostics.add(new BacktestAnalyzerDiagnostic(strategy.id(), row.oddsSource(), evaluation.rejectionReason(), 1));
                }
            }
            recent.addFirst(new ObservedMarketSnapshot(row.observedAt(), row.toMarketSnapshot()));
            while (recent.size() > RECENT_SNAPSHOT_LIMIT) {
                recent.removeLast();
            }
        }

        return new RunRowsResult(BacktestResult.from(rows.size(), runnersAnalyzed, trades), diagnostics);
    }

    private List<BacktestStrategyReport> rankedReports(List<StrategyResult> results) {
        List<StrategyResult> ranked = results.stream()
            .sorted(Comparator
                .comparing((StrategyResult result) -> netRoiPercent(result.marketResults())).reversed()
                .thenComparing(result -> result.result().trades().size(), Comparator.reverseOrder())
                .thenComparing(result -> result.result().strikeRatePercent(), Comparator.reverseOrder())
                .thenComparing(result -> result.result().maxDrawdown())
                .thenComparing(StrategyResult::strategyId))
            .toList();
        List<BacktestStrategyReport> reports = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            StrategyResult result = ranked.get(index);
            reports.add(new BacktestStrategyReport(result.strategyId(), index + 1, result.result(), result.marketResults()));
        }
        return reports;
    }

    private List<BacktestStrategyLeagueReport> leagueReports(List<BacktestStrategyReport> strategyReports) {
        List<BacktestStrategyLeagueReport> reports = new ArrayList<>();
        for (BacktestStrategyReport strategyReport : strategyReports) {
            Map<String, List<BacktestTrade>> tradesByLeague = strategyReport.result().trades().stream()
                .collect(Collectors.groupingBy(BacktestTrade::competitionName, TreeMap::new, Collectors.toList()));
            Map<String, List<BacktestMarketResult>> marketsByLeague = strategyReport.marketResults().stream()
                .collect(Collectors.groupingBy(BacktestMarketResult::competitionName, TreeMap::new, Collectors.toList()));
            for (Map.Entry<String, List<BacktestTrade>> entry : tradesByLeague.entrySet()) {
                reports.add(new BacktestStrategyLeagueReport(
                    strategyReport.strategyId(),
                    entry.getKey(),
                    BacktestResult.from(strategyReport.result().rowsRead(), strategyReport.result().runnersAnalyzed(), entry.getValue()),
                    marketsByLeague.getOrDefault(entry.getKey(), List.of())
                ));
            }
        }
        return reports;
    }

    private List<BacktestMarketSelectionReport> marketSelectionReports(List<BacktestStrategyReport> strategyReports) {
        List<BacktestMarketSelectionReport> reports = new ArrayList<>();
        for (BacktestStrategyReport strategyReport : strategyReports) {
            Map<Integer, List<BacktestMarketResult>> bySelectedRunnerCount = strategyReport.marketResults().stream()
                .collect(Collectors.groupingBy(BacktestMarketResult::selectedRunners, TreeMap::new, Collectors.toList()));
            for (int selectedRunners = 0; selectedRunners <= 3; selectedRunners++) {
                List<BacktestMarketResult> markets = bySelectedRunnerCount.getOrDefault(selectedRunners, List.of());
                reports.add(new BacktestMarketSelectionReport(
                    strategyReport.strategyId(),
                    selectedRunners,
                    markets.size(),
                    sum(markets, BacktestMarketResult::totalStake),
                    sum(markets, BacktestMarketResult::grossPnl),
                    sum(markets, BacktestMarketResult::commissionPaid),
                    sum(markets, BacktestMarketResult::netPnl),
                    percent(sum(markets, BacktestMarketResult::netPnl), sum(markets, BacktestMarketResult::totalStake)),
                    markets.stream().map(BacktestMarketResult::maximumExposure).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO)
                ));
            }
        }
        return reports;
    }

    private List<BacktestBreakdownReport> breakdownReports(List<BacktestStrategyReport> strategyReports) {
        List<BacktestBreakdownReport> reports = new ArrayList<>();
        for (BacktestStrategyReport strategyReport : strategyReports) {
            List<BacktestTrade> trades = strategyReport.result().trades();
            reports.addAll(groupBreakdown("league_runner", strategyReport.strategyId(), trades,
                trade -> trade.competitionName() + "|" + trade.runnerType().name()));
            reports.addAll(groupBreakdown("odds_band", strategyReport.strategyId(), trades, this::oddsBand));
            reports.addAll(groupBreakdown("league_runner_odds", strategyReport.strategyId(), trades,
                trade -> trade.competitionName() + "|" + trade.runnerType().name() + "|" + oddsBand(trade)));
        }
        return reports;
    }

    private List<BacktestBreakdownReport> groupBreakdown(
        String kind,
        String strategyId,
        List<BacktestTrade> trades,
        java.util.function.Function<BacktestTrade, String> classifier
    ) {
        return trades.stream()
            .collect(Collectors.groupingBy(classifier, TreeMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> {
                String[] parts = entry.getKey().split("\\|");
                if (kind.equals("odds_band")) {
                    return new BacktestBreakdownReport(
                        kind,
                        strategyId,
                        "all",
                        BacktestRunnerType.UNKNOWN,
                        entry.getKey(),
                        BacktestResult.from(0, 0, entry.getValue())
                    );
                }
                return new BacktestBreakdownReport(
                    kind,
                    strategyId,
                    parts.length > 0 ? parts[0] : "all",
                    parts.length > 1 ? BacktestRunnerType.valueOf(parts[1]) : BacktestRunnerType.UNKNOWN,
                    kind.equals("odds_band") ? entry.getKey() : parts.length > 2 ? parts[2] : "all",
                    BacktestResult.from(0, 0, entry.getValue())
                );
            })
            .toList();
    }

    private List<BacktestSeasonReport> seasonReports(
        BetxConfig config,
        List<BacktestInputRow> rows,
        List<BacktestStrategy> strategies,
        BigDecimal commissionRate,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel
    ) {
        Map<String, List<BacktestInputRow>> rowsBySeason = rows.stream()
            .collect(Collectors.groupingBy(BacktestInputRow::season, TreeMap::new, Collectors.toList()));
        List<BacktestSeasonReport> reports = new ArrayList<>();
        for (BacktestStrategy strategy : strategies) {
            for (Map.Entry<String, List<BacktestInputRow>> entry : rowsBySeason.entrySet()) {
                BacktestResult result = applySlippage(runRows(config, entry.getValue(), strategy), oddsSlippageRate, slippageModel);
                reports.add(new BacktestSeasonReport(
                    strategy.id(),
                    entry.getKey(),
                    result,
                    marketResults(strategy.id(), tradableRows(entry.getValue(), datasetProfile(entry.getValue()).capability()), result.trades(), commissionRate)
                ));
            }
        }
        return reports;
    }

    private List<BacktestSeasonSummary> seasonSummaries(List<BacktestSeasonReport> seasonReports) {
        return seasonReports.stream()
            .collect(Collectors.groupingBy(BacktestSeasonReport::strategyId, TreeMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> seasonSummary(entry.getKey(), entry.getValue()))
            .toList();
    }

    private BacktestSeasonSummary seasonSummary(String strategyId, List<BacktestSeasonReport> reports) {
        List<BigDecimal> rois = reports.stream()
            .map(BacktestSeasonReport::netRoiPercent)
            .sorted()
            .toList();
        if (rois.isEmpty()) {
            return new BacktestSeasonSummary(strategyId, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO);
        }
        BigDecimal total = rois.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = total.divide(BigDecimal.valueOf(rois.size()), 2, RoundingMode.HALF_UP);
        BigDecimal median = rois.get(rois.size() / 2);
        if (rois.size() % 2 == 0) {
            median = rois.get(rois.size() / 2 - 1).add(rois.get(rois.size() / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }
        int profitable = (int) reports.stream()
            .filter(report -> report.netRoiPercent().compareTo(BigDecimal.ZERO) > 0)
            .count();
        int losing = (int) reports.stream()
            .filter(report -> report.netRoiPercent().compareTo(BigDecimal.ZERO) < 0)
            .count();
        BigDecimal totalNetPnl = reports.stream()
            .map(BacktestSeasonReport::netProfitLoss)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalStake = reports.stream()
            .flatMap(report -> report.marketResults().stream())
            .map(BacktestMarketResult::totalStake)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BacktestSeasonSummary(
            strategyId,
            reports.size(),
            profitable,
            losing,
            mean,
            median,
            rois.getFirst(),
            rois.getLast(),
            percent(totalNetPnl, totalStake),
            mean,
            median,
            rois.getFirst()
        );
    }

    private List<BacktestOutOfSampleReport> outOfSampleReports(
        BetxConfig config,
        List<BacktestInputRow> rows,
        List<BacktestStrategy> strategies,
        BigDecimal commissionRate,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel
    ) {
        List<String> seasons = rows.stream()
            .map(BacktestInputRow::season)
            .distinct()
            .sorted()
            .toList();
        if (seasons.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> periods = periods(seasons);
        List<BacktestOutOfSampleReport> reports = new ArrayList<>();
        for (BacktestStrategy strategy : strategies) {
            for (Map.Entry<String, List<String>> period : periods.entrySet()) {
                List<BacktestInputRow> periodRows = rows.stream()
                    .filter(row -> period.getValue().contains(row.season()))
                    .toList();
                if (!periodRows.isEmpty()) {
                    BacktestResult result = applySlippage(runRows(config, periodRows, strategy), oddsSlippageRate, slippageModel);
                    reports.add(new BacktestOutOfSampleReport(
                        strategy.id(),
                        period.getKey(),
                        period.getValue().getFirst(),
                        period.getValue().getLast(),
                        result,
                        marketResults(strategy.id(), tradableRows(periodRows, datasetProfile(periodRows).capability()), result.trades(), commissionRate)
                    ));
                }
            }
        }
        return reports;
    }

    private Map<String, List<String>> periods(List<String> seasons) {
        Map<String, List<String>> periods = new LinkedHashMap<>();
        periods.put("development", seasons.stream()
            .filter(season -> List.of("2020/21", "2021/22", "2022/23").contains(season))
            .toList());
        periods.put("validation", seasons.stream()
            .filter(season -> List.of("2023/24", "2024/25").contains(season))
            .toList());
        periods.put("test", seasons.stream()
            .filter(season -> "2025/26".equals(season))
            .toList());
        periods.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return periods;
    }

    private List<BacktestMarketResult> marketResults(
        String strategyId,
        List<BacktestInputRow> rows,
        List<BacktestTrade> trades,
        BigDecimal commissionRate
    ) {
        Map<String, BacktestInputRow> representativeRows = rows.stream()
            .sorted(Comparator.comparing(BacktestInputRow::observedAt))
            .collect(Collectors.toMap(
                this::marketKey,
                row -> row,
                (first, ignored) -> first,
                TreeMap::new
            ));
        Map<String, List<BacktestTrade>> tradesByMarket = trades.stream()
            .collect(Collectors.groupingBy(this::marketKey, TreeMap::new, Collectors.toList()));
        List<BacktestMarketResult> results = new ArrayList<>();
        for (Map.Entry<String, BacktestInputRow> entry : representativeRows.entrySet()) {
            List<BacktestTrade> marketTrades = tradesByMarket.getOrDefault(entry.getKey(), List.of());
            results.add(marketTrades.isEmpty()
                ? BacktestMarketResult.empty(strategyId, entry.getValue())
                : BacktestMarketResult.from(strategyId, marketTrades, commissionRate));
        }
        return results;
    }

    private BigDecimal netRoiPercent(List<BacktestMarketResult> marketResults) {
        BigDecimal stake = marketResults.stream()
            .map(BacktestMarketResult::totalStake)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (stake.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal netPnl = marketResults.stream()
            .map(BacktestMarketResult::netPnl)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return netPnl.divide(stake, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(
        List<BacktestMarketResult> markets,
        java.util.function.Function<BacktestMarketResult, BigDecimal> mapper
    ) {
        return markets.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return numerator.divide(denominator, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String oddsBand(BacktestTrade trade) {
        BigDecimal odds = trade.odds();
        if (odds == null) {
            return "unknown";
        }
        if (odds.compareTo(new BigDecimal("1.50")) < 0) {
            return "<1.50";
        }
        if (odds.compareTo(new BigDecimal("2.00")) <= 0) {
            return "1.50-2.00";
        }
        if (odds.compareTo(new BigDecimal("3.00")) <= 0) {
            return "2.01-3.00";
        }
        if (odds.compareTo(new BigDecimal("6.00")) <= 0) {
            return "3.01-6.00";
        }
        return ">6.00";
    }

    private List<BacktestWalkForwardValidation> walkForward(
        BetxConfig config,
        String competition,
        List<BacktestInputRow> rows,
        List<BigDecimal> thresholds
    ) {
        if (rows.isEmpty()) {
            return List.of(BacktestWalkForwardValidation.insufficientSeasons(competition));
        }
        TreeMap<Integer, List<BacktestInputRow>> rowsBySeason = new TreeMap<>();
        for (BacktestInputRow row : rows) {
            rowsBySeason.computeIfAbsent(season(row), ignored -> new ArrayList<>()).add(row);
        }
        List<Integer> seasons = new ArrayList<>(rowsBySeason.keySet());
        if (seasons.size() < 2 || thresholds.isEmpty()) {
            return List.of(BacktestWalkForwardValidation.insufficientSeasons(competition));
        }
        List<BacktestWalkForwardValidation> validations = new ArrayList<>();
        for (int index = 0; index < seasons.size() - 1; index++) {
            int trainSeason = seasons.get(index);
            int evaluationSeason = seasons.get(index + 1);
            ThresholdTrainingSelection selection = trainThreshold(config, rowsBySeason.get(trainSeason), thresholds);
            BacktestResult evaluationResult = runRows(
                config,
                rowsBySeason.get(evaluationSeason),
                new EventMarketAnalyzer(selection.threshold())
            );
            validations.add(new BacktestWalkForwardValidation(
                competition,
                BacktestWalkForwardStatus.EVALUATED,
                trainSeason,
                evaluationSeason,
                selection.threshold(),
                selection.result(),
                evaluationResult
            ));
        }
        return validations;
    }

    private ThresholdTrainingSelection trainThreshold(
        BetxConfig config,
        List<BacktestInputRow> trainRows,
        List<BigDecimal> thresholds
    ) {
        ThresholdTrainingSelection best = null;
        for (BigDecimal threshold : thresholds) {
            BacktestResult result = runRows(config, trainRows, new EventMarketAnalyzer(threshold));
            ThresholdTrainingSelection candidate = new ThresholdTrainingSelection(threshold, result);
            if (best == null || candidate.isBetterThan(best)) {
                best = candidate;
            }
        }
        return best;
    }

    private int season(BacktestInputRow row) {
        return (row.marketStartTime() == null ? row.observedAt() : row.marketStartTime())
            .atZone(ZoneOffset.UTC)
            .getYear();
    }

    private Optional<StrategyConfig> valueFootballStrategy(BetxConfig config) {
        return config.strategies().stream()
            .filter(strategyConfig -> ValueFootballSignalStrategy.STRATEGY_NAME.equals(strategyConfig.name()))
            .findFirst();
    }

    private BacktestTrade toTrade(BacktestInputRow row, BacktestStrategyDecision decision, BigDecimal stake) {
        BigDecimal profitLoss = row.outcome() == BacktestOutcome.WIN
            ? stake.multiply(row.bestBackPrice().subtract(BigDecimal.ONE))
            : stake.negate();
        return new BacktestTrade(
            row.observedAt(),
            row.exchange(),
            row.marketId(),
            row.eventName(),
            row.marketName(),
            row.selectionId(),
            row.runnerName(),
            BetSide.BACK,
            row.bestBackPrice(),
            stake,
            row.outcome(),
            profitLoss,
            row.competitionName(),
            row.season(),
            row.oddsSource(),
            decision.confidenceLabel(),
            decision.oddsMovementPercent(),
            BacktestRunnerType.fromSelectionId(row.selectionId())
        );
    }

    private String runnerKey(BacktestInputRow row) {
        return row.exchange() + "|" + row.marketId() + "|" + row.selectionId();
    }

    private String marketObservationKey(BacktestInputRow row) {
        return row.exchange() + "|" + row.marketId() + "|" + row.observedAt();
    }

    private String marketKey(BacktestInputRow row) {
        return row.exchange() + "|" + row.marketId();
    }

    private String marketKey(BacktestTrade trade) {
        return trade.exchange() + "|" + trade.marketId();
    }

    private BigDecimal percentageDelta(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
            .divide(previous, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(8, RoundingMode.HALF_UP);
    }

    private PreparedRows prepareRows(List<BacktestInputRow> rows) {
        List<BacktestInputRow> orderedRows = rows == null
            ? List.of()
            : rows.stream().sorted(Comparator.comparing(BacktestInputRow::observedAt)).toList();
        List<BacktestInputRow> prepared = new ArrayList<>();
        Set<String> seenRunnerObservations = new HashSet<>();
        int ignoredAtOrAfterStart = 0;
        int duplicateRunnerRows = 0;
        for (BacktestInputRow row : orderedRows) {
            if (row.marketStartTime() != null && !row.observedAt().isBefore(row.marketStartTime())) {
                ignoredAtOrAfterStart++;
                continue;
            }
            String rowKey = row.exchange() + "|" + row.marketId() + "|" + row.selectionId() + "|" + row.observedAt();
            if (!seenRunnerObservations.add(rowKey)) {
                duplicateRunnerRows++;
                continue;
            }
            prepared.add(row);
        }
        return new PreparedRows(prepared, new BacktestLeakageDiagnostics(ignoredAtOrAfterStart, duplicateRunnerRows));
    }

    private DatasetProfile datasetProfile(List<BacktestInputRow> rows) {
        List<String> oddsSources = rows.stream()
            .map(BacktestInputRow::oddsSource)
            .distinct()
            .sorted()
            .toList();
        boolean bookmaker = rows.stream().anyMatch(row -> "football-data".equals(row.exchange()));
        if (bookmaker) {
            if (oddsSources.equals(List.of("closing-average", "opening-bookmaker"))) {
                return new DatasetProfile(
                    "opening-closing",
                    "bookmaker",
                    BacktestDatasetCapability.OPENING_CLOSING,
                    BigDecimal.ZERO
                );
            }
            if (oddsSources.size() <= 1) {
                return new DatasetProfile(
                    oddsSources.isEmpty() ? "unknown" : oddsSources.getFirst(),
                    "bookmaker",
                    BacktestDatasetCapability.SINGLE_PRICE,
                    BigDecimal.ZERO
                );
            }
            throw new BacktestValidationException(
                "Backtest comparison input must use one odds_source or paired opening-bookmaker plus closing-average."
            );
        }
        return new DatasetProfile(
            oddsSources.isEmpty() ? "unknown" : String.join("+", oddsSources),
            "exchange",
            BacktestDatasetCapability.EXCHANGE_SNAPSHOTS,
            DEFAULT_COMMISSION_RATE
        );
    }

    private boolean isTradableRow(BacktestInputRow row, BacktestDatasetCapability datasetCapability) {
        return datasetCapability != BacktestDatasetCapability.OPENING_CLOSING || "closing-average".equals(row.oddsSource());
    }

    private List<BacktestInputRow> tradableRows(List<BacktestInputRow> rows, BacktestDatasetCapability datasetCapability) {
        return rows.stream()
            .filter(row -> isTradableRow(row, datasetCapability))
            .toList();
    }

    private List<BacktestAnalyzerDiagnostic> analyzerDiagnostics(List<StrategyResult> results) {
        return results.stream()
            .flatMap(result -> result.diagnostics().stream())
            .collect(Collectors.groupingBy(
                diagnostic -> diagnostic.strategyId() + "|" + diagnostic.oddsSource() + "|" + diagnostic.reason(),
                TreeMap::new,
                Collectors.summingInt(BacktestAnalyzerDiagnostic::count)
            ))
            .entrySet()
            .stream()
            .map(entry -> {
                String[] parts = entry.getKey().split("\\|", -1);
                return new BacktestAnalyzerDiagnostic(parts[0], parts[1], parts[2], entry.getValue());
            })
            .toList();
    }

    private List<BacktestStrategyUncertaintyReport> uncertaintyReports(
        List<BacktestStrategyReport> strategyReports,
        long randomSeed
    ) {
        return strategyReports.stream()
            .filter(strategy -> strategy.strategyId().equals("value-football-draw-only"))
            .map(strategy -> uncertaintyReport(strategy, randomSeed))
            .toList();
    }

    private BacktestStrategyUncertaintyReport uncertaintyReport(BacktestStrategyReport strategy, long randomSeed) {
        List<BacktestTrade> trades = strategy.result().trades();
        if (trades.isEmpty()) {
            return new BacktestStrategyUncertaintyReport(
                strategy.strategyId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            );
        }
        List<BigDecimal> bootstrappedRois = bootstrapNetRois(strategy.marketResults(), randomSeed);
        BigDecimal grossProfit = trades.stream()
            .map(BacktestTrade::profitLoss)
            .filter(pnl -> pnl.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = trades.stream()
            .map(BacktestTrade::profitLoss)
            .filter(pnl -> pnl.compareTo(BigDecimal.ZERO) < 0)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitFactor = grossLoss.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP);
        BigDecimal averageOdds = trades.stream()
            .map(BacktestTrade::odds)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(trades.size()), 4, RoundingMode.HALF_UP);
        BigDecimal evPerTrade = strategy.netProfitLoss()
            .divide(BigDecimal.valueOf(trades.size()), 4, RoundingMode.HALF_UP);
        return new BacktestStrategyUncertaintyReport(
            strategy.strategyId(),
            percentile(bootstrappedRois, new BigDecimal("0.025")),
            percentile(bootstrappedRois, new BigDecimal("0.975")),
            longestLosingStreak(trades),
            profitFactor,
            averageOdds,
            evPerTrade
        );
    }

    private List<BigDecimal> bootstrapNetRois(List<BacktestMarketResult> marketResults, long randomSeed) {
        List<BacktestMarketResult> settledMarkets = marketResults.stream()
            .filter(market -> market.totalStake().compareTo(BigDecimal.ZERO) > 0)
            .toList();
        if (settledMarkets.isEmpty()) {
            return List.of(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        int runs = 1000;
        Random random = new Random(randomSeed);
        List<BigDecimal> rois = new ArrayList<>();
        for (int run = 0; run < runs; run++) {
            BigDecimal pnl = BigDecimal.ZERO;
            BigDecimal stake = BigDecimal.ZERO;
            for (int index = 0; index < settledMarkets.size(); index++) {
                BacktestMarketResult market = settledMarkets.get(random.nextInt(settledMarkets.size()));
                pnl = pnl.add(market.netPnl());
                stake = stake.add(market.totalStake());
            }
            rois.add(percent(pnl, stake));
        }
        return rois.stream().sorted().toList();
    }

    private BigDecimal percentile(List<BigDecimal> sortedValues, BigDecimal percentile) {
        if (sortedValues.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        int index = percentile.multiply(BigDecimal.valueOf(sortedValues.size() - 1))
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();
        return sortedValues.get(index).setScale(2, RoundingMode.HALF_UP);
    }

    private int longestLosingStreak(List<BacktestTrade> trades) {
        int longest = 0;
        int current = 0;
        for (BacktestTrade trade : trades) {
            if (trade.profitLoss().compareTo(BigDecimal.ZERO) < 0) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private BacktestResult applySlippage(BacktestResult result, BigDecimal oddsSlippageRate) {
        return applySlippage(result, oddsSlippageRate, BacktestSlippageModel.PROFIT_HAIRCUT);
    }

    private BacktestResult applySlippage(
        BacktestResult result,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel
    ) {
        BigDecimal rate = oddsSlippageRate == null ? BigDecimal.ZERO : oddsSlippageRate;
        if (rate.compareTo(BigDecimal.ZERO) == 0 || result.trades().isEmpty()) {
            return result;
        }
        List<BacktestTrade> adjustedTrades = result.trades().stream()
            .map(trade -> adjustedTrade(trade, rate, slippageModel))
            .toList();
        return BacktestResult.from(result.rowsRead(), result.runnersAnalyzed(), adjustedTrades);
    }

    private BacktestTrade adjustedTrade(
        BacktestTrade trade,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel
    ) {
        BigDecimal adjustedOdds = adjustedOdds(trade.odds(), oddsSlippageRate, slippageModel);
        BigDecimal profitLoss = trade.outcome() == BacktestOutcome.WIN
            ? trade.stake().multiply(adjustedOdds.subtract(BigDecimal.ONE))
            : trade.stake().negate();
        return new BacktestTrade(
            trade.observedAt(),
            trade.exchange(),
            trade.marketId(),
            trade.eventName(),
            trade.marketName(),
            trade.selectionId(),
            trade.runnerName(),
            trade.side(),
            adjustedOdds,
            trade.stake(),
            trade.outcome(),
            profitLoss,
            trade.competitionName(),
            trade.season(),
            trade.oddsSource(),
            trade.confidenceLabel(),
            trade.oddsMovementPercent(),
            trade.runnerType()
        );
    }

    private BigDecimal adjustedOdds(
        BigDecimal originalOdds,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel
    ) {
        BacktestSlippageModel model = slippageModel == null ? BacktestSlippageModel.PROFIT_HAIRCUT : slippageModel;
        return model.adjustedOdds(originalOdds, oddsSlippageRate);
    }

    private List<BacktestSlippageReport> slippageReports(List<StrategyResult> results, BigDecimal commissionRate) {
        return results.stream()
            .filter(result -> result.strategyId().equals("value-football-draw-only"))
            .flatMap(result -> DEFAULT_SLIPPAGE_SCENARIOS.stream()
                .map(rate -> slippageReport(result.rawResult(), rate, commissionRate)))
            .toList();
    }

    private BacktestSlippageReport slippageReport(BacktestResult rawResult, BigDecimal slippageRate, BigDecimal commissionRate) {
        BacktestResult adjusted = applySlippage(rawResult, slippageRate);
        List<BacktestMarketResult> markets = adjusted.trades().stream()
            .collect(Collectors.groupingBy(this::marketKey, TreeMap::new, Collectors.toList()))
            .values()
            .stream()
            .map(trades -> BacktestMarketResult.from("value-football-draw-only", trades, commissionRate))
            .toList();
        BigDecimal grossPnl = markets.stream().map(BacktestMarketResult::grossPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netPnl = markets.stream().map(BacktestMarketResult::netPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal stake = markets.stream().map(BacktestMarketResult::totalStake).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BacktestSlippageReport(
            "value-football-draw-only",
            slippageRate,
            adjusted.trades().size(),
            grossPnl,
            netPnl,
            percent(netPnl, stake)
        );
    }

    private List<BacktestDrawOnlySeasonLeagueReport> drawOnlySeasonLeagueReports(List<BacktestStrategyReport> strategyReports) {
        return strategyReports.stream()
            .filter(strategy -> strategy.strategyId().equals("value-football-draw-only"))
            .flatMap(strategy -> strategy.result().trades().stream()
                .collect(Collectors.groupingBy(
                    trade -> trade.competitionName() + "|" + trade.season(),
                    TreeMap::new,
                    Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|", -1);
                    return new BacktestDrawOnlySeasonLeagueReport(parts[0], parts[1], entry.getValue());
                }))
            .toList();
    }

    private List<BacktestMovementReport> movementReports(List<BacktestStrategyReport> strategyReports) {
        return strategyReports.stream()
            .filter(strategy -> strategy.strategyId().equals("value-football-draw-only"))
            .flatMap(strategy -> strategy.result().trades().stream()
                .filter(trade -> trade.oddsMovementPercent() != null)
                .collect(Collectors.groupingBy(
                    trade -> movementBucket(trade.oddsMovementPercent()),
                    TreeMap::new,
                    Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> new BacktestMovementReport(strategy.strategyId(), entry.getKey(), entry.getValue())))
            .toList();
    }

    private String movementBucket(BigDecimal movementPercent) {
        if (movementPercent.compareTo(new BigDecimal("-10")) <= 0) {
            return "strong drop";
        }
        if (movementPercent.compareTo(new BigDecimal("-3")) <= 0) {
            return "moderate drop";
        }
        if (movementPercent.compareTo(new BigDecimal("3")) <= 0) {
            return "stable";
        }
        if (movementPercent.compareTo(new BigDecimal("10")) <= 0) {
            return "moderate drift";
        }
        return "strong drift";
    }

    private List<BacktestEquityCurveRow> equityCurveRows(List<BacktestStrategyReport> strategyReports) {
        List<BacktestTrade> trades = strategyReports.stream()
            .filter(strategy -> strategy.strategyId().equals("value-football-draw-only"))
            .findFirst()
            .map(strategy -> strategy.result().trades())
            .orElse(List.of());
        BigDecimal cumulative = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        List<BacktestEquityCurveRow> rows = new ArrayList<>();
        for (BacktestTrade trade : trades.stream().sorted(Comparator.comparing(BacktestTrade::observedAt)).toList()) {
            cumulative = cumulative.add(trade.profitLoss());
            if (cumulative.compareTo(peak) > 0) {
                peak = cumulative;
            }
            rows.add(new BacktestEquityCurveRow(
                trade.observedAt(),
                trade.competitionName(),
                trade.season(),
                trade.eventName(),
                trade.odds(),
                trade.outcome(),
                trade.profitLoss(),
                cumulative,
                peak.subtract(cumulative)
            ));
        }
        return rows;
    }

    private List<BacktestPaperTrade> paperTrades(
        List<StrategyResult> results,
        List<BacktestInputRow> rows,
        BigDecimal commissionRate,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel
    ) {
        List<BacktestTrade> rawTrades = results.stream()
            .filter(result -> result.strategyId().equals("value-football-draw-only"))
            .findFirst()
            .map(result -> result.rawResult().trades())
            .orElse(List.of());
        if (rawTrades.isEmpty()) {
            return List.of();
        }
        Map<String, List<BacktestInputRow>> rowsByRunner = rows.stream()
            .collect(Collectors.groupingBy(this::runnerKey, TreeMap::new, Collectors.toList()));
        return rawTrades.stream()
            .sorted(Comparator.comparing(BacktestTrade::observedAt))
            .map(trade -> paperTrade(trade, rowsByRunner, commissionRate, oddsSlippageRate, slippageModel))
            .toList();
    }

    private BacktestPaperTrade paperTrade(
        BacktestTrade trade,
        Map<String, List<BacktestInputRow>> rowsByRunner,
        BigDecimal commissionRate,
        BigDecimal oddsSlippageRate,
        BacktestSlippageModel slippageModel
    ) {
        BigDecimal recommendationOdds = recommendationOdds(trade, rowsByRunner);
        BigDecimal closingOdds = closingLineOdds(trade, rowsByRunner);
        BigDecimal executionOdds = adjustedOdds(recommendationOdds, oddsSlippageRate, slippageModel);
        BigDecimal grossPnl = trade.outcome() == BacktestOutcome.WIN
            ? trade.stake().multiply(executionOdds.subtract(BigDecimal.ONE))
            : trade.stake().negate();
        BigDecimal commission = grossPnl.compareTo(BigDecimal.ZERO) > 0
            ? grossPnl.multiply(commissionRate == null ? BigDecimal.ZERO : commissionRate)
            : BigDecimal.ZERO;
        BigDecimal netPnl = grossPnl.subtract(commission);
        return new BacktestPaperTrade(
            trade.marketId(),
            trade.marketId(),
            trade.competitionName(),
            trade.season(),
            trade.eventName(),
            trade.runnerName(),
            trade.side(),
            trade.observedAt(),
            trade.observedAt(),
            trade.observedAt(),
            recommendationOdds,
            recommendationOdds,
            executionOdds,
            closingOdds,
            trade.outcome(),
            grossPnl,
            commission,
            netPnl,
            null,
            null,
            trade.oddsMovementPercent() == null ? "unknown" : movementBucket(trade.oddsMovementPercent())
        );
    }

    private BigDecimal recommendationOdds(
        BacktestTrade trade,
        Map<String, List<BacktestInputRow>> rowsByRunner
    ) {
        String key = trade.exchange() + "|" + trade.marketId() + "|" + trade.selectionId();
        return rowsByRunner.getOrDefault(key, List.of()).stream()
            .filter(row -> row.observedAt().isBefore(trade.observedAt()))
            .max(Comparator.comparing(BacktestInputRow::observedAt))
            .map(BacktestInputRow::bestBackPrice)
            .orElse(trade.odds());
    }

    private BigDecimal closingLineOdds(
        BacktestTrade trade,
        Map<String, List<BacktestInputRow>> rowsByRunner
    ) {
        String key = trade.exchange() + "|" + trade.marketId() + "|" + trade.selectionId();
        return rowsByRunner.getOrDefault(key, List.of()).stream()
            .filter(row -> row.observedAt().isAfter(trade.observedAt()))
            .min(Comparator.comparing(BacktestInputRow::observedAt))
            .map(BacktestInputRow::bestBackPrice)
            .orElse(trade.odds());
    }

    private List<BacktestRollingPaperWindow> rollingPaperWindows(List<BacktestPaperTrade> paperTrades) {
        List<BacktestPaperTrade> ordered = paperTrades.stream()
            .sorted(Comparator.comparing(BacktestPaperTrade::recommendationTimestamp))
            .toList();
        return List.of(100, 250, 500).stream()
            .filter(window -> ordered.size() >= window)
            .map(window -> rollingPaperWindow(window, ordered.subList(ordered.size() - window, ordered.size())))
            .toList();
    }

    private BacktestRollingPaperWindow rollingPaperWindow(int windowSize, List<BacktestPaperTrade> trades) {
        BigDecimal pnl = trades.stream().map(BacktestPaperTrade::netPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal stake = BigDecimal.valueOf(5L * trades.size());
        List<BigDecimal> clvs = trades.stream()
            .map(BacktestPaperTrade::decimalClvRatio)
            .filter(java.util.Objects::nonNull)
            .toList();
        BigDecimal clv = clvs.isEmpty()
            ? null
            : clvs.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(clvs.size()), 8, RoundingMode.HALF_UP);
        return new BacktestRollingPaperWindow(
            windowSize,
            trades.size(),
            percent(pnl, stake),
            clv,
            paperMaxDrawdown(trades),
            paperLongestLosingStreak(trades)
        );
    }

    private List<BacktestClvBreakdownReport> clvBreakdowns(List<BacktestPaperTrade> paperTrades) {
        if (paperTrades.stream().noneMatch(trade -> trade.decimalClvRatio() != null)) {
            return List.of();
        }
        List<BacktestClvBreakdownReport> reports = new ArrayList<>();
        reports.addAll(paperTrades.stream()
            .collect(Collectors.groupingBy(BacktestPaperTrade::league, TreeMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> BacktestClvBreakdownReport.from("league", entry.getKey(), entry.getValue()))
            .toList());
        reports.addAll(paperTrades.stream()
            .collect(Collectors.groupingBy(BacktestPaperTrade::movementBucket, TreeMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> BacktestClvBreakdownReport.from("movement_bucket", entry.getKey(), entry.getValue()))
            .toList());
        return reports;
    }

    private BigDecimal paperMaxDrawdown(List<BacktestPaperTrade> trades) {
        BigDecimal cumulative = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal drawdown = BigDecimal.ZERO;
        for (BacktestPaperTrade trade : trades) {
            cumulative = cumulative.add(trade.netPnl());
            if (cumulative.compareTo(peak) > 0) {
                peak = cumulative;
            }
            BigDecimal currentDrawdown = peak.subtract(cumulative);
            if (currentDrawdown.compareTo(drawdown) > 0) {
                drawdown = currentDrawdown;
            }
        }
        return drawdown;
    }

    private int paperLongestLosingStreak(List<BacktestPaperTrade> trades) {
        int longest = 0;
        int current = 0;
        for (BacktestPaperTrade trade : trades) {
            if (trade.netPnl().compareTo(BigDecimal.ZERO) < 0) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private record ThresholdTrainingSelection(BigDecimal threshold, BacktestResult result) {
        private boolean isBetterThan(ThresholdTrainingSelection other) {
            int roiComparison = result.roiPercent().compareTo(other.result().roiPercent());
            if (roiComparison != 0) {
                return roiComparison > 0;
            }
            return result.trades().size() > other.result().trades().size();
        }
    }

    private record StrategyResult(
        String strategyId,
        BacktestResult rawResult,
        BacktestResult result,
        List<BacktestMarketResult> marketResults,
        List<BacktestAnalyzerDiagnostic> diagnostics
    ) {
        private StrategyResult {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }

    private record RunRowsResult(BacktestResult result, List<BacktestAnalyzerDiagnostic> diagnostics) {
        private RunRowsResult {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }

    private record DatasetProfile(
        String oddsSource,
        String pricingMode,
        BacktestDatasetCapability capability,
        BigDecimal defaultCommissionRate
    ) {
    }

    private record PreparedRows(List<BacktestInputRow> rows, BacktestLeakageDiagnostics diagnostics) {
    }
}
