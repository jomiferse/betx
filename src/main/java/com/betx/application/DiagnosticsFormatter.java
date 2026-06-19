package com.betx.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DiagnosticsFormatter {
    public List<String> format(DiagnosticsReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("BETX DIAGNOSTICS");
        lines.add("Period: " + report.period().label());
        lines.add("");
        lines.add("Data coverage");
        lines.add(line("Real bets", report.coverage().realBets()));
        lines.add(line("Paper trades", report.coverage().paperTrades()));
        lines.add(line("Matched pairs", report.coverage().matchedPairs()));
        lines.add(line("Real-only", report.coverage().realOnly()));
        lines.add(line("Paper-only", report.coverage().paperOnly()));
        lines.add(line("Ambiguous", report.coverage().ambiguous()));
        lines.add("");
        lines.add("Execution");
        lines.add(line("Orders submitted", report.executionMetrics().ordersSubmitted()));
        lines.add(line("Fully matched/recorded", report.executionMetrics().fullyMatched()));
        lines.add(line("Rejected", report.executionMetrics().rejected()));
        lines.add(line("Cancelled", report.executionMetrics().cancelled()));
        lines.add(line("Average execution latency", duration(report.executionMetrics().averageExecutionLatency())));
        lines.add(line("Median execution latency", duration(report.executionMetrics().medianExecutionLatency())));
        lines.add(line("P95 execution latency", duration(report.executionMetrics().p95ExecutionLatency())));
        lines.add(line("Latency provenance", report.executionMetrics().latencyProvenance()));
        lines.add(line("Average real recorded vs paper odds difference", number(report.executionMetrics().averageRealRecordedVsPaperOddsDifference())));
        lines.add(line("Odds provenance", report.executionMetrics().oddsProvenance()));
        lines.add(line("Missing recorded odds", report.executionMetrics().missingRecordedOdds()));
        lines.add(line("Missing exchange order id", report.executionMetrics().missingExchangeOrderId()));
        lines.add("");
        lines.add("Paper vs real");
        lines.add(line("Settled matched pairs", report.paperVsRealMetrics().settledMatchedPairs()));
        lines.add(line("Average real vs paper odds difference", number(report.paperVsRealMetrics().averageRealVsPaperOddsDifference())));
        lines.add(line("Median real vs paper odds difference", number(report.paperVsRealMetrics().medianRealVsPaperOddsDifference())));
        lines.add(line("Average slippage", number(report.paperVsRealMetrics().averageSlippage())));
        lines.add(line("Median slippage", number(report.paperVsRealMetrics().medianSlippage())));
        lines.add(line("Matched paper PnL", money(report.paperVsRealMetrics().matchedPaperPnl())));
        lines.add(line("Matched real PnL", money(report.paperVsRealMetrics().matchedRealPnl())));
        lines.add(line("Execution PnL difference", money(report.paperVsRealMetrics().executionPnlDifference())));
        lines.add(line("Paper PnL per unit stake", number(report.paperVsRealMetrics().paperPnlPerUnitStake())));
        lines.add(line("Real PnL per unit stake", number(report.paperVsRealMetrics().realPnlPerUnitStake())));
        lines.add(line("Paper ROI", number(report.paperVsRealMetrics().paperRoi())));
        lines.add(line("Real ROI", number(report.paperVsRealMetrics().realRoi())));
        lines.add(line("Normalized execution difference", number(report.paperVsRealMetrics().normalizedExecutionDifference())));
        lines.add(line("Result mismatches", report.paperVsRealMetrics().resultMismatches()));
        lines.add("");
        lines.add("Decision funnel");
        lines.add(line("Markets scanned", report.decisionFunnel().marketsScanned()));
        lines.add(line("Runners analyzed", report.decisionFunnel().runnersAnalyzed()));
        lines.add(line("Recommendations generated", report.decisionFunnel().recommendationsGenerated()));
        lines.add(line("Strategy rejections", report.decisionFunnel().strategyRejections()));
        lines.add(line("Risk rejections", report.decisionFunnel().riskRejections()));
        lines.add(line("Confirmation requests", report.decisionFunnel().confirmationRequests()));
        lines.add(line("Orders submitted", report.decisionFunnel().ordersSubmitted()));
        lines.add(line("Orders matched", report.decisionFunnel().ordersMatched()));
        lines.add(line("Orders rejected", report.decisionFunnel().ordersRejected()));
        lines.add(line("Bets settled", report.decisionFunnel().betsSettled()));
        lines.add("");
        lines.add("Integrity");
        long warnings = report.integrityFindings().stream().filter(finding -> finding.severity().name().equals("WARNING")).count();
        long errors = report.integrityFindings().stream().filter(finding -> finding.severity().name().equals("ERROR")).count();
        lines.add(line("Warnings", warnings));
        lines.add(line("Errors", errors));
        report.integrityFindings().forEach(finding -> lines.add("- " + finding.severity() + " " + finding.code()
            + " (" + finding.observations() + "): " + finding.message()));
        lines.add("");
        lines.add("Top findings");
        report.topFindings().forEach(finding -> lines.add("- " + finding));
        lines.add("");
        lines.add("Limitations");
        report.limitations().forEach(limitation -> lines.add("- " + limitation));
        return lines;
    }

    private static String line(String label, Object value) {
        return String.format("%-46s %s", label + ":", value == null ? "N/A" : value);
    }

    private static String number(BigDecimal value) {
        return value == null ? "N/A" : value.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal value) {
        return value == null ? "N/A" : value.toPlainString();
    }

    private static String duration(Duration value) {
        if (value == null) {
            return "N/A";
        }
        return value.toMillis() + " ms";
    }
}
