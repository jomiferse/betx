package com.betx.application;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exports focused draw-only paper trades for prospective validation. */
public class BacktestPaperTradeCsvExporter {
    public List<String> lines(BacktestComparisonReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("event_id,market_id,league,season,event,runner,side,recommendation_timestamp,execution_timestamp,closing_timestamp,available_back_odds,requested_odds,execution_odds,closing_odds,result,gross_pnl,commission,net_pnl,decimal_clv_ratio,implied_probability_change,movement_bucket,slippage_model");
        for (BacktestPaperTrade trade : report.paperTrades()) {
            lines.add(line(
                trade.eventId(),
                trade.marketId(),
                trade.league(),
                trade.season(),
                trade.eventName(),
                trade.runner(),
                trade.side().name(),
                trade.recommendationTimestamp().toString(),
                timestamp(trade.executionTimestamp()),
                timestamp(trade.closingTimestamp()),
                value(trade.availableBackOdds()),
                value(trade.requestedOdds()),
                value(trade.executionOdds()),
                value(trade.closingOdds()),
                trade.result() == null ? "" : trade.result().name(),
                value(trade.grossPnl()),
                value(trade.commission()),
                value(trade.netPnl()),
                value(trade.decimalClvRatio()),
                value(trade.impliedProbabilityChange()),
                trade.movementBucket(),
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
            throw new IllegalStateException("Could not export paper trades CSV: " + outputPath, exc);
        }
    }

    private String line(String... values) {
        return java.util.Arrays.stream(values)
            .map(this::csv)
            .collect(java.util.stream.Collectors.joining(","));
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private String value(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String timestamp(java.time.Instant instant) {
        return instant == null ? "" : instant.toString();
    }
}
