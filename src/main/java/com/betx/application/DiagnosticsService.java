package com.betx.application;

import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import com.betx.application.DiagnosticsModel.DiagnosticsDataset;
import com.betx.application.DiagnosticsModel.DiagnosticsRequest;
import com.betx.application.DiagnosticsModel.MatchGapReason;
import com.betx.application.DiagnosticsModel.MatchProvenance;
import com.betx.application.DiagnosticsModel.MatchStatus;
import com.betx.application.DiagnosticsModel.RealBetDiagnosticRow;
import com.betx.application.DiagnosticsModel.RealOddsSource;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.order.BetExecutionStatus;
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
        DiagnosticsExecutionDataCoverage executionDataCoverage = executionDataCoverage(dataset.realBets());
        DiagnosticsLogEventCoverage logEventCoverage = logEventCoverage(logs);
        DiagnosticsPersistedExecutionCoverage persistedExecutionCoverage = persistedExecutionCoverage(dataset.realBets());
        DiagnosticsPlaceOrdersResponseDuration placeOrdersResponseDuration = placeOrdersResponseDuration(dataset.realBets());
        DiagnosticsProspectiveRealBettingCohort prospectiveRealBettingCohort = prospectiveRealBettingCohort(dataset.realBets());
        DiagnosticsDecisionFunnel funnel = decisionFunnel(dataset, logs);
        List<DiagnosticFinding> findings = integrityFindings(dataset, matches);
        List<String> limitations = limitations(logs, persistedExecutionCoverage, logEventCoverage);
        List<String> topFindings = topFindings(coverage, paperVsReal, execution, findings);
        Map<MatchGapReason, Long> matchingGaps = matchingGaps(matches);
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
            matches,
            matchingGaps,
            executionDataCoverage,
            logEventCoverage,
            persistedExecutionCoverage,
            placeOrdersResponseDuration,
            prospectiveRealBettingCohort,
            logs.topSkippedMarkets(),
            dataset.betRecommendations()
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
                papers.forEach(paper -> matches.add(match(
                    null,
                    paper,
                    MatchStatus.PAPER_ONLY,
                    MatchGapReason.NO_REAL_WITH_SAME_MARKET_SELECTION,
                    0,
                    null
                )));
            } else if (papers.isEmpty()) {
                reals.forEach(real -> matches.add(match(
                    real,
                    null,
                    MatchStatus.REAL_ONLY,
                    MatchGapReason.NO_PAPER_WITH_SAME_MARKET_SELECTION,
                    0,
                    null
                )));
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
            matches.add(match(
                reals.getFirst(),
                papers.getFirst(),
                MatchStatus.AMBIGUOUS,
                reals.size() > papers.size() ? MatchGapReason.MULTIPLE_REAL_CANDIDATES : MatchGapReason.MULTIPLE_PAPER_CANDIDATES,
                reals.size() + papers.size(),
                nearestDistance(reals, papers)
            ));
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
            matches.add(match(
                remainingReals.getFirst(),
                remainingPapers.getFirst(),
                MatchStatus.AMBIGUOUS,
                MatchGapReason.MULTIPLE_VALID_CANDIDATES,
                remainingReals.size() + remainingPapers.size(),
                nearestDistance(remainingReals, remainingPapers)
            ));
            remainingReals.stream().skip(1).forEach(real -> matches.add(match(
                real,
                null,
                MatchStatus.REAL_ONLY,
                MatchGapReason.MULTIPLE_PAPER_CANDIDATES,
                papers.size(),
                nearestDistance(List.of(real), papers)
            )));
            remainingPapers.stream().skip(1).forEach(paper -> matches.add(match(
                null,
                paper,
                MatchStatus.PAPER_ONLY,
                MatchGapReason.MULTIPLE_REAL_CANDIDATES,
                reals.size(),
                nearestDistance(reals, List.of(paper))
            )));
        } else {
            remainingReals.forEach(real -> matches.add(match(
                real,
                null,
                MatchStatus.REAL_ONLY,
                MatchGapReason.OUTSIDE_MATCH_WINDOW,
                papers.size(),
                nearestDistance(List.of(real), papers)
            )));
            remainingPapers.forEach(paper -> matches.add(match(
                null,
                paper,
                MatchStatus.PAPER_ONLY,
                MatchGapReason.OUTSIDE_MATCH_WINDOW,
                reals.size(),
                nearestDistance(reals, List.of(paper))
            )));
        }
    }

    private DiagnosticsMatch match(RealBetDiagnosticRow real, PaperTrade paper, MatchStatus status) {
        return match(real, paper, status, null, null, null);
    }

    private DiagnosticsMatch match(
        RealBetDiagnosticRow real,
        PaperTrade paper,
        MatchStatus status,
        MatchGapReason gapReason,
        Integer candidateCount,
        Duration nearestCandidateTimeDifference
    ) {
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
            realPerUnit != null && paperPerUnit != null ? decimal(realPerUnit.subtract(paperPerUnit)) : null,
            provenance(status),
            gapReason,
            candidateCount,
            nearestCandidateTimeDifference,
            firstNonNull(real == null ? null : real.recommendationId(), paper == null ? null : paper.recommendationId()),
            real == null ? null : real.evaluationId(),
            real == null ? null : real.recommendedAt(),
            real == null ? null : real.recommendedOdds(),
            real == null ? null : real.orderSubmittedAt(),
            real == null ? null : real.orderResponseAt(),
            real == null ? null : real.orderAcceptedAt(),
            real == null ? null : real.executedAt(),
            real == null ? null : real.requestedOdds(),
            real == null ? null : real.averageExecutedOdds(),
            real == null ? null : real.requestedStake(),
            real == null ? null : real.matchedStake(),
            real == null ? null : real.remainingStake(),
            real == null || real.executionStatus() == null ? null : real.executionStatus().name()
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
            oddsDiffs.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.LEGACY_APPROXIMATION,
            settled.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.SQLITE_EXACT
        );
    }

    private DiagnosticsExecutionDataCoverage executionDataCoverage(List<RealBetDiagnosticRow> realBets) {
        List<RealBetDiagnosticRow> prospective = prospective(realBets);
        return new DiagnosticsExecutionDataCoverage(
            realBets.size(),
            realBets.stream().filter(row -> row.evaluationId() != null).count(),
            realBets.stream().filter(row -> row.recommendationId() != null).count(),
            realBets.stream().filter(row -> row.orderSubmittedAt() != null).count(),
            realBets.stream().filter(row -> row.orderResponseAt() != null).count(),
            realBets.stream().filter(row -> row.orderAcceptedAt() != null).count(),
            realBets.stream().filter(row -> row.executedAt() != null).count(),
            realBets.stream().filter(row -> row.requestedOdds() != null).count(),
            realBets.stream().filter(row -> row.averageExecutedOdds() != null).count(),
            realBets.stream().filter(row -> row.requestedStake() != null).count(),
            realBets.stream().filter(row -> row.matchedStake() != null).count(),
            realBets.stream().filter(row -> row.remainingStake() != null).count(),
            realBets.stream().filter(row -> row.executionStatus() != null).count(),
            prospective.size(),
            prospective.stream().filter(row -> row.selectionSide() != SelectionSide.UNKNOWN).count(),
            prospective.stream().filter(row -> row.selectionSide() == SelectionSide.UNKNOWN).count(),
            realBets.stream().filter(row -> row.evaluationId() == null)
                .filter(row -> row.selectionSide() == SelectionSide.UNKNOWN)
                .count(),
            prospective.stream().filter(row -> !isMissing(row.strategyName())).count(),
            prospective.stream().filter(row -> !isMissing(row.competitionName())).count(),
            prospective.stream().filter(row -> row.requestedOdds() != null).count(),
            prospective.stream().filter(row -> row.requestedStake() != null).count(),
            prospective.stream().filter(row -> row.orderSubmittedAt() != null).count(),
            prospective.stream().filter(row -> row.orderResponseAt() != null).count()
        );
    }

    private DiagnosticsLogEventCoverage logEventCoverage(DiagnosticsLogSummary logs) {
        return new DiagnosticsLogEventCoverage(
            logCount(logs, "order.submitted"),
            logCount(logs, "order.response"),
            logCount(logs, "order.accepted"),
            logCount(logs, "order.rejected"),
            logCount(logs, "order.unmatched"),
            logCount(logs, "order.partially_matched"),
            logCount(logs, "order.matched"),
            logCount(logs, "order.settled"),
            logCount(logs, "bet_signal.skipped:ACTIVE_MARKET_INTENT_EXISTS"),
            logCount(logs, "bet_intent.skipped:DUPLICATE_REAL_BET"),
            logs.eventCounts().isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.LOG_CORRELATED
        );
    }

    private DiagnosticsPersistedExecutionCoverage persistedExecutionCoverage(List<RealBetDiagnosticRow> realBets) {
        return new DiagnosticsPersistedExecutionCoverage(
            realBets.size(),
            realBets.stream().filter(row -> row.orderSubmittedAt() != null).count(),
            realBets.stream().filter(row -> row.executedAt() != null).count(),
            realBets.stream().filter(row -> row.stage() == BetIntentStage.SETTLED).count(),
            realBets.stream().filter(row -> row.executionStatus() == BetExecutionStatus.FULLY_MATCHED).count(),
            realBets.stream().filter(row -> row.executionStatus() == BetExecutionStatus.PARTIALLY_MATCHED).count(),
            realBets.stream().filter(row -> row.executionStatus() == BetExecutionStatus.UNMATCHED).count(),
            realBets.stream().filter(row -> row.stage() == BetIntentStage.CANCELLED).count(),
            realBets.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.SQLITE_EXACT
        );
    }

    private DiagnosticsPlaceOrdersResponseDuration placeOrdersResponseDuration(List<RealBetDiagnosticRow> realBets) {
        List<RealBetDiagnosticRow> submitted = realBets.stream()
            .filter(row -> row.orderSubmittedAt() != null)
            .toList();
        List<Duration> durations = submitted.stream()
            .filter(row -> row.orderResponseAt() != null)
            .filter(row -> !row.orderResponseAt().isBefore(row.orderSubmittedAt()))
            .map(row -> Duration.between(row.orderSubmittedAt(), row.orderResponseAt()))
            .sorted()
            .toList();
        return new DiagnosticsPlaceOrdersResponseDuration(
            durations.size(),
            durationAverage(durations),
            durationPercentile(durations, 0.50),
            durationPercentile(durations, 0.95),
            durations.isEmpty() ? null : durations.getFirst(),
            durations.isEmpty() ? null : durations.getLast(),
            submitted.stream()
                .filter(row -> row.orderResponseAt() != null && row.orderResponseAt().isBefore(row.orderSubmittedAt()))
                .count(),
            submitted.stream().filter(row -> row.orderResponseAt() == null).count(),
            durations.stream().filter(duration -> duration.compareTo(Duration.ofSeconds(5)) > 0).count(),
            durations.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.SQLITE_EXACT
        );
    }

    private DiagnosticsProspectiveRealBettingCohort prospectiveRealBettingCohort(List<RealBetDiagnosticRow> realBets) {
        List<RealBetDiagnosticRow> prospective = prospective(realBets);
        List<RealBetDiagnosticRow> settled = prospective.stream().filter(RealBetDiagnosticRow::settledWithPnl).toList();
        BigDecimal turnover = sumReal(prospective, RealBetDiagnosticRow::selectedStake);
        BigDecimal pnl = sumReal(settled, RealBetDiagnosticRow::realizedProfitLoss);
        BigDecimal averageRequested = average(prospective.stream().map(RealBetDiagnosticRow::requestedOdds).filter(Objects::nonNull).toList());
        BigDecimal averageExecuted = average(prospective.stream().map(RealBetDiagnosticRow::averageExecutedOdds).filter(Objects::nonNull).toList());
        return new DiagnosticsProspectiveRealBettingCohort(
            prospective.size(),
            settled.size(),
            prospective.stream().filter(row -> row.stage() != BetIntentStage.SETTLED).count(),
            settled.stream().filter(row -> row.settlementResult() == BetSettlementResult.WIN).count(),
            settled.stream().filter(row -> row.settlementResult() == BetSettlementResult.LOSE).count(),
            money(turnover),
            money(pnl),
            rate(pnl, turnover),
            averageRequested,
            averageExecuted,
            subtract(averageExecuted, averageRequested),
            prospective.stream().filter(row -> row.executionStatus() == BetExecutionStatus.FULLY_MATCHED).count(),
            prospective.stream().filter(row -> row.executionStatus() == BetExecutionStatus.PARTIALLY_MATCHED).count(),
            prospective.stream().filter(row -> row.executionStatus() == BetExecutionStatus.UNMATCHED).count(),
            prospective.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.SQLITE_EXACT
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
            logCount(logs, "order.matched"),
            logCount(logs, "order.partially_matched"),
            logCount(logs, "order.unmatched"),
            logCount(logs, "order.rejected"),
            realBets.stream().filter(row -> row.stage() == BetIntentStage.CANCELLED).count(),
            durationAverage(latencies),
            durationPercentile(latencies, 0.50),
            durationPercentile(latencies, 0.95),
            latencies.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.LOG_CORRELATED,
            average(oddsDiffs),
            oddsDiffs.isEmpty() ? DiagnosticsDataProvenance.UNAVAILABLE : DiagnosticsDataProvenance.LEGACY_APPROXIMATION,
            realBets.stream().filter(row -> row.recordedOdds() == null).count(),
            realBets.stream().filter(row -> row.externalOrderId() == null || row.externalOrderId().isBlank()).count()
        );
    }

    private DiagnosticsDecisionFunnel decisionFunnel(DiagnosticsDataset dataset, DiagnosticsLogSummary logs) {
        long rejectedEvaluations = dataset.rejectionReasons().entrySet().stream()
            .filter(entry -> !"ACCEPTED".equals(entry.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
        return new DiagnosticsDecisionFunnel(
            dataset.marketsScanned(),
            dataset.runnersAnalyzed(),
            dataset.signalRecommendations().values().stream().mapToLong(Long::longValue).sum(),
            rejectedEvaluations,
            logCount(logs, "risk.rejected"),
            logCount(logs, "confirmation.requested"),
            logCount(logs, "order.submitted"),
            logCount(logs, "order.response") + logCount(logs, "order.accepted"),
            logCount(logs, "order.rejected"),
            logCount(logs, "order.settled"),
            dataset.rejectionReasons().entrySet().stream()
                .filter(entry -> !"ACCEPTED".equals(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
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
        addIfPositive(findings, "MISSING_SELECTION_SIDE_PROSPECTIVE_RECORDS", "Prospective real records missing selection_side.", dataset.realBets().stream()
            .filter(this::isProspective)
            .filter(row -> row.selectionSide() == SelectionSide.UNKNOWN)
            .count());
        addIfPositive(findings, "MISSING_STRATEGY_NAME_PROSPECTIVE_RECORDS", "Prospective real records missing strategy_name.", dataset.realBets().stream()
            .filter(this::isProspective)
            .filter(row -> isMissing(row.strategyName()))
            .count());
        addIfPositive(findings, "MISSING_COMPETITION_NAME_PROSPECTIVE_RECORDS", "Prospective real records missing competition_name.", dataset.realBets().stream()
            .filter(this::isProspective)
            .filter(row -> isMissing(row.competitionName()))
            .count());
        addIfPositive(findings, "MISSING_REQUESTED_ODDS_PROSPECTIVE_RECORDS", "Prospective real records missing requested_odds.", dataset.realBets().stream()
            .filter(this::isProspective)
            .filter(row -> row.requestedOdds() == null)
            .count());
        addIfPositive(findings, "MISSING_REQUESTED_STAKE_PROSPECTIVE_RECORDS", "Prospective real records missing requested_stake.", dataset.realBets().stream()
            .filter(this::isProspective)
            .filter(row -> row.requestedStake() == null)
            .count());
        addIfPositive(findings, "MISSING_ORDER_SUBMITTED_AT_PROSPECTIVE_RECORDS", "Prospective real records missing order_submitted_at.", dataset.realBets().stream()
            .filter(this::isProspective)
            .filter(row -> row.orderSubmittedAt() == null)
            .count());
        addIfPositive(findings, "MISSING_ORDER_RESPONSE_AT_PROSPECTIVE_RECORDS", "Prospective real records missing order_response_at.", dataset.realBets().stream()
            .filter(this::isProspective)
            .filter(row -> row.orderResponseAt() == null)
            .count());
        addIfPositive(findings, "PAPER_REAL_RESULT_MISMATCH", "Matched settled paper and real results differ.", matches.stream()
            .filter(match -> match.matchStatus() == MatchStatus.MATCHED)
            .filter(match -> match.paperResult() != null && match.realResult() != null)
            .filter(match -> !Objects.equals(match.paperResult(), match.realResult()))
            .count());
        return findings;
    }

    private boolean isProspective(RealBetDiagnosticRow row) {
        return row.evaluationId() != null && !row.evaluationId().isBlank();
    }

    private List<String> limitations(
        DiagnosticsLogSummary logs,
        DiagnosticsPersistedExecutionCoverage persistedExecutionCoverage,
        DiagnosticsLogEventCoverage logEventCoverage
    ) {
        List<String> values = new ArrayList<>();
        values.add("bet_intents.odds is reported as realRecordedOdds with source BET_INTENT; diagnostics does not assume it is executed odds.");
        values.add("Real execution latency is unavailable for new order.response logs; legacy order.accepted correlations are kept only for historical compatibility.");
        values.add("Exact persisted execution fields are used only when present; legacy rows may not include partial-fill detail.");
        values.add("paper_trades does not persist strategy_name, so strategy is not required for paper-real matching.");
        values.add("Real-vs-paper odds metrics are APPROXIMATED because the real persisted odds source is bet_intents, not a proven executed price.");
        values.add("BetRecommendation is canonicalized in shadow mode but is not yet consumed by paper or real betting.");
        if (persistedExecutionCoverage.betsWithOrderSubmittedAt() > logEventCoverage.orderSubmittedEvents()) {
            values.add("LOG_SQLITE_ORDER_EVENT_COVERAGE_MISMATCH: SQLite orders with order_submitted_at: "
                + persistedExecutionCoverage.betsWithOrderSubmittedAt()
                + "; log order.submitted events: "
                + logEventCoverage.orderSubmittedEvents()
                + ".");
        }
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
            .mapToLong(count -> count - 1)
            .sum();
        addIfPositive(findings, code, code.replace('_', ' ').toLowerCase(java.util.Locale.ROOT) + " extra rows.", duplicates);
    }

    private static void addIfPositive(List<DiagnosticFinding> findings, String code, String message, long observations) {
        if (observations > 0) {
            findings.add(new DiagnosticFinding(DiagnosticsModel.DiagnosticFindingSeverity.WARNING, code, message, observations));
        }
    }

    private static int count(List<DiagnosticsMatch> matches, MatchStatus status) {
        return (int) matches.stream().filter(match -> match.matchStatus() == status).count();
    }

    private static Map<MatchGapReason, Long> matchingGaps(List<DiagnosticsMatch> matches) {
        return matches.stream()
            .map(DiagnosticsMatch::matchGapReason)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), java.util.LinkedHashMap::new, Collectors.counting()));
    }

    private static MatchProvenance provenance(MatchStatus status) {
        return switch (status) {
            case MATCHED -> MatchProvenance.LEGACY_MARKET_SELECTION_TIME;
            case AMBIGUOUS -> MatchProvenance.AMBIGUOUS;
            case REAL_ONLY, PAPER_ONLY -> MatchProvenance.UNMATCHED;
        };
    }

    private static Duration nearestDistance(List<RealBetDiagnosticRow> reals, List<PaperTrade> papers) {
        Duration nearest = null;
        for (RealBetDiagnosticRow real : reals) {
            for (PaperTrade paper : papers) {
                if (real.sortTimestamp() == null || paper.recommendationTimestamp() == null) {
                    continue;
                }
                Duration distance = Duration.between(paper.recommendationTimestamp(), real.sortTimestamp()).abs();
                if (nearest == null || distance.compareTo(nearest) < 0) {
                    nearest = distance;
                }
            }
        }
        return nearest;
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

    private static BigDecimal sumReal(List<RealBetDiagnosticRow> rows, Function<RealBetDiagnosticRow, BigDecimal> value) {
        return rows.stream().map(value).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<RealBetDiagnosticRow> prospective(List<RealBetDiagnosticRow> rows) {
        return rows.stream()
            .filter(row -> row.evaluationId() != null && !row.evaluationId().isBlank())
            .toList();
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

    private static boolean isMissing(String value) {
        return value == null || value.isBlank() || "N/A".equals(value);
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
