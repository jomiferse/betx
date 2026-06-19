package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import com.betx.application.DiagnosticsModel.DiagnosticsDataset;
import com.betx.application.DiagnosticsModel.DiagnosticsRequest;
import com.betx.application.DiagnosticsModel.MatchStatus;
import com.betx.application.DiagnosticsModel.RealBetDiagnosticRow;
import com.betx.application.DiagnosticsModel.RealOddsSource;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DiagnosticsService implements GenerateDiagnosticsUseCase {
    private static final int SCALE = 8;
    private static final Instant NEW_METADATA_CUTOFF = Instant.parse("2026-06-15T00:00:00Z");

    private final BetxConfigRepository configRepository;
    private final DiagnosticsRepository diagnosticsRepository;
    private final DiagnosticsLogReader logReader;
    private final Clock clock;

    @Autowired
    public DiagnosticsService(
        BetxConfigRepository configRepository,
        DiagnosticsRepository diagnosticsRepository,
        DiagnosticsLogReader logReader
    ) {
        this(configRepository, diagnosticsRepository, logReader, Clock.systemUTC());
    }

    DiagnosticsService(
        BetxConfigRepository configRepository,
        DiagnosticsRepository diagnosticsRepository,
        DiagnosticsLogReader logReader,
        Clock clock
    ) {
        this.configRepository = configRepository;
        this.diagnosticsRepository = diagnosticsRepository;
        this.logReader = logReader;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public DiagnosticsReport generate(DiagnosticsRequest request) {
        BetxConfig config = configRepository.load(request.configPath());
        DiagnosticsPeriod requestedPeriod = resolvePeriod(config.storage().path(), request);
        DiagnosticsDataset dataset = diagnosticsRepository.load(config.storage().path(), requestedPeriod.from(), requestedPeriod.to());
        DiagnosticsLogSummary logs = logReader.read(request.logsDir(), requestedPeriod.from(), requestedPeriod.to());
        List<DiagnosticsMatch> matches = match(dataset.realBets(), dataset.paperTrades(), request.matchWindow());
        DiagnosticsCoverage coverage = coverage(dataset, matches);
        DiagnosticsPaperVsRealMetrics paperVsReal = paperVsRealMetrics(matches);
        DiagnosticsExecutionMetrics execution = executionMetrics(dataset.realBets(), matches, logs);
        DiagnosticsDecisionFunnel funnel = decisionFunnel(dataset, logs);
        List<DiagnosticFinding> findings = integrityFindings(dataset, matches);
        List<String> limitations = limitations(logs);
        List<String> topFindings = topFindings(coverage, paperVsReal, execution, findings);
        return new DiagnosticsReport(
            Instant.now(clock),
            requestedPeriod,
            coverage,
            funnel,
            execution,
            paperVsReal,
            findings,
            limitations,
            topFindings,
            matches
        );
    }

    private DiagnosticsPeriod resolvePeriod(String databasePath, DiagnosticsRequest request) {
        if (request.from() != null || request.to() != null) {
            return new DiagnosticsPeriod(request.from(), request.to());
        }
        return diagnosticsRepository.findDefaultPeriod(databasePath);
    }

    private List<DiagnosticsMatch> match(
        List<RealBetDiagnosticRow> realBets,
        List<PaperTrade> paperTrades,
        Duration window
    ) {
        Map<Key, List<RealBetDiagnosticRow>> realByKey = realBets.stream()
            .collect(Collectors.groupingBy(Key::from));
        Map<Key, List<PaperTrade>> paperByKey = paperTrades.stream()
            .collect(Collectors.groupingBy(Key::from));
        Set<Key> keys = new HashSet<>();
        keys.addAll(realByKey.keySet());
        keys.addAll(paperByKey.keySet());
        List<DiagnosticsMatch> matches = new ArrayList<>();
        for (Key key : keys.stream().sorted().toList()) {
            List<RealBetDiagnosticRow> reals = sortedReal(realByKey.getOrDefault(key, List.of()));
            List<PaperTrade> papers = sortedPaper(paperByKey.getOrDefault(key, List.of()));
            if (reals.isEmpty()) {
                papers.forEach(paper -> matches.add(match(null, paper, MatchStatus.PAPER_ONLY)));
            } else if (papers.isEmpty()) {
                reals.forEach(real -> matches.add(match(real, null, MatchStatus.REAL_ONLY)));
            } else if (reals.size() == 1 && papers.size() == 1 && withinWindow(reals.getFirst(), papers.getFirst(), window)) {
                matches.add(match(reals.getFirst(), papers.getFirst(), MatchStatus.MATCHED));
            } else {
                addWindowedMatches(reals, papers, window, matches);
            }
        }
        return matches.stream()
            .sorted(Comparator.comparing(DiagnosticsMatch::marketId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(match -> match.selectionId() == null ? Long.MAX_VALUE : match.selectionId()))
            .toList();
    }

    private void addWindowedMatches(
        List<RealBetDiagnosticRow> reals,
        List<PaperTrade> papers,
        Duration window,
        List<DiagnosticsMatch> matches
    ) {
        if (reals.size() != papers.size()) {
            matches.add(match(reals.getFirst(), papers.getFirst(), MatchStatus.AMBIGUOUS));
            return;
        }
        Set<String> usedRealIds = new HashSet<>();
        Set<String> usedPaperIds = new HashSet<>();
        for (RealBetDiagnosticRow real : reals) {
            List<PaperTrade> candidates = papers.stream()
                .filter(paper -> !usedPaperIds.contains(paper.id()))
                .filter(paper -> withinWindow(real, paper, window))
                .toList();
            if (candidates.size() == 1) {
                PaperTrade paper = candidates.getFirst();
                usedRealIds.add(real.id());
                usedPaperIds.add(paper.id());
                matches.add(match(real, paper, MatchStatus.MATCHED));
            }
        }
        List<RealBetDiagnosticRow> remainingReals = reals.stream()
            .filter(real -> !usedRealIds.contains(real.id()))
            .toList();
        List<PaperTrade> remainingPapers = papers.stream()
            .filter(paper -> !usedPaperIds.contains(paper.id()))
            .toList();
        if (!remainingReals.isEmpty() && !remainingPapers.isEmpty()) {
            matches.add(match(remainingReals.getFirst(), remainingPapers.getFirst(), MatchStatus.AMBIGUOUS));
            remainingReals.stream().skip(1).forEach(real -> matches.add(match(real, null, MatchStatus.REAL_ONLY)));
            remainingPapers.stream().skip(1).forEach(paper -> matches.add(match(null, paper, MatchStatus.PAPER_ONLY)));
        } else {
            remainingReals.forEach(real -> matches.add(match(real, null, MatchStatus.REAL_ONLY)));
            remainingPapers.forEach(paper -> matches.add(match(null, paper, MatchStatus.PAPER_ONLY)));
        }
    }

    private DiagnosticsMatch match(RealBetDiagnosticRow real, PaperTrade paper, MatchStatus status) {
        BigDecimal paperPnl = paper == null ? null : paper.netPnl();
        BigDecimal realPnl = real == null ? null : real.realizedProfitLoss();
        BigDecimal paperPerUnit = perUnit(paperPnl, paper == null ? null : paper.stake());
        BigDecimal realPerUnit = perUnit(realPnl, real == null ? null : real.selectedStake());
        return new DiagnosticsMatch(
            status,
            firstNonNull(real == null ? null : real.eventName(), paper == null ? null : paper.eventName()),
            firstNonNull(real == null ? null : real.marketId(), paper == null ? null : paper.marketId()),
            real == null ? (paper == null ? null : paper.selectionId()) : real.selectionId(),
            firstNonNull(real == null ? null : real.runnerName(), paper == null ? null : paper.runnerName()),
            real == null ? "UNKNOWN" : real.selectionSide().name(),
            firstNonNull(real == null ? null : real.competitionName(), paper == null ? null : paper.league()),
            real == null ? "N/A" : real.strategyName(),
            paper == null ? null : paper.recommendationTimestamp(),
            paper == null ? null : paper.executionTimestamp(),
            real == null ? null : real.createdAt(),
            paper == null ? null : paper.requestedOdds(),
            paper == null ? null : paper.executionOdds(),
            real == null ? null : real.recordedOdds(),
            real == null ? null : RealOddsSource.BET_INTENT,
            paper == null ? null : paper.closingOdds(),
            paper == null ? null : paper.stake(),
            real == null ? null : real.selectedStake(),
            paper == null || paper.result() == null ? null : paper.result().name(),
            real == null || real.settlementResult() == null ? null : real.settlementResult().name(),
            paperPnl,
            realPnl,
            realPnl != null && paperPnl != null ? money(realPnl.subtract(paperPnl)) : null,
            paperPerUnit,
            realPerUnit,
            realPerUnit != null && paperPerUnit != null ? decimal(realPerUnit.subtract(paperPerUnit)) : null
        );
    }

    private DiagnosticsCoverage coverage(DiagnosticsDataset dataset, List<DiagnosticsMatch> matches) {
        return new DiagnosticsCoverage(
            dataset.realBets().size(),
            dataset.paperTrades().size(),
            count(matches, MatchStatus.MATCHED),
            count(matches, MatchStatus.REAL_ONLY),
            count(matches, MatchStatus.PAPER_ONLY),
            count(matches, MatchStatus.AMBIGUOUS)
        );
    }

    private DiagnosticsPaperVsRealMetrics paperVsRealMetrics(List<DiagnosticsMatch> matches) {
        List<DiagnosticsMatch> matched = matches.stream().filter(match -> match.matchStatus() == MatchStatus.MATCHED).toList();
        List<BigDecimal> oddsDiffs = matched.stream()
            .map(match -> subtract(match.realRecordedOdds(), match.paperOdds()))
            .filter(Objects::nonNull)
            .toList();
        List<BigDecimal> slippages = matched.stream()
            .map(match -> rate(subtract(match.realRecordedOdds(), match.recommendedOdds()), match.recommendedOdds()))
            .filter(Objects::nonNull)
            .toList();
        List<DiagnosticsMatch> settled = matched.stream()
            .filter(match -> match.paperPnl() != null && match.realPnl() != null)
            .toList();
        BigDecimal paperPnl = sum(settled, DiagnosticsMatch::paperPnl);
        BigDecimal realPnl = sum(settled, DiagnosticsMatch::realPnl);
        BigDecimal paperStake = sum(settled, DiagnosticsMatch::paperStake);
        BigDecimal realStake = sum(settled, DiagnosticsMatch::realStake);
        BigDecimal paperPerUnit = perUnit(paperPnl, paperStake);
        BigDecimal realPerUnit = perUnit(realPnl, realStake);
        return new DiagnosticsPaperVsRealMetrics(
            matched.size(),
            settled.size(),
            average(oddsDiffs),
            median(oddsDiffs),
            average(slippages),
            median(slippages),
            money(paperPnl),
            money(realPnl),
            settled.isEmpty() ? null : money(realPnl.subtract(paperPnl)),
            paperPerUnit,
            realPerUnit,
            rate(paperPnl, paperStake),
            rate(realPnl, realStake),
            paperPerUnit != null && realPerUnit != null ? decimal(realPerUnit.subtract(paperPerUnit)) : null,
            settled.stream().filter(match -> !Objects.equals(match.paperResult(), match.realResult())).count(),
            oddsDiffs.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.APPROXIMATED,
            settled.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.SQLITE_EXACT
        );
    }

    private DiagnosticsExecutionMetrics executionMetrics(
        List<RealBetDiagnosticRow> realBets,
        List<DiagnosticsMatch> matches,
        DiagnosticsLogSummary logs
    ) {
        List<Duration> latencies = realBets.stream()
            .map(RealBetDiagnosticRow::externalOrderId)
            .filter(id -> id != null && !id.isBlank())
            .map(logs.acceptedLatenciesByExternalOrderId()::get)
            .filter(Objects::nonNull)
            .sorted()
            .toList();
        List<BigDecimal> oddsDiffs = matches.stream()
            .filter(match -> match.matchStatus() == MatchStatus.MATCHED)
            .map(match -> subtract(match.realRecordedOdds(), match.paperOdds()))
            .filter(Objects::nonNull)
            .toList();
        return new DiagnosticsExecutionMetrics(
            logCount(logs, "order.submitted"),
            logCount(logs, "order.accepted"),
            0,
            realBets.stream().filter(row -> row.stage() == BetIntentStage.FAILED).count(),
            logCount(logs, "order.rejected"),
            realBets.stream().filter(row -> row.stage() == BetIntentStage.CANCELLED).count(),
            durationAverage(latencies),
            durationPercentile(latencies, 0.50),
            durationPercentile(latencies, 0.95),
            latencies.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.LOG_CORRELATED,
            average(oddsDiffs),
            oddsDiffs.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.APPROXIMATED,
            realBets.stream().filter(row -> row.recordedOdds() == null).count(),
            realBets.stream().filter(row -> row.externalOrderId() == null || row.externalOrderId().isBlank()).count()
        );
    }

    private DiagnosticsDecisionFunnel decisionFunnel(DiagnosticsDataset dataset, DiagnosticsLogSummary logs) {
        return new DiagnosticsDecisionFunnel(
            dataset.marketsScanned(),
            dataset.runnersAnalyzed(),
            dataset.signalRecommendations().values().stream().mapToLong(Long::longValue).sum(),
            dataset.rejectionReasons().values().stream().mapToLong(Long::longValue).sum(),
            logCount(logs, "risk.rejected"),
            logCount(logs, "confirmation.requested"),
            logCount(logs, "order.submitted"),
            logCount(logs, "order.accepted"),
            logCount(logs, "order.rejected"),
            logCount(logs, "order.settled"),
            dataset.rejectionReasons()
        );
    }

    private List<DiagnosticFinding> integrityFindings(DiagnosticsDataset dataset, List<DiagnosticsMatch> matches) {
        List<DiagnosticFinding> findings = new ArrayList<>();
        duplicateFindings("DUPLICATE_REAL_BETS", dataset.realBets(), row -> key(row.exchange(), row.marketId(), row.selectionId()), findings);
        duplicateFindings("DUPLICATE_PAPER_TRADES", dataset.paperTrades(), row -> key(row.exchange(), row.marketId(), row.selectionId()), findings);
        long settledWithoutPnl = dataset.realBets().stream()
            .filter(row -> row.stage() == BetIntentStage.SETTLED && row.realizedProfitLoss() == null)
            .count();
        addIfPositive(findings, "SETTLED_WITHOUT_PNL", "Settled real bets without realized PnL.", settledWithoutPnl);
        addIfPositive(findings, "WINNING_BET_NEGATIVE_PNL", "Winning real bets with negative PnL.", dataset.realBets().stream()
            .filter(row -> row.settlementResult() == BetSettlementResult.WIN)
            .filter(row -> row.realizedProfitLoss() != null && row.realizedProfitLoss().compareTo(BigDecimal.ZERO) < 0)
            .count());
        addIfPositive(findings, "LOSING_BET_POSITIVE_PNL", "Losing real bets with positive PnL.", dataset.realBets().stream()
            .filter(row -> row.settlementResult() == BetSettlementResult.LOSE)
            .filter(row -> row.realizedProfitLoss() != null && row.realizedProfitLoss().compareTo(BigDecimal.ZERO) > 0)
            .count());
        addIfPositive(findings, "OPEN_BETS_WITH_SETTLED_AT", "Open real bets with settled_at.", dataset.realBets().stream()
            .filter(row -> row.stage() != BetIntentStage.SETTLED && row.settledAt() != null)
            .count());
        addIfPositive(findings, "SETTLED_BETS_WITHOUT_SETTLED_AT", "Settled real bets without settled_at.", dataset.realBets().stream()
            .filter(row -> row.stage() == BetIntentStage.SETTLED && row.settledAt() == null)
            .count());
        addIfPositive(findings, "MISSING_SELECTION_SIDE_NEW_RECORDS", "New real records missing selection_side.", dataset.realBets().stream()
            .filter(this::isNewRecord)
            .filter(row -> row.selectionSide() == SelectionSide.UNKNOWN)
            .count());
        addIfPositive(findings, "PAPER_REAL_RESULT_MISMATCH", "Matched settled paper and real results differ.", matches.stream()
            .filter(match -> match.matchStatus() == MatchStatus.MATCHED)
            .filter(match -> match.paperResult() != null && match.realResult() != null)
            .filter(match -> !Objects.equals(match.paperResult(), match.realResult()))
            .count());
        return findings;
    }

    private boolean isNewRecord(RealBetDiagnosticRow row) {
        return row.createdAt() != null && !row.createdAt().isBefore(NEW_METADATA_CUTOFF);
    }

    private List<String> limitations(DiagnosticsLogSummary logs) {
        List<String> values = new ArrayList<>();
        values.add("bet_intents.odds is reported as realRecordedOdds with source BET_INTENT; diagnostics does not assume it is executed odds.");
        values.add("Real execution latency is calculated only from correlated order.submitted and order.accepted JSONL events.");
        values.add("Fully matched, partially matched, unmatched, rejected, and cancelled execution counts are read from structured logs when available; SQLite bet_intents does not persist partial-fill detail.");
        values.add("paper_trades does not persist strategy_name, so strategy is not required for paper-real matching.");
        values.add("Real-vs-paper odds metrics are APPROXIMATED because the real persisted odds source is bet_intents, not a proven executed price.");
        values.addAll(logs.limitations());
        if (logs.invalidLines() > 0) {
            values.add("Ignored invalid JSONL lines: " + logs.invalidLines() + ".");
        }
        return values;
    }

    private List<String> topFindings(
        DiagnosticsCoverage coverage,
        DiagnosticsPaperVsRealMetrics paperVsReal,
        DiagnosticsExecutionMetrics execution,
        List<DiagnosticFinding> findings
    ) {
        List<String> values = new ArrayList<>();
        values.add("Matched paper-real pairs: " + coverage.matchedPairs() + " observations.");
        if (paperVsReal.averageRealVsPaperOddsDifference() != null) {
            values.add("Average real recorded vs paper odds difference is "
                + paperVsReal.averageRealVsPaperOddsDifference()
                + " across "
                + paperVsReal.matchedPairs()
                + " matched observations.");
        }
        if (execution.averageExecutionLatency() == null) {
            values.add("Execution latency is unavailable because no reliable log-correlated observations were found.");
        } else {
            values.add("Average execution latency is " + execution.averageExecutionLatency() + " across correlated log observations.");
        }
        findings.stream()
            .limit(3)
            .map(finding -> finding.message() + " Observations: " + finding.observations() + ".")
            .forEach(values::add);
        return values;
    }

    private static <T> void duplicateFindings(
        String code,
        List<T> rows,
        Function<T, String> classifier,
        List<DiagnosticFinding> findings
    ) {
        long duplicates = rows.stream()
            .collect(Collectors.groupingBy(classifier, Collectors.counting()))
            .values()
            .stream()
            .filter(count -> count > 1)
            .count();
        addIfPositive(findings, code, code.replace('_', ' ').toLowerCase(java.util.Locale.ROOT) + ".", duplicates);
    }

    private static void addIfPositive(List<DiagnosticFinding> findings, String code, String message, long observations) {
        if (observations > 0) {
            findings.add(new DiagnosticFinding(DiagnosticsModel.DiagnosticFindingSeverity.WARNING, code, message, observations));
        }
    }

    private static int count(List<DiagnosticsMatch> matches, MatchStatus status) {
        return (int) matches.stream().filter(match -> match.matchStatus() == status).count();
    }

    private static List<RealBetDiagnosticRow> sortedReal(List<RealBetDiagnosticRow> rows) {
        return rows.stream()
            .sorted(Comparator.comparing(RealBetDiagnosticRow::sortTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private static List<PaperTrade> sortedPaper(List<PaperTrade> rows) {
        return rows.stream()
            .sorted(Comparator.comparing(PaperTrade::recommendationTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private static boolean withinWindow(RealBetDiagnosticRow real, PaperTrade paper, Duration window) {
        if (real.sortTimestamp() == null || paper.recommendationTimestamp() == null) {
            return true;
        }
        Duration distance = Duration.between(paper.recommendationTimestamp(), real.sortTimestamp()).abs();
        return distance.compareTo(window) <= 0;
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        return decimal(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), SCALE + 2, RoundingMode.HALF_UP));
    }

    private static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return decimal(sorted.get(middle));
        }
        return decimal(sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2), SCALE + 2, RoundingMode.HALF_UP));
    }

    private static Duration durationAverage(List<Duration> values) {
        if (values.isEmpty()) {
            return null;
        }
        long nanos = values.stream().mapToLong(Duration::toNanos).sum() / values.size();
        return Duration.ofNanos(nanos);
    }

    private static Duration durationPercentile(List<Duration> values, double percentile) {
        if (values.isEmpty()) {
            return null;
        }
        int index = (int) Math.ceil(values.size() * percentile) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private static BigDecimal sum(List<DiagnosticsMatch> rows, Function<DiagnosticsMatch, BigDecimal> value) {
        return rows.stream().map(value).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? null : decimal(left.subtract(right));
    }

    private static BigDecimal perUnit(BigDecimal pnl, BigDecimal stake) {
        return rate(pnl, stake);
    }

    private static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return decimal(numerator.divide(denominator, SCALE + 2, RoundingMode.HALF_UP));
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static long logCount(DiagnosticsLogSummary logs, String event) {
        return logs.eventCounts().getOrDefault(event, 0L);
    }

    private static String firstNonNull(String left, String right) {
        return left != null && !left.isBlank() ? left : right;
    }

    private static String key(String exchange, String marketId, long selectionId) {
        return exchange + "|" + marketId + "|" + selectionId;
    }

    private record Key(String exchange, String marketId, long selectionId) implements Comparable<Key> {
        private static Key from(RealBetDiagnosticRow row) {
            return new Key(row.exchange(), row.marketId(), row.selectionId());
        }

        private static Key from(PaperTrade row) {
            return new Key(row.exchange(), row.marketId(), row.selectionId());
        }

        @Override
        public int compareTo(Key other) {
            int exchangeCompare = nullSafe(exchange).compareTo(nullSafe(other.exchange));
            if (exchangeCompare != 0) {
                return exchangeCompare;
            }
            int marketCompare = nullSafe(marketId).compareTo(nullSafe(other.marketId));
            if (marketCompare != 0) {
                return marketCompare;
            }
            return Long.compare(selectionId, other.selectionId);
        }

        private static String nullSafe(String value) {
            return value == null ? "" : value;
        }
    }
}
