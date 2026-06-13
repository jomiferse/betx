package com.betx.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/** Formats historical backtest results for terminal output. */
public class BacktestResultFormatter {
    private static final int TRADE_PREVIEW_LIMIT = 3;

    public List<String> format(BacktestResult result) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Backtest complete | rows=" + result.rowsRead()
            + " | runnersAnalyzed=" + result.runnersAnalyzed()
            + " | trades=" + result.trades().size()
            + " | wins=" + result.wins()
            + " | losses=" + result.losses());
        lines.add("Performance | staked=" + value(result.totalStaked())
            + " | pnl=" + twoDecimal(result.profitLoss())
            + " | roi=" + twoDecimal(result.roiPercent()) + "%"
            + " | strikeRate=" + twoDecimal(result.strikeRatePercent()) + "%"
            + " | maxDrawdown=" + twoDecimal(result.maxDrawdown()));
        if (result.trades().isEmpty()) {
            lines.add("No simulated trades found.");
            return lines;
        }
        lines.add("Top trades");
        topTrades(result).forEach(trade -> lines.add(formatTrade(trade)));
        lines.add("Bottom trades");
        bottomTrades(result).forEach(trade -> lines.add(formatTrade(trade)));
        lines.add("Strategy evaluation");
        for (BacktestSegmentType type : BacktestSegmentType.values()) {
            lines.add("By " + displayName(type));
            result.evaluation().segments(type).stream()
                .limit(5)
                .forEach(segment -> lines.add(formatSegment(segment)));
        }
        return lines;
    }

    private List<BacktestTrade> topTrades(BacktestResult result) {
        return result.trades().stream()
            .sorted(Comparator.comparing(BacktestTrade::profitLoss).reversed())
            .limit(TRADE_PREVIEW_LIMIT)
            .toList();
    }

    private List<BacktestTrade> bottomTrades(BacktestResult result) {
        return result.trades().stream()
            .sorted(Comparator.comparing(BacktestTrade::profitLoss))
            .limit(TRADE_PREVIEW_LIMIT)
            .toList();
    }

    private String formatTrade(BacktestTrade trade) {
        return "TRADE | " + trade.outcome()
            + " | observedAt=" + trade.observedAt()
            + " | event=" + nullSafe(trade.eventName())
            + " | runner=" + nullSafe(trade.runnerName())
            + " | odds=" + value(trade.odds())
            + " | stake=" + value(trade.stake())
            + " | pnl=" + trade.profitLoss().toPlainString();
    }

    private String formatSegment(BacktestSegment segment) {
        return "SEGMENT | " + label(segment.type())
            + " | " + segment.name()
            + " | trades=" + segment.trades()
            + " | wins=" + segment.wins()
            + " | losses=" + segment.losses()
            + " | staked=" + value(segment.totalStaked())
            + " | pnl=" + twoDecimal(segment.profitLoss())
            + " | roi=" + twoDecimal(segment.roiPercent()) + "%"
            + " | strikeRate=" + twoDecimal(segment.strikeRatePercent()) + "%"
            + " | maxDrawdown=" + twoDecimal(segment.maxDrawdown());
    }

    private String displayName(BacktestSegmentType type) {
        return switch (type) {
            case ODDS_BAND -> "odds band";
            case RUNNER_TYPE -> "runner type";
            case COMPETITION -> "competition";
            case CONFIDENCE -> "confidence";
            case ODDS_MOVEMENT -> "odds movement";
        };
    }

    private String label(BacktestSegmentType type) {
        return switch (type) {
            case ODDS_BAND -> "odds_band";
            case RUNNER_TYPE -> "runner_type";
            case COMPETITION -> "competition";
            case CONFIDENCE -> "confidence";
            case ODDS_MOVEMENT -> "odds_movement";
        };
    }

    private String value(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String twoDecimal(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString();
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
