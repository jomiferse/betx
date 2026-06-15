package com.betx.application;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exports deterministic strategy comparison reports to CSV. */
public class BacktestComparisonCsvExporter {
    private static final List<String> HEADER = List.of(
        "section",
        "rank",
        "strategy",
        "league",
        "season",
        "period",
        "odds_source",
        "pricing_mode",
        "dataset_capability",
        "markets",
        "trades",
        "wins",
        "losses",
        "gross_roi",
        "net_roi",
        "gross_pnl",
        "commission",
        "net_pnl",
        "gross_drawdown",
        "net_drawdown",
        "strike_rate",
        "random_seed",
        "commission_rate",
        "odds_slippage_rate",
        "slippage_model"
    );

    public List<String> lines(BacktestComparisonReport report) {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADER));
        for (BacktestStrategyReport strategy : report.strategyReports()) {
            lines.add(line(
                "strategy",
                Integer.toString(strategy.rank()),
                strategy.strategyId(),
                "",
                "",
                "",
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                Integer.toString(strategy.marketResults().size()),
                Integer.toString(strategy.result().trades().size()),
                Integer.toString(strategy.result().wins()),
                Integer.toString(strategy.result().losses()),
                twoDecimal(strategy.result().roiPercent()),
                twoDecimal(strategy.netRoiPercent()),
                twoDecimal(strategy.grossProfitLoss()),
                twoDecimal(strategy.commissionPaid()),
                twoDecimal(strategy.netProfitLoss()),
                twoDecimal(strategy.result().maxDrawdown()),
                twoDecimal(strategy.result().maxDrawdown()),
                twoDecimal(strategy.result().strikeRatePercent()),
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestStrategyLeagueReport league : report.leagueReports()) {
            lines.add(line(
                "league",
                "",
                league.strategyId(),
                league.competitionName(),
                "",
                "",
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                Integer.toString(league.marketResults().size()),
                Integer.toString(league.result().trades().size()),
                Integer.toString(league.result().wins()),
                Integer.toString(league.result().losses()),
                twoDecimal(league.result().roiPercent()),
                twoDecimal(league.netRoiPercent()),
                twoDecimal(league.grossProfitLoss()),
                twoDecimal(league.commissionPaid()),
                twoDecimal(league.netProfitLoss()),
                twoDecimal(league.result().maxDrawdown()),
                twoDecimal(league.result().maxDrawdown()),
                twoDecimal(league.result().strikeRatePercent()),
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestMarketSelectionReport selection : report.marketSelectionReports()) {
            lines.add(line(
                "market_selection",
                "",
                selection.strategyId(),
                "selected_" + selection.selectedRunners(),
                "",
                "",
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                Integer.toString(selection.markets()),
                "",
                "",
                "",
                "",
                twoDecimal(selection.netRoiPercent()),
                twoDecimal(selection.grossPnl()),
                twoDecimal(selection.commissionPaid()),
                twoDecimal(selection.netPnl()),
                twoDecimal(selection.maximumExposure()),
                "",
                "",
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestBreakdownReport breakdown : report.breakdownReports()) {
            lines.add(line(
                "breakdown",
                "",
                breakdown.strategyId(),
                breakdown.kind() + ":" + breakdown.league() + ":" + breakdown.runnerType() + ":" + breakdown.oddsBand(),
                "",
                "",
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                "",
                Integer.toString(breakdown.result().trades().size()),
                Integer.toString(breakdown.result().wins()),
                Integer.toString(breakdown.result().losses()),
                twoDecimal(breakdown.result().roiPercent()),
                twoDecimal(breakdown.result().roiPercent()),
                twoDecimal(breakdown.result().profitLoss()),
                "0.00",
                twoDecimal(breakdown.result().profitLoss()),
                twoDecimal(breakdown.result().maxDrawdown()),
                twoDecimal(breakdown.result().maxDrawdown()),
                twoDecimal(breakdown.result().strikeRatePercent()),
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestSeasonReport season : report.seasonReports()) {
            lines.add(line(
                "season",
                "",
                season.strategyId(),
                "",
                season.season(),
                "",
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                Integer.toString(season.markets()),
                Integer.toString(season.trades()),
                Integer.toString(season.result().wins()),
                Integer.toString(season.result().losses()),
                twoDecimal(season.grossRoiPercent()),
                twoDecimal(season.netRoiPercent()),
                twoDecimal(season.grossProfitLoss()),
                twoDecimal(season.commissionPaid()),
                twoDecimal(season.netProfitLoss()),
                twoDecimal(season.grossMaxDrawdown()),
                twoDecimal(season.netMaxDrawdown()),
                twoDecimal(season.strikeRatePercent()),
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestDrawOnlySeasonLeagueReport season : report.drawOnlySeasonLeagueReports()) {
            lines.add(line(
                "draw_only_season_league",
                "",
                "value-football-draw-only",
                season.league(),
                season.season(),
                "",
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                "",
                Integer.toString(season.trades()),
                Integer.toString(season.wins()),
                Integer.toString(season.trades() - season.wins()),
                twoDecimal(season.roiPercent()),
                twoDecimal(season.roiPercent()),
                twoDecimal(season.grossProfitLoss()),
                "0.00",
                twoDecimal(season.grossProfitLoss()),
                twoDecimal(season.maxDrawdown()),
                twoDecimal(season.maxDrawdown()),
                Integer.toString(season.longestLosingStreak()),
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestSeasonSummary summary : report.seasonSummaries()) {
            lines.add(line(
                "season_summary",
                "",
                summary.strategyId(),
                "",
                "",
                "",
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                Integer.toString(summary.evaluatedSeasons()),
                "",
                Integer.toString(summary.profitableSeasons()),
                Integer.toString(summary.losingSeasons()),
                twoDecimal(summary.meanNetRoiPercent()),
                twoDecimal(summary.medianNetRoiPercent()),
                "",
                "",
                twoDecimal(summary.totalNetRoiPercent()),
                twoDecimal(summary.worstNetRoiPercent()),
                twoDecimal(summary.bestNetRoiPercent()),
                "",
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestOutOfSampleReport period : report.outOfSampleReports()) {
            lines.add(line(
                "out_of_sample",
                "",
                period.strategyId(),
                "",
                period.startSeason() + "-" + period.endSeason(),
                period.period(),
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                Integer.toString(period.marketResults().size()),
                Integer.toString(period.result().trades().size()),
                Integer.toString(period.result().wins()),
                Integer.toString(period.result().losses()),
                twoDecimal(period.result().roiPercent()),
                twoDecimal(netRoi(period.marketResults())),
                twoDecimal(period.result().profitLoss()),
                "0.00",
                twoDecimal(period.result().profitLoss()),
                twoDecimal(period.result().maxDrawdown()),
                twoDecimal(period.result().maxDrawdown()),
                twoDecimal(period.result().strikeRatePercent()),
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestSlippageReport scenario : report.slippageReports()) {
            lines.add(line(
                "slippage",
                "",
                scenario.strategyId(),
                "",
                "",
                "",
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                "",
                Integer.toString(scenario.trades()),
                "",
                "",
                twoDecimal(scenario.netRoiPercent()),
                twoDecimal(scenario.netRoiPercent()),
                twoDecimal(scenario.grossPnl()),
                "",
                twoDecimal(scenario.netPnl()),
                "",
                "",
                value(scenario.slippageRate()),
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestMovementReport movement : report.movementReports()) {
            lines.add(line(
                "movement",
                "",
                movement.strategyId(),
                movement.movementBucket(),
                "",
                "",
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                "",
                Integer.toString(movement.trades()),
                "",
                "",
                twoDecimal(movement.roiPercent()),
                twoDecimal(movement.roiPercent()),
                twoDecimal(movement.pnl()),
                "",
                twoDecimal(movement.pnl()),
                "",
                "",
                "",
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestStrategyUncertaintyReport uncertainty : report.uncertaintyReports()) {
            lines.add(line(
                "uncertainty",
                "",
                uncertainty.strategyId(),
                "",
                "",
                "bootstrap_95",
                report.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                "",
                Integer.toString(uncertainty.longestLosingStreak()),
                "",
                "",
                twoDecimal(uncertainty.bootstrapNetRoiLower95()),
                twoDecimal(uncertainty.bootstrapNetRoiUpper95()),
                twoDecimal(uncertainty.profitFactor()),
                "",
                twoDecimal(uncertainty.expectedValuePerTrade()),
                "",
                "",
                twoDecimal(uncertainty.averageOdds()),
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        for (BacktestAnalyzerDiagnostic diagnostic : report.analyzerDiagnostics()) {
            lines.add(line(
                "analyzer_diagnostic",
                "",
                diagnostic.strategyId(),
                diagnostic.reason(),
                "",
                "",
                diagnostic.oddsSource(),
                report.pricingMode(),
                report.datasetCapability().name(),
                "",
                Integer.toString(diagnostic.count()),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                Long.toString(report.randomSeed()),
                value(report.commissionRate()),
                value(report.oddsSlippageRate()),
                report.slippageModel().name()
            ));
        }
        return lines;
    }

    public void write(Path outputPath, BacktestComparisonReport report) {
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(outputPath, lines(report), StandardCharsets.UTF_8);
        } catch (IOException exc) {
            throw new IllegalStateException("Could not export backtest comparison CSV: " + outputPath, exc);
        }
    }

    private String line(String... values) {
        return java.util.Arrays.stream(values)
            .map(this::csv)
            .collect(java.util.stream.Collectors.joining(","));
    }

    private BigDecimal netRoi(List<BacktestMarketResult> markets) {
        BigDecimal stake = markets.stream().map(BacktestMarketResult::totalStake).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netPnl = markets.stream().map(BacktestMarketResult::netPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (stake.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return netPnl.divide(stake, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private String twoDecimal(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString();
    }

    private String value(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
