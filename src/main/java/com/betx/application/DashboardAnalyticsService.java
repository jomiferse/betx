package com.betx.application;

import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetSettlementResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Read-only analytics facade for the product dashboard. */
@Service
public class DashboardAnalyticsService {
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    private final GenerateRealBettingReportUseCase reportUseCase;
    private final BetxInterfaceProperties properties;

    public DashboardAnalyticsService(GenerateRealBettingReportUseCase reportUseCase, BetxInterfaceProperties properties) {
        this.reportUseCase = reportUseCase;
        this.properties = properties;
    }

    public DashboardSummaryView summary(String range) {
        List<RealBettingReportRow> rows = rowsForRange(range);
        List<RealBettingReportRow> settledRows = settledRows(rows);
        BigDecimal totalStaked = sum(settledRows, RealBettingReportRow::selectedStake);
        BigDecimal pnl = sum(settledRows, RealBettingReportRow::realizedProfitLoss);
        long wins = settledRows.stream().filter(row -> row.settlementResult() == BetSettlementResult.WIN).count();
        long losses = settledRows.stream().filter(row -> row.settlementResult() == BetSettlementResult.LOSE).count();
        return new DashboardSummaryView(
            pnl,
            roi(pnl, totalStaked),
            rows.size(),
            wins,
            losses,
            percent(wins, wins + losses),
            totalStaked,
            maxDrawdown(settledRows),
            openExposure(rows),
            lastUpdatedAt(rows)
        );
    }

    public DashboardSummaryView summary() {
        return summary("ALL");
    }

    public List<DashboardEquityPoint> equity(String range) {
        BigDecimal cumulativePnl = BigDecimal.ZERO;
        BigDecimal cumulativeStake = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        List<DashboardEquityPoint> points = new ArrayList<>();
        List<RealBettingReportRow> settled = settledRows(rowsForRange(range));
        Map<LocalDate, DailyAggregate> dailyAggregates = dailyAggregates(settled);
        long sequenceNumber = 0;
        for (RealBettingReportRow row : settled) {
            sequenceNumber++;
            cumulativePnl = money(cumulativePnl.add(nullToZero(row.realizedProfitLoss())));
            cumulativeStake = money(cumulativeStake.add(nullToZero(row.selectedStake())));
            if (cumulativePnl.compareTo(peak) > 0) {
                peak = cumulativePnl;
            }
            LocalDate day = day(row.performanceTimestamp());
            DailyAggregate daily = dailyAggregates.getOrDefault(day, DailyAggregate.empty());
            points.add(new DashboardEquityPoint(
                row.performanceTimestamp(),
                cumulativePnl,
                cumulativePnl,
                money(peak.subtract(cumulativePnl)),
                money(row.realizedProfitLoss()),
                daily.pnl(),
                daily.trades(),
                sequenceNumber,
                roi(cumulativePnl, cumulativeStake)
            ));
        }
        return List.copyOf(points);
    }

    public List<DashboardEquityPoint> equity() {
        return equity("ALL");
    }

    public List<DashboardDailyPnlPoint> dailyPnl(String range) {
        return dailyAggregates(settledRows(rowsForRange(range))).entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                DailyAggregate aggregate = entry.getValue();
                return new DashboardDailyPnlPoint(
                    entry.getKey(),
                    aggregate.trades(),
                    aggregate.wins(),
                    aggregate.losses(),
                    aggregate.totalStake(),
                    aggregate.pnl(),
                    roi(aggregate.pnl(), aggregate.totalStake())
                );
            })
            .toList();
    }

    public List<DashboardDailyPnlPoint> dailyPnl() {
        return dailyPnl("ALL");
    }

    public List<DashboardBreakdownItem> strategyBreakdown(String range) {
        return settledRows(rowsForRange(range)).stream()
            .collect(Collectors.groupingBy(row -> firstPresent(row.strategyName(), "N/A")))
            .entrySet().stream()
            .map(entry -> segment(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(DashboardBreakdownItem::pnl).reversed())
            .toList();
    }

    public List<DashboardBreakdownItem> strategyBreakdown() {
        return strategyBreakdown("ALL");
    }

    public DashboardTradePage trades(
        String range,
        int page,
        int size,
        String status,
        String result,
        String strategy,
        String search,
        String sort,
        String order
    ) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size <= 0 ? DEFAULT_PAGE_SIZE : size));
        List<DashboardTradeView> filtered = rowsForRange(range).stream()
            .filter(row -> statusMatches(row, status))
            .filter(row -> resultMatches(row, result))
            .filter(row -> strategyMatches(row, strategy))
            .filter(row -> searchMatches(row, search))
            .sorted(comparator(sort, order))
            .map(DashboardAnalyticsService::trade)
            .toList();
        int from = Math.min(normalizedPage * normalizedSize, filtered.size());
        int to = Math.min(from + normalizedSize, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / normalizedSize);
        return new DashboardTradePage(filtered.subList(from, to), normalizedPage, normalizedSize, filtered.size(), totalPages);
    }

    public DashboardTradePage trades() {
        return trades("ALL", 0, DEFAULT_PAGE_SIZE, null, null, null, null, "timestamp", "desc");
    }

    private RealBettingReport report() {
        Path configPath = properties.configPath();
        return reportUseCase.generate(new ConfigPath(configPath));
    }

    private List<RealBettingReportRow> rowsForRange(String range) {
        List<RealBettingReportRow> rows = report().rows();
        if (isAllRange(range) || rows.isEmpty()) {
            return rows;
        }
        Instant anchor = rows.stream()
            .map(DashboardAnalyticsService::tradeTimestamp)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
        Integer days = days(range);
        if (anchor == null || days == null) {
            return rows;
        }
        Instant cutoff = anchor.minus(days, ChronoUnit.DAYS);
        return rows.stream()
            .filter(row -> {
                Instant timestamp = tradeTimestamp(row);
                return timestamp != null && !timestamp.isBefore(cutoff);
            })
            .toList();
    }

    private static List<RealBettingReportRow> settledRows(List<RealBettingReportRow> rows) {
        return rows.stream()
            .filter(RealBettingReportRow::isSettledForPerformance)
            .sorted(Comparator.comparing(RealBettingReportRow::performanceTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private static DashboardBreakdownItem segment(String name, List<RealBettingReportRow> rows) {
        BigDecimal totalStake = sum(rows, RealBettingReportRow::selectedStake);
        BigDecimal pnl = sum(rows, RealBettingReportRow::realizedProfitLoss);
        long wins = rows.stream().filter(row -> row.settlementResult() == BetSettlementResult.WIN).count();
        long losses = rows.stream().filter(row -> row.settlementResult() == BetSettlementResult.LOSE).count();
        return new DashboardBreakdownItem(name, rows.size(), wins, losses, pnl, roi(pnl, totalStake), percent(wins, wins + losses));
    }

    private static DashboardTradeView trade(RealBettingReportRow row) {
        return new DashboardTradeView(
            tradeTimestamp(row),
            firstPresent(row.marketName(), row.eventName(), "N/A"),
            firstPresent(row.runnerName(), "N/A"),
            firstPresent(row.strategyName(), "N/A"),
            row.odds(),
            row.selectedStake(),
            row.stage() == null ? "UNKNOWN" : row.stage().name(),
            row.settlementResult() == null ? null : row.settlementResult().name(),
            row.realizedProfitLoss()
        );
    }

    private static Map<LocalDate, DailyAggregate> dailyAggregates(List<RealBettingReportRow> rows) {
        return rows.stream()
            .collect(Collectors.groupingBy(row -> day(row.performanceTimestamp()), Collectors.collectingAndThen(Collectors.toList(), DailyAggregate::from)));
    }

    private static BigDecimal maxDrawdown(List<RealBettingReportRow> rows) {
        BigDecimal cumulativePnl = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (RealBettingReportRow row : rows) {
            cumulativePnl = money(cumulativePnl.add(nullToZero(row.realizedProfitLoss())));
            if (cumulativePnl.compareTo(peak) > 0) {
                peak = cumulativePnl;
            }
            BigDecimal drawdown = money(peak.subtract(cumulativePnl));
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }

    private static BigDecimal openExposure(List<RealBettingReportRow> rows) {
        return sum(rows.stream().filter(RealBettingReportRow::isOpen).toList(), RealBettingReportRow::selectedStake);
    }

    private static Instant lastUpdatedAt(List<RealBettingReportRow> rows) {
        return rows.stream()
            .map(RealBettingReportRow::updatedAt)
            .filter(value -> value != null)
            .max(Comparator.naturalOrder())
            .orElse(null);
    }

    private static boolean statusMatches(RealBettingReportRow row, String status) {
        if (isBlankOrAll(status)) {
            return true;
        }
        return row.stage() != null && row.stage().name().equalsIgnoreCase(status.strip());
    }

    private static boolean resultMatches(RealBettingReportRow row, String result) {
        if (isBlankOrAll(result)) {
            return true;
        }
        String normalized = normalizeResult(result);
        if ("PENDING".equals(normalized)) {
            return row.settlementResult() == null;
        }
        return row.settlementResult() != null && row.settlementResult().name().equals(normalized);
    }

    private static boolean strategyMatches(RealBettingReportRow row, String strategy) {
        return isBlankOrAll(strategy) || firstPresent(row.strategyName(), "N/A").equalsIgnoreCase(strategy.strip());
    }

    private static boolean searchMatches(RealBettingReportRow row, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.strip().toLowerCase(Locale.ROOT);
        return contains(row.marketName(), needle)
            || contains(row.eventName(), needle)
            || contains(row.runnerName(), needle)
            || contains(row.strategyName(), needle);
    }

    private static Comparator<RealBettingReportRow> comparator(String sort, String order) {
        Comparator<RealBettingReportRow> comparator = switch (sort == null ? "" : sort.toLowerCase(Locale.ROOT)) {
            case "pnl" -> Comparator.comparing(row -> nullToZero(row.realizedProfitLoss()));
            case "stake" -> Comparator.comparing(row -> nullToZero(row.selectedStake()));
            case "odds" -> Comparator.comparing(row -> nullToZero(row.odds()));
            default -> Comparator.comparing(DashboardAnalyticsService::tradeTimestamp, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if (!"asc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    private static Instant tradeTimestamp(RealBettingReportRow row) {
        if (row.updatedAt() != null) {
            return row.updatedAt();
        }
        if (row.performanceTimestamp() != null) {
            return row.performanceTimestamp();
        }
        return row.createdAt();
    }

    private static LocalDate day(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static BigDecimal sum(List<RealBettingReportRow> rows, java.util.function.Function<RealBettingReportRow, BigDecimal> mapper) {
        return money(rows.stream().map(mapper).reduce(BigDecimal.ZERO, (left, right) -> left.add(nullToZero(right))));
    }

    private static BigDecimal roi(BigDecimal pnl, BigDecimal stake) {
        if (stake == null || stake.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return nullToZero(pnl).multiply(new BigDecimal("100")).divide(stake, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).multiply(new BigDecimal("100"))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static Integer days(String range) {
        if (range == null || range.isBlank()) {
            return 30;
        }
        String normalized = range.strip().toUpperCase(Locale.ROOT);
        if (!normalized.endsWith("D")) {
            return null;
        }
        try {
            return Integer.parseInt(normalized.substring(0, normalized.length() - 1));
        } catch (NumberFormatException exc) {
            return null;
        }
    }

    private static boolean isAllRange(String range) {
        return range == null || range.isBlank() || "ALL".equalsIgnoreCase(range.strip());
    }

    private static boolean isBlankOrAll(String value) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.strip());
    }

    private static String normalizeResult(String value) {
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "WON", "GANADA" -> "WIN";
            case "LOST", "PERDIDA" -> "LOSE";
            case "PENDING", "PENDIENTE" -> "PENDING";
            default -> value.strip().toUpperCase(Locale.ROOT);
        };
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return nullToZero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String firstPresent(String first, String fallback) {
        return firstPresent(first, fallback, "N/A");
    }

    private static String firstPresent(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private record DailyAggregate(long trades, long wins, long losses, BigDecimal totalStake, BigDecimal pnl) {
        static DailyAggregate from(List<RealBettingReportRow> rows) {
            long wins = rows.stream().filter(row -> row.settlementResult() == BetSettlementResult.WIN).count();
            long losses = rows.stream().filter(row -> row.settlementResult() == BetSettlementResult.LOSE).count();
            return new DailyAggregate(
                rows.size(),
                wins,
                losses,
                sum(rows, RealBettingReportRow::selectedStake),
                sum(rows, RealBettingReportRow::realizedProfitLoss)
            );
        }

        static DailyAggregate empty() {
            return new DailyAggregate(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
