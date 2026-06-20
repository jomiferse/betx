package com.betx.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DiagnosticsCsvExporter {
    private static final String HEADER = String.join(",",
        "match_status",
        "match_provenance",
        "match_gap_reason",
        "recommendation_id",
        "evaluation_id",
        "candidate_count",
        "nearest_candidate_time_difference_seconds",
        "market_id",
        "selection_id",
        "event_name",
        "runner_name",
        "selection_side",
        "competition_name",
        "strategy_name",
        "recommended_at",
        "recommended_odds",
        "exact_recommended_odds",
        "order_submitted_at",
        "order_response_at",
        "order_accepted_at",
        "executed_at",
        "requested_odds",
        "average_executed_odds",
        "requested_stake",
        "matched_stake",
        "remaining_stake",
        "execution_status",
        "paper_odds",
        "real_recorded_odds",
        "real_odds_source",
        "closing_odds",
        "paper_stake",
        "real_stake",
        "paper_pnl",
        "real_pnl",
        "execution_pnl_difference",
        "paper_pnl_per_unit_stake",
        "real_pnl_per_unit_stake",
        "normalized_execution_difference",
        "paper_executed_at",
        "real_recorded_at",
        "settled_at"
    );

    public void export(DiagnosticsReport report, Path exportPath) {
        try {
            Path parent = exportPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(exportPath, lines(report), StandardCharsets.UTF_8);
        } catch (IOException exc) {
            throw new UncheckedIOException("Could not write diagnostics CSV export: " + exportPath, exc);
        }
    }

    public List<String> lines(DiagnosticsReport report) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (DiagnosticsMatch match : report.matchedPairs()) {
            lines.add(String.join(",",
                csv(match.matchStatus().name()),
                csv(match.matchProvenance()),
                csv(match.matchGapReason()),
                csv(match.recommendationId()),
                csv(match.evaluationId()),
                csv(match.candidateCount()),
                csv(seconds(match.nearestCandidateTimeDifference())),
                csv(match.marketId()),
                csv(match.selectionId()),
                csv(match.eventName()),
                csv(match.runnerName()),
                csv(match.selectionSide()),
                csv(match.competitionName()),
                csv(match.strategyName()),
                csv(match.recommendedAt()),
                csv(match.recommendedOdds()),
                csv(match.exactRecommendedOdds()),
                csv(match.orderSubmittedAt()),
                csv(match.orderResponseAt()),
                csv(match.orderAcceptedAt()),
                csv(match.executedAt()),
                csv(match.requestedOdds()),
                csv(match.averageExecutedOdds()),
                csv(match.requestedStake()),
                csv(match.matchedStake()),
                csv(match.remainingStake()),
                csv(match.executionStatus()),
                csv(match.paperOdds()),
                csv(match.realRecordedOdds()),
                csv(match.realOddsSource()),
                csv(match.closingOdds()),
                csv(match.paperStake()),
                csv(match.realStake()),
                csv(match.paperPnl()),
                csv(match.realPnl()),
                csv(match.executionPnlDifference()),
                csv(match.paperPnlPerUnitStake()),
                csv(match.realPnlPerUnitStake()),
                csv(match.normalizedExecutionDifference()),
                csv(match.paperExecutionTimestamp()),
                csv(match.realRecordedTimestamp()),
                ""
            ));
        }
        return lines;
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value instanceof Instant instant ? instant.toString() : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static Long seconds(java.time.Duration value) {
        return value == null ? null : value.toSeconds();
    }
}
