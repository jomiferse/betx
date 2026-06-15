package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/** Formats historical backtest results for terminal output. */
public class BacktestResultFormatter {
    private static final int TRADE_PREVIEW_LIMIT = 3;

    public List<String> format(BacktestResult result) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Backtest complete | rows=" + result.rowsRead()
            + " | runnersAnalyzed=" + result.runnersAnalyzed()
            + " | trades=" + result.trades().size()
            + " | wins=" + result.wins()
            + " | losses=" + result.losses());
        lines.add("Performance | staked=" + value(result.totalStaked())
            + " | pnl=" + twoDecimal(result.profitLoss())
            + " | roi=" + twoDecimal(result.roiPercent()) + "%"
            + " | strikeRate=" + twoDecimal(result.strikeRatePercent()) + "%"
            + " | maxDrawdown=" + twoDecimal(result.maxDrawdown()));
        if (result.trades().isEmpty()) {
            lines.add("No simulated trades found.");
            return lines;
        }
        lines.add("Top trades");
        topTrades(result).forEach(trade -> lines.add(formatTrade(trade)));
        lines.add("Bottom trades");
        bottomTrades(result).forEach(trade -> lines.add(formatTrade(trade)));
        lines.add("Strategy evaluation");
        for (BacktestSegmentType type : BacktestSegmentType.values()) {
            lines.add("By " + displayName(type));
            result.evaluation().segments(type).stream()
                .limit(5)
                .forEach(segment -> lines.add(formatSegment(segment)));
        }
        return lines;
    }

    public List<String> formatRobustness(BacktestRobustnessReport report) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Robustness validation");
        lines.add("ROI per league");
        report.leagueReports().forEach(league -> lines.add(formatLeague(league)));
        lines.add("Walk-forward validation");
        report.walkForwardValidations().forEach(validation -> lines.add(formatWalkForward(validation)));
        lines.add("Parameter sensitivity");
        report.sensitivityReports().forEach(sensitivity -> lines.add(formatSensitivity(sensitivity)));
        return lines;
    }

    public List<String> formatComparison(BacktestComparisonReport report) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Strategy comparison | randomSeed=" + report.randomSeed()
            + " | pricingMode=" + report.pricingMode()
            + " | commissionRate=" + value(report.commissionRate())
            + " | oddsSlippageRate=" + value(report.oddsSlippageRate())
            + " | slippageModel=" + report.slippageModel()
            + " | oddsSource=" + report.oddsSource()
            + " | datasetCapability=" + report.datasetCapability());
        report.strategyReports().forEach(strategy -> lines.add(formatStrategy(strategy)));
        lines.add("League comparison");
        if (report.leagueReports().isEmpty()) {
            lines.add("No league strategy trades found.");
        } else {
            report.leagueReports().forEach(league -> lines.add(formatStrategyLeague(league)));
        }
        lines.add("Market selection analysis");
        report.marketSelectionReports().forEach(selection -> lines.add(formatMarketSelection(selection)));
        lines.add("Strategy breakdowns");
        report.breakdownReports().forEach(breakdown -> lines.add(formatBreakdown(breakdown)));
        lines.add("Season validation");
        report.seasonReports().forEach(season -> lines.add(formatSeason(season)));
        lines.add("Value-football draw-only league seasons");
        report.drawOnlySeasonLeagueReports().forEach(season -> lines.add(formatDrawOnlySeasonLeague(season)));
        lines.add("Season summary");
        report.seasonSummaries().forEach(summary -> lines.add(formatSeasonSummary(summary)));
        lines.add("Out-of-sample periods");
        report.outOfSampleReports().forEach(period -> lines.add(formatOutOfSample(period)));
        lines.add("Draw-only slippage scenarios");
        report.slippageReports().forEach(scenario -> lines.add(formatSlippage(scenario)));
        lines.add("Opening-to-closing movement diagnostics");
        report.movementReports().forEach(movement -> lines.add(formatMovement(movement)));
        lines.add("Paper trading CLV");
        lines.add(formatClvSummary(report.clvSummary()));
        report.clvBreakdowns().forEach(breakdown -> lines.add(formatClvBreakdown(breakdown)));
        lines.add(formatPaperValidation(report.paperValidation()));
        report.rollingPaperWindows().forEach(window -> lines.add(formatRollingPaperWindow(window)));
        lines.add("Leakage diagnostics");
        lines.add(formatLeakageDiagnostics(report.leakageDiagnostics()));
        lines.add("Analyzer diagnostics");
        if (report.analyzerDiagnostics().isEmpty()) {
            lines.add("No analyzer rejections found.");
        } else {
            report.analyzerDiagnostics().forEach(diagnostic -> lines.add(formatAnalyzerDiagnostic(diagnostic)));
        }
        lines.add("Statistical uncertainty");
        report.uncertaintyReports().forEach(uncertainty -> lines.add(formatUncertainty(uncertainty)));
        return lines;
    }

    private List<BacktestTrade> topTrades(BacktestResult result) {
        return result.trades().stream()
            .sorted(Comparator.comparing(BacktestTrade::profitLoss).reversed())
            .limit(TRADE_PREVIEW_LIMIT)
            .toList();
    }

    private List<BacktestTrade> bottomTrades(BacktestResult result) {
        return result.trades().stream()
            .sorted(Comparator.comparing(BacktestTrade::profitLoss))
            .limit(TRADE_PREVIEW_LIMIT)
            .toList();
    }

    private String formatTrade(BacktestTrade trade) {
        return "TRADE | " + trade.outcome()
            + " | observedAt=" + trade.observedAt()
            + " | event=" + nullSafe(trade.eventName())
            + " | runner=" + nullSafe(trade.runnerName())
            + " | odds=" + value(trade.odds())
            + " | stake=" + value(trade.stake())
            + " | pnl=" + trade.profitLoss().toPlainString();
    }

    private String formatSegment(BacktestSegment segment) {
        return "SEGMENT | " + label(segment.type())
            + " | " + segment.name()
            + " | trades=" + segment.trades()
            + " | wins=" + segment.wins()
            + " | losses=" + segment.losses()
            + " | staked=" + value(segment.totalStaked())
            + " | pnl=" + twoDecimal(segment.profitLoss())
            + " | roi=" + twoDecimal(segment.roiPercent()) + "%"
            + " | strikeRate=" + twoDecimal(segment.strikeRatePercent()) + "%"
            + " | maxDrawdown=" + twoDecimal(segment.maxDrawdown())
            + flag(segment);
    }

    private String formatLeague(BacktestLeagueReport league) {
        if (!league.hasData()) {
            return "LEAGUE | " + league.competitionName() + " | status=NO DATA";
        }
        BacktestResult result = league.result();
        return "LEAGUE | " + league.competitionName()
            + " | trades=" + result.trades().size()
            + " | roi=" + twoDecimal(result.roiPercent()) + "%"
            + " | maxDrawdown=" + twoDecimal(result.maxDrawdown())
            + flag(result);
    }

    private String formatWalkForward(BacktestWalkForwardValidation validation) {
        if (validation.status() == BacktestWalkForwardStatus.INSUFFICIENT_SEASONS) {
            return "WALK_FORWARD | " + validation.competitionName() + " | status=insufficient_seasons";
        }
        BacktestResult evaluation = validation.evaluationResult();
        return "WALK_FORWARD | " + validation.competitionName()
            + " | trainSeason=" + validation.trainSeason()
            + " | evaluationSeason=" + validation.evaluationSeason()
            + " | selectedThreshold=" + threshold(validation.selectedThreshold()) + "%"
            + " | trainRoi=" + twoDecimal(validation.trainResult().roiPercent()) + "%"
            + " | evaluationTrades=" + evaluation.trades().size()
            + " | evaluationRoi=" + twoDecimal(evaluation.roiPercent()) + "%"
            + " | evaluationMaxDrawdown=" + twoDecimal(evaluation.maxDrawdown())
            + flag(evaluation);
    }

    private String formatSensitivity(BacktestSensitivityReport sensitivity) {
        BacktestResult result = sensitivity.result();
        return "SENSITIVITY | " + sensitivity.competitionName()
            + " | threshold=" + threshold(sensitivity.threshold()) + "%"
            + " | trades=" + result.trades().size()
            + " | roi=" + twoDecimal(result.roiPercent()) + "%"
            + flag(result);
    }

    private String formatStrategy(BacktestStrategyReport strategy) {
        BacktestResult result = strategy.result();
        return "STRATEGY | strategy=" + strategy.strategyId()
            + " | trades=" + result.trades().size()
            + " | wins=" + result.wins()
            + " | losses=" + result.losses()
            + " | roi=" + twoDecimal(result.roiPercent()) + "%"
            + " | grossPnl=" + twoDecimal(strategy.grossProfitLoss())
            + " | commission=" + twoDecimal(strategy.commissionPaid())
            + " | netPnl=" + twoDecimal(strategy.netProfitLoss())
            + " | netRoi=" + twoDecimal(strategy.netRoiPercent()) + "%"
            + " | maxDrawdown=" + twoDecimal(result.maxDrawdown())
            + " | strikeRate=" + twoDecimal(result.strikeRatePercent()) + "%";
    }

    private String formatStrategyLeague(BacktestStrategyLeagueReport league) {
        BacktestResult result = league.result();
        return "LEAGUE_STRATEGY | strategy=" + league.strategyId()
            + " | league=" + league.competitionName()
            + " | trades=" + result.trades().size()
            + " | wins=" + result.wins()
            + " | losses=" + result.losses()
            + " | roi=" + twoDecimal(result.roiPercent()) + "%"
            + " | grossPnl=" + twoDecimal(league.grossProfitLoss())
            + " | commission=" + twoDecimal(league.commissionPaid())
            + " | netPnl=" + twoDecimal(league.netProfitLoss())
            + " | netRoi=" + twoDecimal(league.netRoiPercent()) + "%"
            + " | maxDrawdown=" + twoDecimal(result.maxDrawdown())
            + " | strikeRate=" + twoDecimal(result.strikeRatePercent()) + "%";
    }

    private String formatMarketSelection(BacktestMarketSelectionReport selection) {
        return "MARKET_SELECTION | strategy=" + selection.strategyId()
            + " | selectedRunners=" + selection.selectedRunners()
            + " | markets=" + selection.markets()
            + " | totalStake=" + twoDecimal(selection.totalStake())
            + " | grossPnl=" + twoDecimal(selection.grossPnl())
            + " | commission=" + twoDecimal(selection.commissionPaid())
            + " | netPnl=" + twoDecimal(selection.netPnl())
            + " | netRoi=" + twoDecimal(selection.netRoiPercent()) + "%"
            + " | maxExposure=" + twoDecimal(selection.maximumExposure());
    }

    private String formatBreakdown(BacktestBreakdownReport breakdown) {
        BacktestResult result = breakdown.result();
        return "BREAKDOWN | kind=" + breakdown.kind()
            + " | strategy=" + breakdown.strategyId()
            + " | league=" + breakdown.league()
            + " | runnerType=" + breakdown.runnerType()
            + " | oddsBand=" + breakdown.oddsBand()
            + " | trades=" + result.trades().size()
            + " | roi=" + twoDecimal(result.roiPercent()) + "%"
            + " | pnl=" + twoDecimal(result.profitLoss());
    }

    private String formatSeason(BacktestSeasonReport season) {
        return "SEASON | strategy=" + season.strategyId()
            + " | season=" + season.season()
            + " | markets=" + season.markets()
            + " | trades=" + season.trades()
            + " | grossPnl=" + twoDecimal(season.grossProfitLoss())
            + " | commission=" + twoDecimal(season.commissionPaid())
            + " | netPnl=" + twoDecimal(season.netProfitLoss())
            + " | grossRoi=" + twoDecimal(season.grossRoiPercent()) + "%"
            + " | netRoi=" + twoDecimal(season.netRoiPercent()) + "%"
            + " | grossMaxDrawdown=" + twoDecimal(season.grossMaxDrawdown())
            + " | netMaxDrawdown=" + twoDecimal(season.netMaxDrawdown())
            + " | strikeRate=" + twoDecimal(season.strikeRatePercent()) + "%";
    }

    private String formatDrawOnlySeasonLeague(BacktestDrawOnlySeasonLeagueReport season) {
        return "DRAW_ONLY_SEASON_LEAGUE"
            + " | league=" + season.league()
            + " | season=" + season.season()
            + " | trades=" + season.trades()
            + " | wins=" + season.wins()
            + " | averageOdds=" + twoDecimal(season.averageOdds())
            + " | grossPnl=" + twoDecimal(season.grossProfitLoss())
            + " | roi=" + twoDecimal(season.roiPercent()) + "%"
            + " | maxDrawdown=" + twoDecimal(season.maxDrawdown())
            + " | longestLosingStreak=" + season.longestLosingStreak();
    }

    private String formatSeasonSummary(BacktestSeasonSummary summary) {
        return "SEASON_SUMMARY | strategy=" + summary.strategyId()
            + " | evaluatedSeasons=" + summary.evaluatedSeasons()
            + " | profitableSeasons=" + summary.profitableSeasons()
            + " | losingSeasons=" + summary.losingSeasons()
            + " | meanNetRoi=" + twoDecimal(summary.meanNetRoiPercent()) + "%"
            + " | medianNetRoi=" + twoDecimal(summary.medianNetRoiPercent()) + "%"
            + " | worstNetRoi=" + twoDecimal(summary.worstNetRoiPercent()) + "%"
            + " | bestNetRoi=" + twoDecimal(summary.bestNetRoiPercent()) + "%"
            + " | totalNetRoi=" + twoDecimal(summary.totalNetRoiPercent()) + "%";
    }

    private String formatOutOfSample(BacktestOutOfSampleReport period) {
        BacktestResult result = period.result();
        return "OOS | strategy=" + period.strategyId()
            + " | period=" + period.period()
            + " | seasons=" + period.startSeason() + "-" + period.endSeason()
            + " | trades=" + result.trades().size()
            + " | roi=" + twoDecimal(result.roiPercent()) + "%"
            + " | pnl=" + twoDecimal(result.profitLoss());
    }

    private String formatSlippage(BacktestSlippageReport scenario) {
        return "SLIPPAGE | strategy=" + scenario.strategyId()
            + " | rate=" + value(scenario.slippageRate())
            + " | trades=" + scenario.trades()
            + " | grossPnl=" + twoDecimal(scenario.grossPnl())
            + " | netPnl=" + twoDecimal(scenario.netPnl())
            + " | netRoi=" + twoDecimal(scenario.netRoiPercent()) + "%";
    }

    private String formatMovement(BacktestMovementReport movement) {
        return "MOVEMENT | strategy=" + movement.strategyId()
            + " | bucket=" + movement.movementBucket()
            + " | trades=" + movement.trades()
            + " | pnl=" + twoDecimal(movement.pnl())
            + " | roi=" + twoDecimal(movement.roiPercent()) + "%";
    }

    private String formatClvSummary(BacktestClvSummary summary) {
        return "CLV | strategy=value-football-draw-only"
            + " | status=" + summary.status()
            + " | validClvTrades=" + summary.trades()
            + " | averageClv=" + valueOrUnavailable(summary.averageClv())
            + " | medianClv=" + valueOrUnavailable(summary.medianClv())
            + " | positiveClv=" + percentOrUnavailable(summary.positiveClvPercent());
    }

    private String formatClvBreakdown(BacktestClvBreakdownReport breakdown) {
        return "CLV_BREAKDOWN | strategy=value-football-draw-only"
            + " | kind=" + breakdown.kind()
            + " | name=" + breakdown.name()
            + " | trades=" + breakdown.trades()
            + " | averageClv=" + value(breakdown.averageClv())
            + " | medianClv=" + value(breakdown.medianClv())
            + " | positiveClv=" + twoDecimal(breakdown.positiveClvPercent()) + "%";
    }

    private String formatPaperValidation(BacktestPaperValidationReport validation) {
        return "PAPER_VALIDATION | strategy=value-football-draw-only"
            + " | status=" + validation.status()
            + " | clvStatus=" + validation.clvStatus()
            + " | settledTrades=" + validation.settledTrades()
            + " | medianClv=" + valueOrUnavailable(validation.medianClv())
            + " | theoreticalRoi=" + twoDecimal(validation.theoreticalRoiPercent()) + "%"
            + " | executableRoi=" + twoDecimal(validation.executableRoiPercent()) + "%"
            + " | closingOddsRoi=" + twoDecimal(validation.closingOddsRoiPercent()) + "%"
            + " | executionLoss=" + twoDecimal(validation.executionLossPercentagePoints()) + "pp";
    }

    private String formatRollingPaperWindow(BacktestRollingPaperWindow window) {
        return "ROLLING_PAPER | strategy=value-football-draw-only"
            + " | window=" + window.windowSize()
            + " | trades=" + window.trades()
            + " | roi=" + twoDecimal(window.roiPercent()) + "%"
            + " | averageClv=" + valueOrUnavailable(window.averageClv())
            + " | maxDrawdown=" + twoDecimal(window.maxDrawdown())
            + " | longestLosingStreak=" + window.longestLosingStreak();
    }

    private String formatLeakageDiagnostics(BacktestLeakageDiagnostics diagnostics) {
        return "LEAKAGE | rowsIgnoredAtOrAfterMarketStart=" + diagnostics.rowsIgnoredAtOrAfterMarketStart()
            + " | duplicateRunnerRowsIgnored=" + diagnostics.duplicateRunnerRowsIgnored();
    }

    private String formatAnalyzerDiagnostic(BacktestAnalyzerDiagnostic diagnostic) {
        return "ANALYZER_DIAGNOSTIC | strategy=" + diagnostic.strategyId()
            + " | oddsSource=" + diagnostic.oddsSource()
            + " | reason=" + diagnostic.reason()
            + " | count=" + diagnostic.count();
    }

    private String formatUncertainty(BacktestStrategyUncertaintyReport uncertainty) {
        return "UNCERTAINTY | strategy=" + uncertainty.strategyId()
            + " | bootstrapNetRoi95=" + twoDecimal(uncertainty.bootstrapNetRoiLower95())
            + ".." + twoDecimal(uncertainty.bootstrapNetRoiUpper95()) + "%"
            + " | longestLosingStreak=" + uncertainty.longestLosingStreak()
            + " | profitFactor=" + twoDecimal(uncertainty.profitFactor())
            + " | averageOdds=" + twoDecimal(uncertainty.averageOdds())
            + " | expectedValuePerTrade=" + twoDecimal(uncertainty.expectedValuePerTrade());
    }

    private String flag(BacktestResult result) {
        return result.roiPercent().compareTo(new BigDecimal("20.00")) > 0 && result.trades().size() < 100
            ? " | flag=LOW SAMPLE SIZE"
            : "";
    }

    private String flag(BacktestSegment segment) {
        return segment.roiPercent().compareTo(new BigDecimal("20.00")) > 0 && segment.trades() < 100
            ? " | flag=LOW SAMPLE SIZE"
            : "";
    }

    private String displayName(BacktestSegmentType type) {
        return switch (type) {
            case ODDS_BAND -> "odds band";
            case RUNNER_TYPE -> "runner type";
            case COMPETITION -> "competition";
            case CONFIDENCE -> "confidence";
            case ODDS_MOVEMENT -> "odds movement";
        };
    }

    private String label(BacktestSegmentType type) {
        return switch (type) {
            case ODDS_BAND -> "odds_band";
            case RUNNER_TYPE -> "runner_type";
            case COMPETITION -> "competition";
            case CONFIDENCE -> "confidence";
            case ODDS_MOVEMENT -> "odds_movement";
        };
    }

    private String value(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String valueOrUnavailable(BigDecimal value) {
        return value == null ? "n/a" : value(value);
    }

    private String percentOrUnavailable(BigDecimal value) {
        return value == null ? "n/a" : twoDecimal(value) + "%";
    }

    private String twoDecimal(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString();
    }

    private String threshold(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).stripTrailingZeros().toPlainString();
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
