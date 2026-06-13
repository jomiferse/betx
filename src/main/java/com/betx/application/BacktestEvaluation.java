package com.betx.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Grouped strategy diagnostics for simulated backtest trades. */
public record BacktestEvaluation(Map<BacktestSegmentType, List<BacktestSegment>> segments) {
    public BacktestEvaluation {
        EnumMap<BacktestSegmentType, List<BacktestSegment>> normalized = new EnumMap<>(BacktestSegmentType.class);
        for (BacktestSegmentType type : BacktestSegmentType.values()) {
            normalized.put(type, segments == null ? List.of() : List.copyOf(segments.getOrDefault(type, List.of())));
        }
        segments = Map.copyOf(normalized);
    }

    public static BacktestEvaluation empty() {
        return from(List.of());
    }

    public static BacktestEvaluation from(List<BacktestTrade> trades) {
        List<BacktestTrade> safeTrades = trades == null ? List.of() : List.copyOf(trades);
        EnumMap<BacktestSegmentType, List<BacktestSegment>> grouped = new EnumMap<>(BacktestSegmentType.class);
        grouped.put(BacktestSegmentType.ODDS_BAND, group(safeTrades, BacktestSegmentType.ODDS_BAND, BacktestEvaluation::oddsBand));
        grouped.put(BacktestSegmentType.RUNNER_TYPE, group(safeTrades, BacktestSegmentType.RUNNER_TYPE, trade -> trade.runnerType().name()));
        grouped.put(BacktestSegmentType.COMPETITION, group(safeTrades, BacktestSegmentType.COMPETITION, BacktestTrade::competitionName));
        grouped.put(BacktestSegmentType.CONFIDENCE, group(safeTrades, BacktestSegmentType.CONFIDENCE, BacktestTrade::confidenceLabel));
        grouped.put(BacktestSegmentType.ODDS_MOVEMENT, group(safeTrades, BacktestSegmentType.ODDS_MOVEMENT, BacktestEvaluation::oddsMovement));
        return new BacktestEvaluation(grouped);
    }

    public List<BacktestSegment> segments(BacktestSegmentType type) {
        return segments.getOrDefault(type, List.of());
    }

    private static List<BacktestSegment> group(
        List<BacktestTrade> trades,
        BacktestSegmentType type,
        Function<BacktestTrade, String> classifier
    ) {
        return trades.stream()
            .collect(Collectors.groupingBy(classifier))
            .entrySet()
            .stream()
            .map(entry -> BacktestSegment.from(type, entry.getKey(), entry.getValue()))
            .sorted(
                Comparator.comparing(BacktestSegment::trades)
                    .reversed()
                    .thenComparing(Comparator.comparing(BacktestSegment::roiPercent).reversed())
            )
            .toList();
    }

    private static String oddsBand(BacktestTrade trade) {
        BigDecimal odds = trade.odds();
        if (odds == null) {
            return "unknown";
        }
        if (odds.compareTo(new BigDecimal("1.50")) < 0) {
            return "<1.50";
        }
        if (odds.compareTo(new BigDecimal("2.00")) <= 0) {
            return "1.50-2.00";
        }
        if (odds.compareTo(new BigDecimal("3.00")) <= 0) {
            return "2.01-3.00";
        }
        if (odds.compareTo(new BigDecimal("6.00")) <= 0) {
            return "3.01-6.00";
        }
        return ">6.00";
    }

    private static String oddsMovement(BacktestTrade trade) {
        BigDecimal movement = trade.oddsMovementPercent();
        if (movement == null) {
            return "unknown";
        }
        if (movement.compareTo(new BigDecimal("-10.00")) <= 0) {
            return "steam <= -10%";
        }
        if (movement.compareTo(new BigDecimal("-3.00")) <= 0) {
            return "drop -10% to -3%";
        }
        if (movement.compareTo(new BigDecimal("-1.00")) <= 0) {
            return "drop -3% to -1%";
        }
        if (movement.compareTo(new BigDecimal("1.00")) <= 0) {
            return "stable -1% to +1%";
        }
        if (movement.compareTo(new BigDecimal("5.00")) <= 0) {
            return "drift +1% to +5%";
        }
        return "drift > +5%";
    }
}
