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
        lines.add("Bet recommendations");
        DiagnosticsBetRecommendationsSummary recommendations = report.betRecommendations();
        lines.add(line("Total recommendations", recommendations.totalRecommendations()));
        lines.add(line("pre-2.2 shadow rows", recommendations.pre22ShadowRows()));
        lines.add(line("post-2.2 canonical rows", recommendations.post22CanonicalRows()));
        lines.add(line("Canonical active recommendations", recommendations.canonicalActiveRecommendations()));
        lines.add(line("Canonical covered recommendations", recommendations.canonicalCoveredRecommendations()));
        lines.add(line("Canonical expired recommendations", recommendations.canonicalExpiredRecommendations()));
        lines.add(line("Recommendation observations", recommendations.recommendationObservations()));
        lines.add(line("Average observed_count", formatDouble(recommendations.averageObservedCount())));
        lines.add(line("Median observed_count", formatDouble(recommendations.medianObservedCount())));
        lines.add(line("P95 observed_count", formatDouble(recommendations.p95ObservedCount())));
        lines.add(line("Duplicate canonical groups", recommendations.duplicateCanonicalGroups()));
        lines.add(line("Recommendations with evaluation_id", coverage(
            recommendations.withEvaluationId(),
            recommendations.totalRecommendations()
        )));
        lines.add(line("Recommendations with last_evaluation_id", coverage(
            recommendations.withLastEvaluationId(),
            recommendations.totalRecommendations()
        )));
        lines.add(line("Recommendations with strategy_name", coverage(
            recommendations.withStrategyName(),
            recommendations.totalRecommendations()
        )));
        lines.add(line("Recommendations with selection_side", coverage(
            recommendations.withSelectionSide(),
            recommendations.totalRecommendations()
        )));
        lines.add(line("Recommendations created in period", recommendations.createdInPeriod()));
        lines.add(line("Orphan recommendations", recommendations.orphanRecommendations()));
        addGrouped(lines, "Top recommendations by observed_count", recommendations.topByObservedCount());
        addGrouped(lines, "Recommendations by strategy", recommendations.byStrategy());
        addGrouped(lines, "Recommendations by selection side", recommendations.bySelectionSide());
        addGrouped(lines, "Recommendations by competition", recommendations.byCompetition());
        lines.add("");
        lines.add("Paper recommendation coverage");
        DiagnosticsPaperRecommendationCoverage paperCoverage = report.paperRecommendationCoverage();
        lines.add(line("Paper trades total", paperCoverage.paperTradesTotal()));
        lines.add(line("Paper trades with recommendation_id", coverage(
            paperCoverage.paperTradesWithRecommendationId(),
            paperCoverage.paperTradesTotal()
        )));
        lines.add(line("Paper trades without recommendation_id", paperCoverage.paperTradesWithoutRecommendationId()));
        lines.add(line("Post-2.3 paper trades", paperCoverage.post23PaperTrades()));
        lines.add(line("Post-2.3 paper trades with recommendation_id", coverage(
            paperCoverage.post23PaperTradesWithRecommendationId(),
            paperCoverage.post23PaperTrades()
        )));
        lines.add(line(
            "Paper trades with recommendation_id but missing BetRecommendation",
            paperCoverage.paperTradesWithRecommendationIdButMissingBetRecommendation()
        ));
        lines.add(line(
            "Paper trades linked to canonical recommendation",
            paperCoverage.paperTradesLinkedToCanonicalRecommendation()
        ));
        lines.add(line(
            "Paper trades linked to ACTIVE recommendations",
            paperCoverage.paperTradesLinkedToActiveRecommendations()
        ));
        lines.add(line(
            "Paper trades linked to COVERED recommendations",
            paperCoverage.paperTradesLinkedToCoveredRecommendations()
        ));
        lines.add(line(
            "Paper trades linked to EXPIRED recommendations",
            paperCoverage.paperTradesLinkedToExpiredRecommendations()
        ));
        lines.add(line("Real orders with recommendation_id", coverage(
            report.executionDataCoverage().withRecommendationId(),
            report.executionDataCoverage().totalOrders()
        )));
        lines.add(line("BetRecommendation consumed by paper", "yes"));
        lines.add(line("BetRecommendation consumed by real", report.executionDataCoverage().withRecommendationId() > 0 ? "yes" : "no"));
        lines.add(line("Matching by recommendation_id", "no"));
        lines.add("");
        lines.add("Matching gaps");
        if (report.matchingGaps().isEmpty()) {
            lines.add(line("None", 0));
        } else {
            report.matchingGaps().forEach((reason, count) -> lines.add(line(reason.name(), count)));
        }
        lines.add("");
        lines.add("Operational events observed in logs");
        lines.add(line("Source", "STRUCTURED_LOGS"));
        lines.add(line("order.submitted events", report.logEventCoverage().orderSubmittedEvents()));
        lines.add(line("order.response events", report.logEventCoverage().orderResponseEvents()));
        lines.add(line("legacy order.accepted events", report.logEventCoverage().orderAcceptedEvents()));
        lines.add(line("order.rejected events", report.logEventCoverage().orderRejectedEvents()));
        lines.add(line("order.unmatched events", report.logEventCoverage().orderUnmatchedEvents()));
        lines.add(line("order.partially_matched events", report.logEventCoverage().orderPartiallyMatchedEvents()));
        lines.add(line("order.matched events", report.logEventCoverage().orderMatchedEvents()));
        lines.add(line("order.settled events", report.logEventCoverage().orderSettledEvents()));
        lines.add("");
        lines.add("Duplicate prevention");
        lines.add(line("Early active-market skips", report.logEventCoverage().activeMarketSkips()));
        lines.add(line("Atomic duplicate blocks", report.logEventCoverage().atomicDuplicateBlocks()));
        if (!report.topSkippedMarkets().isEmpty()) {
            lines.add("Top skipped markets:");
            report.topSkippedMarkets().forEach(market -> lines.add("- "
                + text(market.eventName())
                + " / "
                + text(market.runnerName())
                + " / attempts "
                + market.attempts()
                + " / existingBetIntentId "
                + text(market.existingBetIntentId())
                + " / status "
                + text(market.existingExecutionStatus())));
        }
        lines.add("");
        lines.add("Persisted records in SQLite");
        lines.add(line("Source", "SQLITE"));
        lines.add(line("bets with order_submitted_at", coverage(
            report.persistedExecutionCoverage().betsWithOrderSubmittedAt(),
            report.persistedExecutionCoverage().realBets()
        )));
        lines.add(line("bets with executed_at", coverage(
            report.persistedExecutionCoverage().betsWithExecutedAt(),
            report.persistedExecutionCoverage().realBets()
        )));
        lines.add(line("settled real bets", report.persistedExecutionCoverage().settledRealBets()));
        lines.add("");
        lines.add("Execution");
        lines.add(line("Fully matched/recorded", report.persistedExecutionCoverage().fullyMatched()));
        lines.add(line("Partially matched/recorded", report.persistedExecutionCoverage().partiallyMatched()));
        lines.add(line("Unmatched/recorded", report.persistedExecutionCoverage().unmatched()));
        lines.add(line("Rejected events", report.logEventCoverage().orderRejectedEvents()));
        lines.add(line("Cancelled", report.persistedExecutionCoverage().cancelled()));
        lines.add(line("Average execution latency", duration(report.executionMetrics().averageExecutionLatency())));
        lines.add(line("Median execution latency", duration(report.executionMetrics().medianExecutionLatency())));
        lines.add(line("P95 execution latency", duration(report.executionMetrics().p95ExecutionLatency())));
        lines.add(line("Latency provenance", report.executionMetrics().latencyProvenance()));
        lines.add("");
        lines.add("PlaceOrders response duration");
        lines.add(line("Observations", report.placeOrdersResponseDuration().observations()));
        lines.add(line("Average", duration(report.placeOrdersResponseDuration().average())));
        lines.add(line("Median", duration(report.placeOrdersResponseDuration().median())));
        lines.add(line("P95", duration(report.placeOrdersResponseDuration().p95())));
        lines.add(line("Minimum", duration(report.placeOrdersResponseDuration().minimum())));
        lines.add(line("Maximum", duration(report.placeOrdersResponseDuration().maximum())));
        lines.add(line("ORDER_RESPONSE_BEFORE_SUBMISSION", report.placeOrdersResponseDuration().responseBeforeSubmission()));
        lines.add(line("MISSING_ORDER_RESPONSE", report.placeOrdersResponseDuration().missingOrderResponse()));
        lines.add(line("SLOW_PLACE_ORDER_RESPONSE", report.placeOrdersResponseDuration().slowResponses()));
        lines.add(line("Provenance", report.placeOrdersResponseDuration().provenance()));
        lines.add("");
        lines.add("Legacy approximation");
        lines.add(line("Average real recorded vs paper odds difference", number(report.executionMetrics().averageRealRecordedVsPaperOddsDifference())));
        lines.add(line("Odds provenance", report.executionMetrics().oddsProvenance()));
        lines.add(line("Missing recorded odds", report.executionMetrics().missingRecordedOdds()));
        lines.add(line("Missing exchange order id", report.executionMetrics().missingExchangeOrderId()));
        lines.add("");
        lines.add("Execution data coverage");
        DiagnosticsExecutionDataCoverage dataCoverage = report.executionDataCoverage();
        lines.add(line("Orders with evaluation_id", coverage(dataCoverage.withEvaluationId(), dataCoverage.totalOrders())));
        lines.add(line("Orders with recommendation_id", coverage(dataCoverage.withRecommendationId(), dataCoverage.totalOrders())));
        lines.add(line("Orders with order_submitted_at", coverage(dataCoverage.withOrderSubmittedAt(), dataCoverage.totalOrders())));
        lines.add(line("Orders with order_response_at", coverage(dataCoverage.withOrderResponseAt(), dataCoverage.totalOrders())));
        lines.add(line("Orders with order_accepted_at", coverage(dataCoverage.withOrderAcceptedAt(), dataCoverage.totalOrders())));
        lines.add(line("Orders with executed_at", coverage(dataCoverage.withExecutedAt(), dataCoverage.totalOrders())));
        lines.add(line("Orders with requested_odds", coverage(dataCoverage.withRequestedOdds(), dataCoverage.totalOrders())));
        lines.add(line("Orders with average_executed_odds", coverage(dataCoverage.withAverageExecutedOdds(), dataCoverage.totalOrders())));
        lines.add(line("Orders with requested_stake", coverage(dataCoverage.withRequestedStake(), dataCoverage.totalOrders())));
        lines.add(line("Orders with matched_stake", coverage(dataCoverage.withMatchedStake(), dataCoverage.totalOrders())));
        lines.add(line("Orders with remaining_stake", coverage(dataCoverage.withRemainingStake(), dataCoverage.totalOrders())));
        lines.add(line("Orders with execution_status", coverage(dataCoverage.withExecutionStatus(), dataCoverage.totalOrders())));
        lines.add(line("Prospective bets", dataCoverage.prospectiveOrders()));
        lines.add(line("Prospective with selection_side", dataCoverage.prospectiveWithSelectionSide()));
        lines.add(line("Prospective missing selection_side", dataCoverage.prospectiveMissingSelectionSide()));
        lines.add(line("Historical UNKNOWN selection_side", dataCoverage.historicalUnknownSelectionSide()));
        lines.add("");
        lines.add("Prospective real betting cohort");
        DiagnosticsProspectiveRealBettingCohort cohort = report.prospectiveRealBettingCohort();
        lines.add(line("Source", cohort.provenance()));
        lines.add(line("Real bets", cohort.realBets()));
        lines.add(line("Settled bets", cohort.settledBets()));
        lines.add(line("Open bets", cohort.openBets()));
        lines.add(line("Wins", cohort.wins()));
        lines.add(line("Losses", cohort.losses()));
        lines.add(line("Turnover", money(cohort.turnover())));
        lines.add(line("Net realized PnL", money(cohort.netRealizedPnl())));
        lines.add(line("ROI", number(cohort.roi())));
        lines.add(line("Average requested odds", number(cohort.averageRequestedOdds())));
        lines.add(line("Average executed odds", number(cohort.averageExecutedOdds())));
        lines.add(line("Requested-to-executed odds difference", number(cohort.requestedToExecutedOddsDifference())));
        lines.add(line("Fully matched", cohort.fullyMatched()));
        lines.add(line("Partially matched", cohort.partiallyMatched()));
        lines.add(line("Unmatched", cohort.unmatched()));
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
        lines.add(line("Runner evaluations", report.decisionFunnel().runnersAnalyzed()));
        lines.add(line("Accepted evaluations", report.decisionFunnel().runnersAnalyzed() - report.decisionFunnel().strategyRejections()));
        lines.add(line("Rejected evaluations", report.decisionFunnel().strategyRejections()));
        lines.add(line("Signal generated events observed", report.decisionFunnel().recommendationsGenerated()));
        lines.add(line("Risk rejections", report.decisionFunnel().riskRejections()));
        lines.add(line("Confirmation requests", report.decisionFunnel().confirmationRequests()));
        lines.add(line("order.submitted events", report.decisionFunnel().ordersSubmitted()));
        lines.add(line("order.response/legacy accepted events", report.decisionFunnel().ordersMatched()));
        lines.add(line("order.rejected events", report.decisionFunnel().ordersRejected()));
        lines.add(line("order.settled events", report.decisionFunnel().betsSettled()));
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

    private static String coverage(long value, long total) {
        return value + " / " + total;
    }

    private static String formatDouble(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private static void addGrouped(List<String> lines, String title, java.util.Map<String, Long> values) {
        lines.add(title + ":");
        if (values == null || values.isEmpty()) {
            lines.add("- None: 0");
            return;
        }
        values.forEach((name, count) -> lines.add("- " + text(name) + ": " + count));
    }
}
