package com.betx.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exports the focused draw-only cumulative equity curve. */
public class BacktestEquityCurveCsvExporter {
    public List<String> lines(BacktestComparisonReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("observedAt,league,season,event,odds,result,pnl,cumulativePnl,drawdown");
        report.equityCurveRows().forEach(row -> lines.add(line(
            row.observedAt().toString(),
            row.league(),
            row.season(),
            row.event(),
            row.odds().stripTrailingZeros().toPlainString(),
            row.result().name(),
            row.pnl().stripTrailingZeros().toPlainString(),
            row.cumulativePnl().stripTrailingZeros().toPlainString(),
            row.drawdown().stripTrailingZeros().toPlainString()
        )));
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
            throw new IllegalStateException("Could not export backtest equity curve CSV: " + outputPath, exc);
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
}
