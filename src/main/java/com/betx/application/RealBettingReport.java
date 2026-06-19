package com.betx.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Calculated read-only report for real betting performance. */
public record RealBettingReport(
    Instant periodStart,
    Instant periodEnd,
    String periodLabel,
    long settledBets,
    long openBets,
    long wins,
    long losses,
    long voidsCancelled,
    BigDecimal totalStaked,
    BigDecimal openExposure,
    BigDecimal netRealizedPnl,
    BigDecimal roiPercent,
    BigDecimal winRatePercent,
    BigDecimal averageExecutedOdds,
    BigDecimal operationalAvailableBalance,
    BigDecimal exchangeAvailableBalance,
    BigDecimal realizedEquity,
    BigDecimal peakRealizedEquity,
    BigDecimal maximumDrawdown,
    BigDecimal currentDrawdown,
    BigDecimal initialReferenceBalance,
    boolean realizedEquityUsesInitialReference,
    int maxWinningStreak,
    int maxLosingStreak,
    List<RealBettingReportSegment> selectionSideSegments,
    List<RealBettingReportSegment> runnerSegments,
    List<RealBettingReportSegment> competitionSegments,
    List<RealBettingReportSegment> strategySegments,
    List<RealBettingReportSegment> oddsBandSegments,
    List<RealBettingReportDailyPnl> dailyPnl,
    List<RealBettingReportRollingWindow> rollingWindows,
    List<RealBettingReportRow> rows,
    List<RealBettingReportWarning> warnings,
    List<String> limitations
) {
    public RealBettingReport {
        periodLabel = periodLabel == null || periodLabel.isBlank() ? "N/A" : periodLabel;
        totalStaked = zeroIfNull(totalStaked);
        openExposure = zeroIfNull(openExposure);
        netRealizedPnl = zeroIfNull(netRealizedPnl);
        roiPercent = zeroIfNull(roiPercent);
        winRatePercent = zeroIfNull(winRatePercent);
        averageExecutedOdds = zeroIfNull(averageExecutedOdds);
        realizedEquity = zeroIfNull(realizedEquity);
        peakRealizedEquity = zeroIfNull(peakRealizedEquity);
        maximumDrawdown = zeroIfNull(maximumDrawdown);
        currentDrawdown = zeroIfNull(currentDrawdown);
        selectionSideSegments = selectionSideSegments == null ? List.of() : List.copyOf(selectionSideSegments);
        runnerSegments = runnerSegments == null ? List.of() : List.copyOf(runnerSegments);
        competitionSegments = competitionSegments == null ? List.of() : List.copyOf(competitionSegments);
        strategySegments = strategySegments == null ? List.of() : List.copyOf(strategySegments);
        oddsBandSegments = oddsBandSegments == null ? List.of() : List.copyOf(oddsBandSegments);
        dailyPnl = dailyPnl == null ? List.of() : List.copyOf(dailyPnl);
        rollingWindows = rollingWindows == null ? List.of() : List.copyOf(rollingWindows);
        rows = rows == null ? List.of() : List.copyOf(rows);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    public RealBettingReport withRows(List<RealBettingReportRow> newRows) {
        return new RealBettingReport(
            periodStart,
            periodEnd,
            periodLabel,
            settledBets,
            openBets,
            wins,
            losses,
            voidsCancelled,
            totalStaked,
            openExposure,
            netRealizedPnl,
            roiPercent,
            winRatePercent,
            averageExecutedOdds,
            operationalAvailableBalance,
            exchangeAvailableBalance,
            realizedEquity,
            peakRealizedEquity,
            maximumDrawdown,
            currentDrawdown,
            initialReferenceBalance,
            realizedEquityUsesInitialReference,
            maxWinningStreak,
            maxLosingStreak,
            selectionSideSegments,
            runnerSegments,
            competitionSegments,
            strategySegments,
            oddsBandSegments,
            dailyPnl,
            rollingWindows,
            newRows,
            warnings,
            limitations
        );
    }

    public static RealBettingReport empty() {
        return new RealBettingReport(
            null,
            null,
            "N/A",
            0,
            0,
            0,
            0,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            false,
            0,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new RealBettingReportWarning("Small sample: results are not statistically reliable yet.")),
            List.of("No real betting rows were found.")
        );
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
