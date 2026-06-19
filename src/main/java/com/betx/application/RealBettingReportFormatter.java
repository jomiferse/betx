package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Formats real betting reports for terminal output. */
public class RealBettingReportFormatter {
    public List<String> format(RealBettingReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("REAL BETTING REPORT");
        lines.add("Period: " + report.periodLabel());
        lines.add("");
        lines.add("Summary");
        lines.add(row("Settled bets", Long.toString(report.settledBets())));
        lines.add(row("Open bets", Long.toString(report.openBets())));
        lines.add(row("Wins", Long.toString(report.wins())));
        lines.add(row("Losses", Long.toString(report.losses())));
        lines.add(row("Voids/cancelled", Long.toString(report.voidsCancelled())));
        lines.add(row("Win rate", percent(report.winRatePercent())));
        lines.add(row("Total staked / turnover", moneyMagnitude(report.totalStaked())));
        lines.add(row("Open exposure", moneyMagnitude(report.openExposure())));
        lines.add(row("Net realized PnL", moneySigned(report.netRealizedPnl())));
        lines.add(row("ROI", percent(report.roiPercent())));
        lines.add(row("Average executed odds", decimal(report.averageExecutedOdds())));
        lines.add(row("Operational available balance", optionalMoney(report.operationalAvailableBalance())));
        lines.add(row("Exchange available balance", optionalMoney(report.exchangeAvailableBalance())));
        if (report.realizedEquityUsesInitialReference()) {
            lines.add(row("Realized equity", moneySigned(report.realizedEquity())));
            lines.add(row("Peak realized equity", moneySigned(report.peakRealizedEquity())));
        } else {
            lines.add(row("Cumulative realized PnL", moneySigned(report.realizedEquity())));
            lines.add(row("Peak cumulative realized PnL", moneySigned(report.peakRealizedEquity())));
        }
        lines.add(row("Maximum drawdown", moneyMagnitude(report.maximumDrawdown())));
        lines.add(row("Current drawdown from peak", moneyMagnitude(report.currentDrawdown())));
        lines.add(row("Maximum winning streak", Integer.toString(report.maxWinningStreak())));
        lines.add(row("Maximum losing streak", Integer.toString(report.maxLosingStreak())));
        lines.add("");
        addWarnings(lines, report);
        addLimitations(lines, report);
        addRollingWindows(lines, report);
        addSegments(lines, "By selection side", report.selectionSideSegments());
        addSegments(lines, "By selection / runner", report.runnerSegments());
        addSegments(lines, "By competition", report.competitionSegments());
        addSegments(lines, "By strategy", report.strategySegments());
        addSegments(lines, "By executed odds range", report.oddsBandSegments());
        addDaily(lines, report);
        return lines;
    }

    private static void addWarnings(List<String> lines, RealBettingReport report) {
        if (report.warnings().isEmpty()) {
            return;
        }
        lines.add("Warnings");
        report.warnings().forEach(warning -> lines.add("- " + warning.message()));
        lines.add("");
    }

    private static void addLimitations(List<String> lines, RealBettingReport report) {
        if (report.limitations().isEmpty()) {
            return;
        }
        lines.add("Limitations");
        report.limitations().forEach(limitation -> lines.add("- " + limitation));
        lines.add("");
    }

    private static void addSegments(List<String> lines, String title, List<RealBettingReportSegment> segments) {
        lines.add(title);
        if (segments.isEmpty()) {
            lines.add("  N/A");
            lines.add("");
            return;
        }
        lines.add(String.format("  %-24s %8s %8s %12s %10s", "Name", "Settled", "WinRate", "PnL", "ROI"));
        for (RealBettingReportSegment segment : segments) {
            lines.add(String.format(
                "  %-24s %8d %8s %12s %10s",
                truncate(segment.name(), 24),
                segment.settledBets(),
                percent(segment.winRatePercent()),
                moneySigned(segment.netRealizedPnl()),
                percent(segment.roiPercent())
            ));
        }
        lines.add("");
    }

    private static void addRollingWindows(List<String> lines, RealBettingReport report) {
        lines.add("Rolling windows");
        if (report.rollingWindows().isEmpty()) {
            lines.add("  N/A");
            lines.add("");
            return;
        }
        lines.add(String.format("  %-38s %8s %8s %12s %12s %10s %12s %8s %8s", "Window", "Settled", "WinRate", "Turnover", "PnL", "ROI", "Drawdown", "WinStr", "LossStr"));
        for (RealBettingReportRollingWindow window : report.rollingWindows()) {
            String name = "Last " + window.requestedSize() + " settled bets";
            if (window.availableSettledBets() < window.requestedSize()) {
                name = name + ": " + window.availableSettledBets() + " available";
            }
            lines.add(String.format(
                "  %-38s %8d %8s %12s %12s %10s %12s %8d %8d",
                truncate(name, 38),
                window.availableSettledBets(),
                percent(window.winRatePercent()),
                moneyMagnitude(window.totalStaked()),
                moneySigned(window.netRealizedPnl()),
                percent(window.roiPercent()),
                moneyMagnitude(window.maximumDrawdown()),
                window.maxWinningStreak(),
                window.maxLosingStreak()
            ));
        }
        lines.add("");
    }

    private static void addDaily(List<String> lines, RealBettingReport report) {
        lines.add("By day");
        if (report.dailyPnl().isEmpty()) {
            lines.add("  N/A");
            return;
        }
        lines.add(String.format("  %-12s %8s %12s", "Day", "Settled", "PnL"));
        for (RealBettingReportDailyPnl day : report.dailyPnl()) {
            lines.add(String.format(
                "  %-12s %8d %12s",
                day.day(),
                day.settledBets(),
                moneySigned(day.pnl())
            ));
        }
    }

    private static String row(String label, String value) {
        return String.format("%-32s %s", label + ":", value);
    }

    private static String moneySigned(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        String sign = safe.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + safe.setScale(2, RoundingMode.HALF_UP).toPlainString() + " €";
    }

    private static String moneyMagnitude(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.abs().setScale(2, RoundingMode.HALF_UP).toPlainString() + " €";
    }

    private static String optionalMoney(BigDecimal value) {
        return value == null ? "N/A" : moneySigned(value);
    }

    private static String percent(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.setScale(2, RoundingMode.HALF_UP).toPlainString() + " %";
    }

    private static String decimal(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "N/A";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + ".";
    }
}
