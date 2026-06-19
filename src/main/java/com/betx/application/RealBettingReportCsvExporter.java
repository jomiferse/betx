package com.betx.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Exports real betting report rows as CSV for audit and spreadsheet analysis. */
public class RealBettingReportCsvExporter {
    private static final String HEADER = String.join(",",
        "bet_intent_id",
        "event_name",
        "market_name",
        "competition_name",
        "strategy_name",
        "runner_name",
        "selection_side",
        "stage",
        "settlement_result",
        "selected_stake",
        "executed_odds",
        "realized_profit_loss",
        "available_balance",
        "effective_available_balance",
        "created_at",
        "executed_at",
        "settled_at"
    );

    public void export(RealBettingReport report, Path exportPath) {
        try {
            Path parent = exportPath.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(exportPath, lines(report), StandardCharsets.UTF_8);
        } catch (IOException exc) {
            throw new UncheckedIOException("Could not write CSV report export: " + exportPath, exc);
        }
    }

    private static List<String> lines(RealBettingReport report) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (RealBettingReportRow row : report.rows()) {
            lines.add(String.join(",",
                csv(row.id()),
                csv(row.eventName()),
                csv(row.marketName()),
                csv(row.competitionName()),
                csv(row.strategyName()),
                csv(row.runnerName()),
                csv(row.selectionSide().name()),
                csv(row.stage() == null ? null : row.stage().name()),
                csv(row.settlementResult() == null ? null : row.settlementResult().name()),
                csv(row.selectedStake() == null ? null : row.selectedStake().toPlainString()),
                csv(row.odds() == null ? null : row.odds().toPlainString()),
                csv(row.realizedProfitLoss() == null ? null : row.realizedProfitLoss().toPlainString()),
                csv(row.availableBalance() == null ? null : row.availableBalance().toPlainString()),
                csv(row.effectiveAvailableBalance() == null ? null : row.effectiveAvailableBalance().toPlainString()),
                csv(instant(row.createdAt())),
                csv(executedAt(row)),
                csv(instant(row.settledAt()))
            ));
        }
        return lines;
    }

    private static String executedAt(RealBettingReportRow row) {
        if (row.stage() == com.betx.domain.order.BetIntentStage.EXECUTED && row.updatedAt() != null) {
            return instant(row.updatedAt());
        }
        return "";
    }

    private static String instant(Instant value) {
        return value == null ? "" : value.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
