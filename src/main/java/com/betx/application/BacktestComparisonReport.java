package com.betx.application;

import java.util.List;

/** Ranked strategy comparison plus league breakdowns for a historical research run. */
public record BacktestComparisonReport(
    long randomSeed,
    java.math.BigDecimal commissionRate,
    java.math.BigDecimal oddsSlippageRate,
    BacktestSlippageModel slippageModel,
    String pricingMode,
    String oddsSource,
    BacktestDatasetCapability datasetCapability,
    List<BacktestStrategyReport> strategyReports,
    List<BacktestStrategyLeagueReport> leagueReports,
    List<BacktestMarketSelectionReport> marketSelectionReports,
    List<BacktestBreakdownReport> breakdownReports,
    List<BacktestSeasonReport> seasonReports,
    List<BacktestSeasonSummary> seasonSummaries,
    List<BacktestOutOfSampleReport> outOfSampleReports,
    BacktestLeakageDiagnostics leakageDiagnostics,
    List<BacktestAnalyzerDiagnostic> analyzerDiagnostics,
    List<BacktestStrategyUncertaintyReport> uncertaintyReports,
    List<BacktestSlippageReport> slippageReports,
    List<BacktestDrawOnlySeasonLeagueReport> drawOnlySeasonLeagueReports,
    List<BacktestMovementReport> movementReports,
    List<BacktestEquityCurveRow> equityCurveRows,
    List<BacktestPaperTrade> paperTrades,
    BacktestClvSummary clvSummary,
    List<BacktestClvBreakdownReport> clvBreakdowns,
    List<BacktestRollingPaperWindow> rollingPaperWindows
) {
    private static final int DEFAULT_MINIMUM_SETTLED_PAPER_TRADES = 300;

    public BacktestComparisonReport {
        commissionRate = commissionRate == null ? java.math.BigDecimal.ZERO : commissionRate;
        oddsSlippageRate = oddsSlippageRate == null ? java.math.BigDecimal.ZERO : oddsSlippageRate;
        slippageModel = slippageModel == null ? BacktestSlippageModel.PROFIT_HAIRCUT : slippageModel;
        pricingMode = pricingMode == null || pricingMode.isBlank() ? "exchange" : pricingMode;
        oddsSource = oddsSource == null || oddsSource.isBlank() ? "unknown" : oddsSource;
        datasetCapability = datasetCapability == null ? BacktestDatasetCapability.SINGLE_PRICE : datasetCapability;
        strategyReports = strategyReports == null ? List.of() : List.copyOf(strategyReports);
        leagueReports = leagueReports == null ? List.of() : List.copyOf(leagueReports);
        marketSelectionReports = marketSelectionReports == null ? List.of() : List.copyOf(marketSelectionReports);
        breakdownReports = breakdownReports == null ? List.of() : List.copyOf(breakdownReports);
        seasonReports = seasonReports == null ? List.of() : List.copyOf(seasonReports);
        seasonSummaries = seasonSummaries == null ? List.of() : List.copyOf(seasonSummaries);
        outOfSampleReports = outOfSampleReports == null ? List.of() : List.copyOf(outOfSampleReports);
        leakageDiagnostics = leakageDiagnostics == null ? new BacktestLeakageDiagnostics(0, 0) : leakageDiagnostics;
        analyzerDiagnostics = analyzerDiagnostics == null ? List.of() : List.copyOf(analyzerDiagnostics);
        uncertaintyReports = uncertaintyReports == null ? List.of() : List.copyOf(uncertaintyReports);
        slippageReports = slippageReports == null ? List.of() : List.copyOf(slippageReports);
        drawOnlySeasonLeagueReports = drawOnlySeasonLeagueReports == null ? List.of() : List.copyOf(drawOnlySeasonLeagueReports);
        movementReports = movementReports == null ? List.of() : List.copyOf(movementReports);
        equityCurveRows = equityCurveRows == null ? List.of() : List.copyOf(equityCurveRows);
        paperTrades = paperTrades == null ? List.of() : List.copyOf(paperTrades);
        clvSummary = clvSummary == null ? BacktestClvSummary.unavailable(paperTrades) : clvSummary;
        clvBreakdowns = clvBreakdowns == null ? List.of() : List.copyOf(clvBreakdowns);
        rollingPaperWindows = rollingPaperWindows == null ? List.of() : List.copyOf(rollingPaperWindows);
    }

    public BacktestPaperValidationReport paperValidation() {
        return paperValidation(java.math.BigDecimal.valueOf(DEFAULT_MINIMUM_SETTLED_PAPER_TRADES));
    }

    public BacktestPaperValidationReport paperValidation(java.math.BigDecimal minimumSettledTrades) {
        int minimum = minimumSettledTrades == null
            ? DEFAULT_MINIMUM_SETTLED_PAPER_TRADES
            : minimumSettledTrades.intValue();
        List<BacktestPaperTrade> settledPaperTrades = paperTrades.stream()
            .filter(trade -> trade.result() != null)
            .toList();
        java.math.BigDecimal theoreticalRoi = roi(settledPaperTrades.stream()
            .map(trade -> profitLoss(trade.result(), trade.requestedOdds(), java.math.BigDecimal.valueOf(5)))
            .toList());
        java.math.BigDecimal executableRoi = roi(settledPaperTrades.stream()
            .map(BacktestPaperTrade::netPnl)
            .toList());
        java.math.BigDecimal closingRoi = roi(settledPaperTrades.stream()
            .filter(trade -> trade.closingOdds() != null)
            .map(trade -> profitLoss(trade.result(), trade.closingOdds(), java.math.BigDecimal.valueOf(5)))
            .toList());
        BacktestPaperValidationStatus status;
        if (clvSummary.status() == BacktestClvStatus.INSUFFICIENT_DATA) {
            status = BacktestPaperValidationStatus.INSUFFICIENT_DATA;
        } else if (settledPaperTrades.size() < minimum) {
            status = BacktestPaperValidationStatus.INSUFFICIENT_SAMPLE;
        } else if (clvSummary.status() == BacktestClvStatus.VALID_PROSPECTIVE) {
            if (theoreticalRoi.compareTo(java.math.BigDecimal.ZERO) > 0
                && executableRoi.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                status = BacktestPaperValidationStatus.EXECUTION_FAILURE;
            } else if (clvSummary.medianClv().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                status = BacktestPaperValidationStatus.WEAK_EVIDENCE;
            } else {
                status = BacktestPaperValidationStatus.CANDIDATE_EDGE;
            }
        } else if (executableRoi.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            status = BacktestPaperValidationStatus.NEGATIVE_EXECUTABLE_ROI;
        } else if (threePercentSlippageRoi().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            status = BacktestPaperValidationStatus.FRAGILE_EDGE;
        } else {
            status = BacktestPaperValidationStatus.HISTORICAL_CANDIDATE;
        }
        return new BacktestPaperValidationReport(
            status,
            clvSummary.status(),
            settledPaperTrades.size(),
            clvSummary.medianClv(),
            theoreticalRoi,
            executableRoi,
            closingRoi,
            theoreticalRoi.subtract(executableRoi)
        );
    }

    public BacktestComparisonReport(
        long randomSeed,
        java.math.BigDecimal commissionRate,
        List<BacktestStrategyReport> strategyReports,
        List<BacktestStrategyLeagueReport> leagueReports
    ) {
        this(
            randomSeed,
            commissionRate,
            java.math.BigDecimal.ZERO,
            BacktestSlippageModel.PROFIT_HAIRCUT,
            "exchange",
            "unknown",
            BacktestDatasetCapability.EXCHANGE_SNAPSHOTS,
            strategyReports,
            leagueReports,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new BacktestLeakageDiagnostics(0, 0),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            BacktestClvSummary.unavailable(List.of()),
            List.of(),
            List.of()
        );
    }

    public BacktestComparisonReport(
        long randomSeed,
        List<BacktestStrategyReport> strategyReports,
        List<BacktestStrategyLeagueReport> leagueReports
    ) {
        this(randomSeed, java.math.BigDecimal.ZERO, strategyReports, leagueReports);
    }

    private static java.math.BigDecimal roi(List<java.math.BigDecimal> pnls) {
        if (pnls.isEmpty()) {
            return java.math.BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        java.math.BigDecimal total = pnls.stream().reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal stake = java.math.BigDecimal.valueOf(5L * pnls.size());
        return total.divide(stake, 10, java.math.RoundingMode.HALF_UP)
            .multiply(java.math.BigDecimal.valueOf(100))
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static java.math.BigDecimal profitLoss(
        BacktestOutcome outcome,
        java.math.BigDecimal odds,
        java.math.BigDecimal stake
    ) {
        return outcome == BacktestOutcome.WIN
            ? stake.multiply(odds.subtract(java.math.BigDecimal.ONE))
            : stake.negate();
    }

    private java.math.BigDecimal threePercentSlippageRoi() {
        return slippageReports.stream()
            .filter(report -> "value-football-draw-only".equals(report.strategyId()))
            .filter(report -> report.slippageRate().compareTo(new java.math.BigDecimal("0.03")) == 0)
            .findFirst()
            .map(BacktestSlippageReport::netRoiPercent)
            .orElse(java.math.BigDecimal.ZERO);
    }
}
