package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.RealBettingReportRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** Calculates read-only real betting performance metrics from persisted rows. */
@Service
public class RealBettingReportService implements GenerateRealBettingReportUseCase {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final List<String> ODDS_BANDS = List.of(
        "1.00-1.99",
        "2.00-2.99",
        "3.00-3.99",
        "4.00-4.99",
        "5.00+"
    );

    private final BetxConfigRepository configRepository;
    private final RealBettingReportRepository reportRepository;

    public RealBettingReportService(BetxConfigRepository configRepository, RealBettingReportRepository reportRepository) {
        this.configRepository = configRepository;
        this.reportRepository = reportRepository;
    }

    @Override
    public RealBettingReport generate(ConfigPath configPath) {
        BetxConfig config = configRepository.load(configPath);
        List<RealBettingReportRow> rows = reportRepository.listReportRows(config.storage().path());
        if (rows.isEmpty()) {
            return RealBettingReport.empty();
        }
        List<RealBettingReportRow> settled = rows.stream()
            .filter(RealBettingReportRow::isSettledForPerformance)
            .sorted(Comparator.comparing(RealBettingReportRow::performanceTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        long openBets = rows.stream().filter(RealBettingReportRow::isOpen).count();
        BigDecimal openExposure = rows.stream()
            .filter(RealBettingReportRow::isOpen)
            .map(RealBettingReportRow::selectedStake)
            .filter(stake -> stake != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cancelled = rows.stream().filter(row -> row.stage() == BetIntentStage.CANCELLED).count();

        BigDecimal totalStaked = sum(settled, RealBettingReportRow::selectedStake);
        BigDecimal netPnl = sum(settled, RealBettingReportRow::realizedProfitLoss);
        long wins = countSettlement(settled, BetSettlementResult.WIN);
        long losses = countSettlement(settled, BetSettlementResult.LOSE);
        long voids = countSettlement(settled, BetSettlementResult.VOID);
        BalanceMetrics balances = balances(rows);
        EquityMetrics equity = equityMetrics(settled);

        return new RealBettingReport(
            periodStart(rows),
            periodEnd(rows),
            periodLabel(rows),
            settled.size(),
            openBets,
            wins,
            losses,
            voids + cancelled,
            money(totalStaked),
            money(openExposure),
            money(netPnl),
            percent(netPnl, totalStaked),
            percent(BigDecimal.valueOf(wins), BigDecimal.valueOf(wins + losses)),
            averageOdds(settled),
            balances.operationalAvailableBalance(),
            balances.exchangeAvailableBalance(),
            money(equity.current()),
            money(equity.peak()),
            money(equity.maximumDrawdown()),
            money(equity.currentDrawdown()),
            equity.initialReferenceBalance(),
            equity.usesInitialReference(),
            streak(settled, BetSettlementResult.WIN),
            streak(settled, BetSettlementResult.LOSE),
            segments(settled, row -> row.selectionSide().name()),
            segments(settled, RealBettingReportRow::runnerName),
            segments(settled, RealBettingReportRow::competitionName),
            segments(settled, RealBettingReportRow::strategyName),
            oddsBandSegments(settled),
            dailyPnl(settled),
            rollingWindows(settled),
            rows,
            warnings(settled.size()),
            limitations(rows, equity)
        );
    }

    private static Instant periodStart(List<RealBettingReportRow> rows) {
        return rows.stream()
            .map(row -> row.createdAt() == null ? row.updatedAt() : row.createdAt())
            .filter(value -> value != null)
            .min(Comparator.naturalOrder())
            .orElse(null);
    }

    private static Instant periodEnd(List<RealBettingReportRow> rows) {
        return rows.stream()
            .map(row -> row.updatedAt() == null ? row.settledAt() : row.updatedAt())
            .filter(value -> value != null)
            .max(Comparator.naturalOrder())
            .orElse(null);
    }

    private static String periodLabel(List<RealBettingReportRow> rows) {
        Instant start = periodStart(rows);
        Instant end = periodEnd(rows);
        if (start == null || end == null) {
            return "N/A";
        }
        return start + " to " + end;
    }

    private static BigDecimal sum(List<RealBettingReportRow> rows, Function<RealBettingReportRow, BigDecimal> value) {
        return rows.stream()
            .map(value)
            .filter(item -> item != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long countSettlement(List<RealBettingReportRow> rows, BetSettlementResult settlement) {
        return rows.stream().filter(row -> row.settlementResult() == settlement).count();
    }

    private static BigDecimal averageOdds(List<RealBettingReportRow> settled) {
        List<BigDecimal> odds = settled.stream()
            .map(RealBettingReportRow::odds)
            .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
            .toList();
        if (odds.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = odds.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(odds.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return numerator.multiply(ONE_HUNDRED).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BalanceMetrics balances(List<RealBettingReportRow> rows) {
        BigDecimal exchange = latestBalance(rows, RealBettingReportRow::availableBalance);
        BigDecimal operational = latestBalance(rows, RealBettingReportRow::effectiveAvailableBalance);
        return new BalanceMetrics(operational, exchange);
    }

    private static BigDecimal latestBalance(List<RealBettingReportRow> rows, Function<RealBettingReportRow, BigDecimal> value) {
        return rows.stream()
            .filter(row -> value.apply(row) != null)
            .max(Comparator.comparing(RealBettingReportRow::balanceTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(value)
            .orElse(null);
    }

    private static EquityMetrics equityMetrics(List<RealBettingReportRow> settled) {
        if (settled.isEmpty()) {
            return new EquityMetrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, false);
        }
        BigDecimal initialReference = initialReferenceBalance(settled.getFirst());
        BigDecimal current = initialReference == null ? BigDecimal.ZERO : initialReference;
        BigDecimal peak = current;
        BigDecimal maximumDrawdown = BigDecimal.ZERO;
        for (RealBettingReportRow row : settled) {
            current = current.add(row.realizedProfitLoss() == null ? BigDecimal.ZERO : row.realizedProfitLoss());
            if (current.compareTo(peak) > 0) {
                peak = current;
            }
            BigDecimal drawdown = peak.subtract(current);
            if (drawdown.compareTo(maximumDrawdown) > 0) {
                maximumDrawdown = drawdown;
            }
        }
        return new EquityMetrics(
            current,
            peak,
            maximumDrawdown,
            peak.subtract(current),
            initialReference,
            initialReference != null
        );
    }

    private static BigDecimal initialReferenceBalance(RealBettingReportRow row) {
        BigDecimal balance = row.effectiveAvailableBalance() == null ? row.availableBalance() : row.effectiveAvailableBalance();
        if (balance == null || row.realizedProfitLoss() == null) {
            return null;
        }
        return balance.subtract(row.realizedProfitLoss());
    }

    private static int streak(List<RealBettingReportRow> settled, BetSettlementResult target) {
        int best = 0;
        int current = 0;
        for (RealBettingReportRow row : settled) {
            if (row.settlementResult() == target) {
                current++;
                best = Math.max(best, current);
            } else if (row.settlementResult() == BetSettlementResult.WIN || row.settlementResult() == BetSettlementResult.LOSE) {
                current = 0;
            }
        }
        return best;
    }

    private static List<RealBettingReportSegment> segments(
        List<RealBettingReportRow> settled,
        Function<RealBettingReportRow, String> classifier
    ) {
        Map<String, List<RealBettingReportRow>> grouped = settled.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                row -> label(classifier.apply(row)),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));
        return grouped.entrySet().stream()
            .map(entry -> segment(entry.getKey(), entry.getValue()))
            .sorted(segmentComparator())
            .toList();
    }

    private static List<RealBettingReportSegment> oddsBandSegments(List<RealBettingReportRow> settled) {
        Map<String, List<RealBettingReportRow>> grouped = new LinkedHashMap<>();
        ODDS_BANDS.forEach(band -> grouped.put(band, new java.util.ArrayList<>()));
        for (RealBettingReportRow row : settled) {
            grouped.computeIfAbsent(oddsBand(row.odds()), ignored -> new java.util.ArrayList<>()).add(row);
        }
        return grouped.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(entry -> segment(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static RealBettingReportSegment segment(String name, List<RealBettingReportRow> rows) {
        long wins = countSettlement(rows, BetSettlementResult.WIN);
        long losses = countSettlement(rows, BetSettlementResult.LOSE);
        long voids = countSettlement(rows, BetSettlementResult.VOID);
        BigDecimal stake = sum(rows, RealBettingReportRow::selectedStake);
        BigDecimal pnl = sum(rows, RealBettingReportRow::realizedProfitLoss);
        return new RealBettingReportSegment(
            name,
            rows.size(),
            wins,
            losses,
            voids,
            money(stake),
            money(pnl),
            percent(pnl, stake),
            percent(BigDecimal.valueOf(wins), BigDecimal.valueOf(wins + losses))
        );
    }

    private static Comparator<RealBettingReportSegment> segmentComparator() {
        return Comparator.comparing(RealBettingReportSegment::settledBets).reversed()
            .thenComparing(RealBettingReportSegment::name);
    }

    private static String oddsBand(BigDecimal odds) {
        if (odds == null) {
            return "N/A";
        }
        if (odds.compareTo(new BigDecimal("2.00")) < 0) {
            return "1.00-1.99";
        }
        if (odds.compareTo(new BigDecimal("3.00")) < 0) {
            return "2.00-2.99";
        }
        if (odds.compareTo(new BigDecimal("4.00")) < 0) {
            return "3.00-3.99";
        }
        if (odds.compareTo(new BigDecimal("5.00")) < 0) {
            return "4.00-4.99";
        }
        return "5.00+";
    }

    private static List<RealBettingReportDailyPnl> dailyPnl(List<RealBettingReportRow> settled) {
        Map<java.time.LocalDate, List<RealBettingReportRow>> grouped = settled.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                row -> row.performanceTimestamp().atZone(ZoneOffset.UTC).toLocalDate(),
                java.util.TreeMap::new,
                java.util.stream.Collectors.toList()
            ));
        return grouped.entrySet().stream()
            .map(entry -> new RealBettingReportDailyPnl(
                entry.getKey(),
                entry.getValue().size(),
                money(sum(entry.getValue(), RealBettingReportRow::realizedProfitLoss))
            ))
            .toList();
    }

    private static List<RealBettingReportRollingWindow> rollingWindows(List<RealBettingReportRow> settled) {
        return List.of(25, 50, 100).stream()
            .map(size -> rollingWindow(settled, size))
            .toList();
    }

    private static RealBettingReportRollingWindow rollingWindow(List<RealBettingReportRow> settled, int requestedSize) {
        int fromIndex = Math.max(0, settled.size() - requestedSize);
        List<RealBettingReportRow> window = settled.subList(fromIndex, settled.size());
        long wins = countSettlement(window, BetSettlementResult.WIN);
        long losses = countSettlement(window, BetSettlementResult.LOSE);
        BigDecimal totalStaked = sum(window, RealBettingReportRow::selectedStake);
        BigDecimal netPnl = sum(window, RealBettingReportRow::realizedProfitLoss);
        return new RealBettingReportRollingWindow(
            requestedSize,
            window.size(),
            wins,
            losses,
            percent(BigDecimal.valueOf(wins), BigDecimal.valueOf(wins + losses)),
            money(totalStaked),
            money(netPnl),
            percent(netPnl, totalStaked),
            money(equityMetrics(window).maximumDrawdown()),
            streak(window, BetSettlementResult.WIN),
            streak(window, BetSettlementResult.LOSE)
        );
    }

    private static List<RealBettingReportWarning> warnings(int settledBets) {
        if (settledBets < 100) {
            return List.of(new RealBettingReportWarning("Small sample: results are not statistically reliable yet."));
        }
        if (settledBets < 250) {
            return List.of(new RealBettingReportWarning("Early evidence: continue collecting real betting data."));
        }
        return List.of(new RealBettingReportWarning("Validation sample reached. Review ROI, drawdown and stability by segment."));
    }

    private static List<String> limitations(List<RealBettingReportRow> rows, EquityMetrics equity) {
        List<String> limitations = new java.util.ArrayList<>();
        if (rows.stream().anyMatch(row -> "N/A".equals(row.competitionName()))) {
            limitations.add("Competition is shown as N/A when no linked signal_history row exists.");
        }
        if (rows.stream().anyMatch(row -> "N/A".equals(row.strategyName()))) {
            limitations.add("Strategy is shown as N/A for historical bets created before strategy persistence.");
        }
        if (!equity.usesInitialReference()) {
            limitations.add("No reliable initial balance was found; the performance curve starts from cumulative realized PnL at 0.00 €.");
        }
        return limitations;
    }

    private static String label(String value) {
        return value == null || value.isBlank() ? "N/A" : value.strip();
    }

    private record BalanceMetrics(BigDecimal operationalAvailableBalance, BigDecimal exchangeAvailableBalance) {
    }

    private record EquityMetrics(
        BigDecimal current,
        BigDecimal peak,
        BigDecimal maximumDrawdown,
        BigDecimal currentDrawdown,
        BigDecimal initialReferenceBalance,
        boolean usesInitialReference
    ) {
    }
}
