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
import com.betx.domain.staking.StakeSizingAdjustment;
import com.betx.domain.staking.StakeSizingContext;
import com.betx.domain.staking.StakeSizingDecision;
import com.betx.domain.staking.StakeSizingEngine;
import com.betx.domain.staking.StakeSizingMode;
import com.betx.domain.staking.StakeSizingRiskProfile;
import com.betx.domain.staking.StakeSizingSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DiagnosticsService implements GenerateDiagnosticsUseCase {
    private static final int SCALE = 8;
    private static final Duration DIVERGENCE_LOG_WINDOW = Duration.ofMinutes(2);

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
        Map<MatchGapReason, Long> matchingGaps = matchingGaps(matches);
        DiagnosticsRecommendationReadiness recommendationReadiness = recommendationReadiness(
            dataset.recommendationReadiness(),
            dataset.betRecommendations(),
            logEventCoverage
        );
        DiagnosticsRecommendationIdMatchingPreview recommendationIdMatchingPreview = recommendationIdMatchingPreview(
            dataset,
            request.matchWindow()
        );
        DiagnosticsRecommendationDivergenceAnalysis recommendationDivergenceAnalysis = recommendationDivergenceAnalysis(dataset, logs);
        DiagnosticsStrategyPerformance strategyPerformance = strategyPerformance(dataset.realBets(), matches);
        DiagnosticsCandidateFilterSimulation candidateFilterSimulation = candidateFilterSimulation(dataset.realBets(), strategyPerformance.allTime());
        DiagnosticsCandidateFilterShadowValidation candidateFilterShadowValidation = candidateFilterShadowValidation(dataset);
        DiagnosticsStakeSizingShadowDiagnostics stakeSizingShadowDiagnostics = stakeSizingShadowDiagnostics(
            dataset,
            logs,
            Instant.now(clock)
        );
        DiagnosticsStakeSizingScenarioSimulation stakeSizingScenarioSimulation = stakeSizingScenarioSimulation(dataset);
        List<DiagnosticFinding> findings = integrityFindings(dataset, matches);
        List<String> limitations = limitations(logs, persistedExecutionCoverage, logEventCoverage);
        List<String> topFindings = topFindings(
            coverage,
            paperVsReal,
            execution,
            strategyPerformance,
            candidateFilterSimulation,
            stakeSizingShadowDiagnostics,
            findings
        );
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
            dataset.betRecommendations(),
            dataset.paperRecommendationCoverage(),
            recommendationReadiness,
            recommendationIdMatchingPreview,
            recommendationDivergenceAnalysis,
            strategyPerformance,
            candidateFilterSimulation,
            candidateFilterShadowValidation,
            stakeSizingShadowDiagnostics,
            stakeSizingScenarioSimulation
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

    private DiagnosticsRecommendationReadiness recommendationReadiness(
        DiagnosticsRecommendationReadiness base,
        DiagnosticsBetRecommendationsSummary betRecommendations,
        DiagnosticsLogEventCoverage logEventCoverage
    ) {
        List<String> reasons = new ArrayList<>();
        boolean hasPaperSample = base.paperTradesWithRecommendationId() > 0;
        if (!hasPaperSample) {
            reasons.add("Insufficient post-2.3 paper sample with recommendation_id.");
        }
        if (base.paperTradesMissingRecommendationIdPost23() > 0) {
            reasons.add("Some post-2.3 paper trades are missing recommendation_id.");
        }
        if (base.brokenPaperRecommendationJoins() > 0) {
            reasons.add("Some paper recommendation_id values do not resolve to BetRecommendation.");
        }
        if (betRecommendations.duplicateCanonicalGroups() > 0) {
            reasons.add("Duplicate canonical recommendation groups exist.");
        }
        if (logEventCoverage.orderSubmittedEvents() > 0 && logEventCoverage.orderResponseEvents() == 0) {
            reasons.add("order.response logs are missing while order.submitted logs exist.");
        }
        if (base.post25RealBetsWithoutRecommendationId() > 0) {
            reasons.add("Some post-2.5 real bets are missing recommendation_id.");
        }
        if (base.realBetsWithRecommendationIdButMissingBetRecommendation() > 0) {
            reasons.add("Some real recommendation_id values do not resolve to BetRecommendation.");
        }
        boolean hasRealSample = base.post25RealBets() > 0;
        String realConsumption = reasons.isEmpty() ? "YES" : hasPaperSample ? "PARTIAL" : "PARTIAL";
        List<String> finalReasons = new ArrayList<>(reasons);
        if (!hasRealSample) {
            finalReasons.add("No post-2.5 real bet sample with recommendation_id is available yet.");
        }
        String recommendationIdMatching = hasRealSample && base.post25RealBetsWithoutRecommendationId() == 0
            && base.realBetsWithRecommendationIdButMissingBetRecommendation() == 0
            ? "PARTIAL"
            : "NO";
        if (recommendationIdMatching.equals("PARTIAL")) {
            finalReasons.add("Recommendation-id matching preview is available but not official. Need more prospective sample and conflict analysis before enabling.");
        }
        return base.withReadiness(
            realConsumption,
            recommendationIdMatching,
            recommendationIdMatching.equals("PARTIAL") ? "RECOMMENDATION_ID_MATCHING_CANDIDATE" :
                realConsumption.equals("YES") ? "READY_FOR_REAL_CONSUMPTION" : "PARTIAL",
            finalReasons
        );
    }

    private DiagnosticsRecommendationIdMatchingPreview recommendationIdMatchingPreview(
        DiagnosticsDataset dataset,
        Duration window
    ) {
        DiagnosticsRecommendationIdMatchingScope allTime = recommendationIdMatchingScope(
            "all-time",
            null,
            dataset,
            window
        );
        Instant post25Cutoff = dataset.realBets().stream()
            .filter(row -> !isMissing(row.recommendationId()))
            .map(RealBetDiagnosticRow::createdAt)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);
        DiagnosticsRecommendationIdMatchingScope post25 = post25Cutoff == null
            ? DiagnosticsRecommendationIdMatchingScope.empty()
            : recommendationIdMatchingScope("post-2.5", post25Cutoff, dataset, window);
        return new DiagnosticsRecommendationIdMatchingPreview(
            allTime.paperTradesEligible() > 0 || allTime.realBetsEligible() > 0,
            false,
            allTime,
            post25
        );
    }

    private DiagnosticsRecommendationIdMatchingScope recommendationIdMatchingScope(
        String scope,
        Instant cutoff,
        DiagnosticsDataset dataset,
        Duration window
    ) {
        List<PaperTrade> papers = filterPapersForPreview(dataset.paperTrades(), cutoff);
        List<RealBetDiagnosticRow> reals = filterRealsForPreview(dataset.realBets(), cutoff);
        List<DiagnosticsMatch> scopedLegacyMatches = match(reals, papers, window);
        Map<String, List<PaperTrade>> papersByRecommendation = papers.stream()
            .filter(row -> !isMissing(row.recommendationId()))
            .collect(Collectors.groupingBy(PaperTrade::recommendationId));
        Map<String, List<RealBetDiagnosticRow>> realsByRecommendation = reals.stream()
            .filter(row -> !isMissing(row.recommendationId()))
            .collect(Collectors.groupingBy(RealBetDiagnosticRow::recommendationId));
        Set<String> recommendationIds = new HashSet<>();
        recommendationIds.addAll(papersByRecommendation.keySet());
        recommendationIds.addAll(realsByRecommendation.keySet());

        long matched = 0;
        long paperOnly = 0;
        long realOnly = 0;
        long manyPaperToOneReal = 0;
        long onePaperToManyReal = 0;
        long manyToMany = 0;
        Set<PreviewPair> recommendationPairs = new HashSet<>();
        Set<String> recommendationAmbiguousPaperIds = new HashSet<>();
        Set<String> recommendationAmbiguousRealIds = new HashSet<>();
        for (String recommendationId : recommendationIds) {
            List<PaperTrade> recommendationPapers = papersByRecommendation.getOrDefault(recommendationId, List.of());
            List<RealBetDiagnosticRow> recommendationReals = realsByRecommendation.getOrDefault(recommendationId, List.of());
            if (recommendationPapers.size() == 1 && recommendationReals.size() == 1) {
                matched++;
                recommendationPairs.add(PreviewPair.of(recommendationPapers.getFirst(), recommendationReals.getFirst()));
            } else if (recommendationPapers.size() == 1 && recommendationReals.isEmpty()) {
                paperOnly++;
            } else if (recommendationPapers.isEmpty() && recommendationReals.size() == 1) {
                realOnly++;
            } else if (recommendationPapers.size() > 1 && recommendationReals.size() == 1) {
                manyPaperToOneReal++;
                recommendationPapers.forEach(paper -> recommendationAmbiguousPaperIds.add(paper.id()));
                recommendationAmbiguousRealIds.add(recommendationReals.getFirst().id());
            } else if (recommendationPapers.size() == 1 && recommendationReals.size() > 1) {
                onePaperToManyReal++;
                recommendationAmbiguousPaperIds.add(recommendationPapers.getFirst().id());
                recommendationReals.forEach(real -> recommendationAmbiguousRealIds.add(real.id()));
            } else if (recommendationPapers.size() > 1 && recommendationReals.size() > 1) {
                manyToMany++;
                recommendationPapers.forEach(paper -> recommendationAmbiguousPaperIds.add(paper.id()));
                recommendationReals.forEach(real -> recommendationAmbiguousRealIds.add(real.id()));
            }
        }

        Set<PreviewPair> legacyPairs = legacyPairs(reals, papers, window);
        DiagnosticsRecommendationLegacyComparison comparison = compareLegacyAndRecommendation(
            legacyPairs,
            recommendationPairs,
            recommendationAmbiguousPaperIds,
            recommendationAmbiguousRealIds,
            scopedLegacyMatches
        );
        long recommendationsWithBoth = recommendationIds.stream()
            .filter(id -> !papersByRecommendation.getOrDefault(id, List.of()).isEmpty())
            .filter(id -> !realsByRecommendation.getOrDefault(id, List.of()).isEmpty())
            .count();
        long recommendationsWithPaperOnly = recommendationIds.stream()
            .filter(id -> !papersByRecommendation.getOrDefault(id, List.of()).isEmpty())
            .filter(id -> realsByRecommendation.getOrDefault(id, List.of()).isEmpty())
            .count();
        long recommendationsWithRealOnly = recommendationIds.stream()
            .filter(id -> papersByRecommendation.getOrDefault(id, List.of()).isEmpty())
            .filter(id -> !realsByRecommendation.getOrDefault(id, List.of()).isEmpty())
            .count();
        long totalRecommendations = "all-time".equals(scope)
            ? dataset.recommendationReadiness().totalCanonicalRecommendations()
            : recommendationIds.size();
        return new DiagnosticsRecommendationIdMatchingScope(
            scope,
            cutoff,
            papers.size(),
            papers.stream().filter(row -> !isMissing(row.recommendationId())).count(),
            papersByRecommendation.values().stream().mapToLong(List::size).sum(),
            reals.size(),
            reals.stream().filter(row -> !isMissing(row.recommendationId())).count(),
            realsByRecommendation.values().stream().mapToLong(List::size).sum(),
            recommendationsWithBoth,
            recommendationsWithPaperOnly,
            recommendationsWithRealOnly,
            Math.max(0, totalRecommendations - recommendationIds.size()),
            matched,
            paperOnly,
            realOnly,
            manyPaperToOneReal + onePaperToManyReal + manyToMany,
            manyPaperToOneReal,
            onePaperToManyReal,
            manyToMany,
            comparison
        );
    }

    private static DiagnosticsRecommendationLegacyComparison compareLegacyAndRecommendation(
        Set<PreviewPair> legacyPairs,
        Set<PreviewPair> recommendationPairs,
        Set<String> recommendationAmbiguousPaperIds,
        Set<String> recommendationAmbiguousRealIds,
        List<DiagnosticsMatch> legacyMatches
    ) {
        Map<String, String> recommendationByPaper = recommendationPairs.stream()
            .collect(Collectors.toMap(PreviewPair::paperId, PreviewPair::realId, (left, right) -> left));
        Map<String, String> recommendationByReal = recommendationPairs.stream()
            .collect(Collectors.toMap(PreviewPair::realId, PreviewPair::paperId, (left, right) -> left));
        long matchedByBoth = legacyPairs.stream().filter(recommendationPairs::contains).count();
        long conflicts = legacyPairs.stream()
            .filter(pair -> {
                String recommendationReal = recommendationByPaper.get(pair.paperId());
                String recommendationPaper = recommendationByReal.get(pair.realId());
                return recommendationReal != null && !recommendationReal.equals(pair.realId())
                    || recommendationPaper != null && !recommendationPaper.equals(pair.paperId());
            })
            .count();
        long legacyOnly = legacyPairs.size() - matchedByBoth - conflicts;
        long recommendationOnly = recommendationPairs.stream()
            .filter(pair -> !legacyPairs.contains(pair))
            .filter(pair -> legacyPairs.stream().noneMatch(legacy -> legacy.paperId().equals(pair.paperId()) || legacy.realId().equals(pair.realId())))
            .count();
        Set<String> legacyPaperIds = legacyPairs.stream().map(PreviewPair::paperId).collect(Collectors.toSet());
        Set<String> legacyRealIds = legacyPairs.stream().map(PreviewPair::realId).collect(Collectors.toSet());
        long legacyRealOnlyButRecommendationMatched = recommendationPairs.stream()
            .filter(pair -> !legacyPairs.contains(pair))
            .filter(pair -> !legacyRealIds.contains(pair.realId()))
            .count();
        long legacyPaperOnlyButRecommendationMatched = recommendationPairs.stream()
            .filter(pair -> !legacyPairs.contains(pair))
            .filter(pair -> !legacyPaperIds.contains(pair.paperId()))
            .count();
        long recommendationAmbiguousButLegacyMatched = legacyPairs.stream()
            .filter(pair -> recommendationAmbiguousPaperIds.contains(pair.paperId()) || recommendationAmbiguousRealIds.contains(pair.realId()))
            .count();
        long legacyAmbiguousResolvedByRecommendation = legacyMatches.stream()
            .filter(match -> match.matchStatus() == MatchStatus.AMBIGUOUS)
            .filter(match -> recommendationPairs.stream().anyMatch(pair ->
                Objects.equals(match.marketId(), pair.marketId())
                    && Objects.equals(match.selectionId(), pair.selectionId())))
            .count();
        return new DiagnosticsRecommendationLegacyComparison(
            legacyPairs.size(),
            recommendationPairs.size(),
            matchedByBoth,
            legacyOnly,
            recommendationOnly,
            conflicts,
            legacyRealOnlyButRecommendationMatched,
            legacyPaperOnlyButRecommendationMatched,
            legacyAmbiguousResolvedByRecommendation,
            recommendationAmbiguousButLegacyMatched
        );
    }

    private static Set<PreviewPair> legacyPairs(List<RealBetDiagnosticRow> realBets, List<PaperTrade> paperTrades, Duration window) {
        Map<Key, List<RealBetDiagnosticRow>> realByKey = realBets.stream().collect(Collectors.groupingBy(Key::from));
        Map<Key, List<PaperTrade>> paperByKey = paperTrades.stream().collect(Collectors.groupingBy(Key::from));
        Set<Key> keys = new HashSet<>();
        keys.addAll(realByKey.keySet());
        keys.addAll(paperByKey.keySet());
        Set<PreviewPair> pairs = new HashSet<>();
        for (Key key : keys) {
            List<RealBetDiagnosticRow> reals = sortedReal(realByKey.getOrDefault(key, List.of()));
            List<PaperTrade> papers = sortedPaper(paperByKey.getOrDefault(key, List.of()));
            if (reals.size() == 1 && papers.size() == 1 && withinWindow(reals.getFirst(), papers.getFirst(), window)) {
                pairs.add(PreviewPair.of(papers.getFirst(), reals.getFirst()));
            } else if (reals.size() == papers.size()) {
                Set<String> usedPapers = new HashSet<>();
                for (RealBetDiagnosticRow real : reals) {
                    List<PaperTrade> candidates = papers.stream()
                        .filter(paper -> !usedPapers.contains(paper.id()))
                        .filter(paper -> withinWindow(real, paper, window))
                        .toList();
                    if (candidates.size() == 1) {
                        PaperTrade paper = candidates.getFirst();
                        usedPapers.add(paper.id());
                        pairs.add(PreviewPair.of(paper, real));
                    }
                }
            }
        }
        return pairs;
    }

    private DiagnosticsRecommendationDivergenceAnalysis recommendationDivergenceAnalysis(
        DiagnosticsDataset dataset,
        DiagnosticsLogSummary logs
    ) {
        Map<String, List<PaperTrade>> papersByRecommendation = dataset.paperTrades().stream()
            .filter(row -> !isMissing(row.recommendationId()))
            .collect(Collectors.groupingBy(PaperTrade::recommendationId));
        Map<String, List<RealBetDiagnosticRow>> realsByRecommendation = dataset.realBets().stream()
            .filter(row -> !isMissing(row.recommendationId()))
            .collect(Collectors.groupingBy(RealBetDiagnosticRow::recommendationId));
        Set<String> recommendationIds = new HashSet<>();
        recommendationIds.addAll(papersByRecommendation.keySet());
        recommendationIds.addAll(realsByRecommendation.keySet());

        List<DiagnosticsRecommendationDivergenceExample> paperOnly = new ArrayList<>();
        List<DiagnosticsRecommendationDivergenceExample> realOnly = new ArrayList<>();
        long ambiguous = 0;
        for (String recommendationId : recommendationIds.stream().sorted().toList()) {
            List<PaperTrade> papers = papersByRecommendation.getOrDefault(recommendationId, List.of());
            List<RealBetDiagnosticRow> reals = realsByRecommendation.getOrDefault(recommendationId, List.of());
            if (papers.size() == 1 && reals.isEmpty()) {
                paperOnly.add(divergenceExample(recommendationId, papers, reals, "PAPER_ONLY", logs.events()));
            } else if (papers.isEmpty() && reals.size() == 1) {
                realOnly.add(divergenceExample(recommendationId, papers, reals, "REAL_ONLY", logs.events()));
            } else if (!papers.isEmpty() || !reals.isEmpty()) {
                if (!(papers.size() == 1 && reals.size() == 1)) {
                    ambiguous++;
                }
            }
        }

        Map<DiagnosticsRecommendationDivergenceReason, Long> paperBreakdown = reasonBreakdown(paperOnly);
        Map<DiagnosticsRecommendationDivergenceReason, Long> realBreakdown = reasonBreakdown(realOnly);
        return new DiagnosticsRecommendationDivergenceAnalysis(
            paperOnly.size(),
            realOnly.size(),
            ambiguous,
            paperBreakdown,
            realBreakdown,
            paperBreakdown.getOrDefault(DiagnosticsRecommendationDivergenceReason.UNKNOWN, 0L),
            realBreakdown.getOrDefault(DiagnosticsRecommendationDivergenceReason.UNKNOWN, 0L),
            paperOnly.stream().limit(10).toList(),
            realOnly.stream().limit(10).toList()
        );
    }

    private DiagnosticsRecommendationDivergenceExample divergenceExample(
        String recommendationId,
        List<PaperTrade> papers,
        List<RealBetDiagnosticRow> reals,
        String classification,
        List<DiagnosticsLogEvent> logEvents
    ) {
        DivergenceSubject subject = DivergenceSubject.from(recommendationId, papers, reals);
        EvidenceMatch evidence = evidenceFor(subject, classification, logEvents);
        return new DiagnosticsRecommendationDivergenceExample(
            recommendationId,
            subject.canonicalKey(),
            subject.eventName(),
            subject.runnerName(),
            subject.marketId(),
            subject.selectionId(),
            subject.selectionSide(),
            subject.strategyName(),
            subject.firstSeenAt(),
            subject.lastSeenAt(),
            papers.size(),
            reals.size(),
            classification,
            evidence.reason(),
            evidence.evidence()
        );
    }

    private EvidenceMatch evidenceFor(DivergenceSubject subject, String classification, List<DiagnosticsLogEvent> logEvents) {
        List<DiagnosticsLogEvent> exactEvents = logEvents.stream()
            .filter(event -> Objects.equals(event.recommendationId(), subject.recommendationId()))
            .toList();
        EvidenceMatch exact = classifyEvidence(subject, classification, exactEvents, DiagnosticsDataProvenance.STRUCTURED_LOGS);
        if (exact != null) {
            return exact;
        }
        List<DiagnosticsLogEvent> temporalEvents = logEvents.stream()
            .filter(event -> isMissing(event.recommendationId()))
            .filter(event -> sameMarketSelection(subject, event))
            .filter(event -> withinDivergenceWindow(subject, event.timestamp()))
            .toList();
        EvidenceMatch temporal = classifyEvidence(subject, classification, temporalEvents, DiagnosticsDataProvenance.LEGACY_APPROXIMATION);
        if (temporal != null) {
            return temporal;
        }
        DiagnosticsRecommendationDivergenceReason reason = "PAPER_ONLY".equals(classification)
            ? DiagnosticsRecommendationDivergenceReason.REAL_NOT_ATTEMPTED
            : DiagnosticsRecommendationDivergenceReason.PAPER_NOT_CREATED;
        String message = "PAPER_ONLY".equals(classification)
            ? "No real-side evidence was found for this recommendation."
            : "No paper-side evidence was found for this recommendation.";
        return new EvidenceMatch(
            reason,
            List.of(new DiagnosticsRecommendationDivergenceEvidence(
                "diagnostics.divergence",
                subject.firstSeenAt(),
                message,
                DiagnosticsDataProvenance.DIAGNOSTICS,
                subject.recommendationId()
            ))
        );
    }

    private static EvidenceMatch classifyEvidence(
        DivergenceSubject subject,
        String classification,
        List<DiagnosticsLogEvent> events,
        DiagnosticsDataProvenance source
    ) {
        for (DiagnosticsLogEvent event : events) {
            DiagnosticsRecommendationDivergenceReason reason = "PAPER_ONLY".equals(classification)
                ? realSideReason(event)
                : paperSideReason(event);
            if (reason != null) {
                return new EvidenceMatch(
                    reason,
                    List.of(new DiagnosticsRecommendationDivergenceEvidence(
                        event.eventName(),
                        event.timestamp(),
                        evidenceMessage(event),
                        source,
                        event.recommendationId() == null ? subject.recommendationId() : event.recommendationId()
                    ))
                );
            }
        }
        return null;
    }

    private static DiagnosticsRecommendationDivergenceReason realSideReason(DiagnosticsLogEvent event) {
        String reason = normalizeReason(event.reason());
        if ("risk.blocked".equals(event.eventName())) {
            if (reason.contains("max_open_positions")) {
                return DiagnosticsRecommendationDivergenceReason.REAL_BLOCKED_BY_RISK_MAX_OPEN_POSITIONS;
            }
            if (reason.contains("daily_loss")) {
                return DiagnosticsRecommendationDivergenceReason.REAL_BLOCKED_BY_RISK_DAILY_LOSS;
            }
        }
        if ("bet_signal.skipped".equals(event.eventName()) && reason.contains("active_market_intent_exists")) {
            return DiagnosticsRecommendationDivergenceReason.REAL_BLOCKED_BY_ACTIVE_MARKET_INTENT_EXISTS;
        }
        if ("bet_intent.skipped".equals(event.eventName())) {
            if (reason.contains("duplicate_real_bet")) {
                return DiagnosticsRecommendationDivergenceReason.REAL_BLOCKED_BY_DUPLICATE_GUARD;
            }
            if (reason.contains("disabled") || reason.contains("config")) {
                return DiagnosticsRecommendationDivergenceReason.REAL_SKIPPED_BY_CONFIG;
            }
        }
        if (reason.contains("confirmation")) {
            return DiagnosticsRecommendationDivergenceReason.REAL_REJECTED_BY_CONFIRMATION;
        }
        if ("dependency.error".equals(event.eventName()) || "market.scan.failed".equals(event.eventName())) {
            return DiagnosticsRecommendationDivergenceReason.REAL_DEPENDENCY_ERROR;
        }
        if ("order.rejected".equals(event.eventName()) || reason.contains("order_failed")) {
            return DiagnosticsRecommendationDivergenceReason.REAL_ORDER_FAILED;
        }
        if (reason.contains("market_closed")) {
            return DiagnosticsRecommendationDivergenceReason.MARKET_CLOSED_BEFORE_REAL;
        }
        return null;
    }

    private static DiagnosticsRecommendationDivergenceReason paperSideReason(DiagnosticsLogEvent event) {
        String reason = normalizeReason(event.reason());
        if ("paper_trade.execution_failed".equals(event.eventName())) {
            return DiagnosticsRecommendationDivergenceReason.PAPER_EXECUTION_FAILED;
        }
        if ("market.scan.failed".equals(event.eventName())) {
            return DiagnosticsRecommendationDivergenceReason.PAPER_MARKET_SCAN_FAILED;
        }
        if ("dependency.error".equals(event.eventName())) {
            if (reason.contains("paper_settlement") || reason.contains("market_data")) {
                return DiagnosticsRecommendationDivergenceReason.PAPER_SETTLEMENT_OR_MARKET_DATA_ERROR;
            }
            return DiagnosticsRecommendationDivergenceReason.PAPER_DEPENDENCY_ERROR;
        }
        if (reason.contains("disabled")) {
            return DiagnosticsRecommendationDivergenceReason.PAPER_DISABLED;
        }
        if (reason.contains("dedup")) {
            return DiagnosticsRecommendationDivergenceReason.PAPER_SKIPPED_BY_DEDUP;
        }
        if (reason.contains("market_closed")) {
            return DiagnosticsRecommendationDivergenceReason.MARKET_CLOSED_BEFORE_PAPER;
        }
        return null;
    }

    private static String evidenceMessage(DiagnosticsLogEvent event) {
        if (!isMissing(event.message())) {
            return event.message();
        }
        if (!isMissing(event.reason())) {
            return event.reason();
        }
        return event.eventName();
    }

    private static String normalizeReason(String reason) {
        return reason == null ? "" : reason.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean sameMarketSelection(DivergenceSubject subject, DiagnosticsLogEvent event) {
        return Objects.equals(subject.exchange(), event.exchange())
            && Objects.equals(subject.marketId(), event.marketId())
            && subject.selectionId() == event.selectionId();
    }

    private static boolean withinDivergenceWindow(DivergenceSubject subject, Instant timestamp) {
        if (timestamp == null || subject.firstSeenAt() == null || subject.lastSeenAt() == null) {
            return false;
        }
        return !timestamp.isBefore(subject.firstSeenAt().minus(DIVERGENCE_LOG_WINDOW))
            && !timestamp.isAfter(subject.lastSeenAt().plus(DIVERGENCE_LOG_WINDOW));
    }

    private static Map<DiagnosticsRecommendationDivergenceReason, Long> reasonBreakdown(
        List<DiagnosticsRecommendationDivergenceExample> examples
    ) {
        return examples.stream()
            .collect(Collectors.groupingBy(
                DiagnosticsRecommendationDivergenceExample::reason,
                java.util.LinkedHashMap::new,
                Collectors.counting()
            ));
    }

    private static List<PaperTrade> filterPapersForPreview(List<PaperTrade> rows, Instant cutoff) {
        return rows.stream()
            .filter(row -> cutoff == null || row.recommendationTimestamp() == null || !row.recommendationTimestamp().isBefore(cutoff))
            .toList();
    }

    private static List<RealBetDiagnosticRow> filterRealsForPreview(List<RealBetDiagnosticRow> rows, Instant cutoff) {
        return rows.stream()
            .filter(row -> cutoff == null || row.createdAt() == null || !row.createdAt().isBefore(cutoff))
            .toList();
    }

    private DiagnosticsStrategyPerformance strategyPerformance(
        List<RealBetDiagnosticRow> realBets,
        List<DiagnosticsMatch> matches
    ) {
        Map<String, String> matchingByRealId = matches.stream()
            .filter(match -> match.realRecordedTimestamp() != null || match.realRecordedOdds() != null)
            .collect(Collectors.toMap(
                match -> match.marketId() + "|" + match.selectionId() + "|" + match.realRecordedTimestamp(),
                match -> match.matchProvenance() == MatchProvenance.LEGACY_MARKET_SELECTION_TIME
                    ? "legacy matched"
                    : match.matchStatus().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                (left, right) -> left
            ));
        DiagnosticsStrategyPerformanceSegment allTime = performanceSegment("all-time", realBets);
        List<DiagnosticsStrategyPerformanceSegment> scopes = List.of(
            allTime,
            performanceSegment("post-2.5", sinceFirstRecommendationId(realBets)),
            performanceSegment("post-2.6", sinceFirstRecommendationId(realBets)),
            performanceSegment("last 25 settled", lastSettled(realBets, 25)),
            performanceSegment("last 50 settled", lastSettled(realBets, 50)),
            performanceSegment("last 100 settled", lastSettled(realBets, 100))
        );
        return new DiagnosticsStrategyPerformance(
            allTime,
            scopes,
            groupPerformance(realBets, row -> row.selectionSide().name()),
            groupPerformance(realBets, row -> oddsBucket(effectiveOdds(row))),
            groupPerformance(realBets, RealBetDiagnosticRow::competitionName),
            groupPerformance(realBets, RealBetDiagnosticRow::strategyName),
            groupPerformance(realBets, row -> row.createdAt() == null ? "UNKNOWN" : row.createdAt().atZone(ZoneOffset.UTC).toLocalDate().toString()),
            groupPerformance(realBets, row -> row.createdAt() == null ? "UNKNOWN" : row.createdAt().atZone(ZoneOffset.UTC).getYear()
                + "-" + "%02d".formatted(row.createdAt().atZone(ZoneOffset.UTC).getMonthValue())),
            groupPerformance(realBets, row -> row.createdAt() == null ? "UNKNOWN" : "%02d:00".formatted(row.createdAt().atZone(ZoneOffset.UTC).getHour())),
            Map.of("UNAVAILABLE", DiagnosticsStrategyPerformanceSegment.empty("UNAVAILABLE")),
            Map.of("UNAVAILABLE", DiagnosticsStrategyPerformanceSegment.empty("UNAVAILABLE")),
            groupPerformance(realBets, row -> matchingByRealId.getOrDefault(
                row.marketId() + "|" + row.selectionId() + "|" + row.createdAt(),
                isMissing(row.recommendationId()) ? "real-only" : "matched by recommendation_id preview"
            )),
            List.of(
                "Real separated commission is not persisted in bet_intents; net PnL is SQLITE_EXACT and gross/commission are unavailable.",
                "Real edge, confidence, liquidity, closing odds and CLV are not persisted for bet_intents, so those candidate filters are not simulated.",
                "market_start_time is not persisted for bet_intents, so time-to-event buckets are unavailable for real-bet performance."
            )
        );
    }

    private DiagnosticsCandidateFilterSimulation candidateFilterSimulation(
        List<RealBetDiagnosticRow> realBets,
        DiagnosticsStrategyPerformanceSegment baseline
    ) {
        List<RealBetDiagnosticRow> settled = settledRealBets(realBets);
        Set<String> negativeSides = negativeSegments(settled, row -> row.selectionSide().name());
        Set<String> negativeOddsBuckets = negativeSegments(settled, row -> oddsBucket(effectiveOdds(row)));
        List<CandidateFilter> filters = List.of(
            new CandidateFilter("EXCLUDE_DRAW", row -> row.selectionSide() != SelectionSide.DRAW),
            new CandidateFilter("EXCLUDE_AWAY", row -> row.selectionSide() != SelectionSide.AWAY),
            new CandidateFilter("EXCLUDE_DRAW_AND_AWAY", row -> row.selectionSide() != SelectionSide.DRAW && row.selectionSide() != SelectionSide.AWAY),
            new CandidateFilter("EXCLUDE_ODDS_5_PLUS", row -> lessThan(effectiveOdds(row), "5.00")),
            new CandidateFilter("EXCLUDE_ODDS_4_PLUS", row -> lessThan(effectiveOdds(row), "4.00")),
            new CandidateFilter("ONLY_HOME", row -> row.selectionSide() == SelectionSide.HOME),
            new CandidateFilter("ONLY_ODDS_1_50_TO_3_99", row -> oddsBetween(row, "1.50", "3.99")),
            new CandidateFilter("ONLY_ODDS_2_00_TO_3_99", row -> oddsBetween(row, "2.00", "3.99")),
            new CandidateFilter("EXCLUDE_DRAW_AND_ODDS_4_PLUS", row -> row.selectionSide() != SelectionSide.DRAW && lessThan(effectiveOdds(row), "4.00")),
            new CandidateFilter("EXCLUDE_NEGATIVE_SEGMENTS_CURRENT", row ->
                !negativeSides.contains(row.selectionSide().name()) && !negativeOddsBuckets.contains(oddsBucket(effectiveOdds(row))))
        );
        List<DiagnosticsCandidateFilterResult> results = filters.stream()
            .map(filter -> candidateFilterResult(filter, settled, baseline))
            .toList();
        return new DiagnosticsCandidateFilterSimulation(
            baseline,
            results,
            top(results, Comparator.comparing(DiagnosticsCandidateFilterResult::deltaPnl, Comparator.nullsFirst(Comparator.naturalOrder())).reversed()),
            top(results, Comparator.comparing(DiagnosticsCandidateFilterResult::includedRoi, Comparator.nullsFirst(Comparator.naturalOrder())).reversed()),
            top(results, Comparator.comparing(
                (DiagnosticsCandidateFilterResult result) -> subtract(result.baselineMaxDrawdown(), result.includedMaxDrawdown()),
                Comparator.nullsFirst(Comparator.naturalOrder())
            ).reversed()),
            top(results.stream()
                .filter(result -> result.status() == DiagnosticsCandidateFilterStatus.CANDIDATE
                    || result.status() == DiagnosticsCandidateFilterStatus.WEAK_EVIDENCE)
                .toList(), Comparator.comparing(DiagnosticsCandidateFilterResult::deltaPnl, Comparator.nullsFirst(Comparator.naturalOrder())).reversed()),
            top(results, Comparator.comparing(DiagnosticsCandidateFilterResult::volumeRetentionPct, Comparator.nullsLast(Comparator.naturalOrder()))),
            recommendation(results)
        );
    }

    private DiagnosticsStrategyPerformanceSegment performanceSegment(String name, List<RealBetDiagnosticRow> rows) {
        List<RealBetDiagnosticRow> settled = settledRealBets(rows);
        BigDecimal turnover = sumReal(settled, this::stake);
        BigDecimal netPnl = sumReal(settled, RealBetDiagnosticRow::realizedProfitLoss);
        List<BigDecimal> odds = settled.stream().map(this::effectiveOdds).filter(Objects::nonNull).toList();
        List<BigDecimal> stakes = settled.stream().map(this::stake).filter(Objects::nonNull).toList();
        long wins = settled.stream().filter(row -> row.settlementResult() == BetSettlementResult.WIN).count();
        long losses = settled.stream().filter(row -> row.settlementResult() == BetSettlementResult.LOSE).count();
        long voids = settled.stream().filter(row -> row.settlementResult() == BetSettlementResult.VOID).count();
        long decided = wins + losses;
        BigDecimal strikeRate = decided == 0
            ? null
            : decimal(BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(decided), SCALE + 2, RoundingMode.HALF_UP));
        BigDecimal positive = sumReal(settled.stream()
            .filter(row -> row.realizedProfitLoss() != null && row.realizedProfitLoss().compareTo(BigDecimal.ZERO) > 0)
            .toList(), RealBetDiagnosticRow::realizedProfitLoss);
        BigDecimal negative = sumReal(settled.stream()
            .filter(row -> row.realizedProfitLoss() != null && row.realizedProfitLoss().compareTo(BigDecimal.ZERO) < 0)
            .toList(), RealBetDiagnosticRow::realizedProfitLoss).abs();
        BigDecimal averageWin = wins == 0 ? null : money(positive.divide(BigDecimal.valueOf(wins), SCALE + 2, RoundingMode.HALF_UP));
        BigDecimal averageLoss = losses == 0 ? null : money(negative.divide(BigDecimal.valueOf(losses), SCALE + 2, RoundingMode.HALF_UP));
        return new DiagnosticsStrategyPerformanceSegment(
            name,
            rows.size(),
            settled.size(),
            rows.stream().filter(row -> row.stage() != BetIntentStage.SETTLED).count(),
            wins,
            losses,
            voids,
            rows.stream().filter(row -> row.stage() == BetIntentStage.CANCELLED).count(),
            strikeRate,
            average(odds),
            average(stakes) == null ? null : money(average(stakes)),
            money(turnover),
            null,
            null,
            money(netPnl),
            rate(netPnl, turnover),
            maxDrawdown(settled),
            currentDrawdown(settled),
            negative.compareTo(BigDecimal.ZERO) == 0 ? null : decimal(positive.divide(negative, SCALE + 2, RoundingMode.HALF_UP)),
            averageWin,
            averageLoss,
            strikeRate == null || strikeRate.compareTo(BigDecimal.ZERO) == 0
                ? null
                : decimal(BigDecimal.ONE.divide(strikeRate, SCALE + 2, RoundingMode.HALF_UP))
        );
    }

    private Map<String, DiagnosticsStrategyPerformanceSegment> groupPerformance(
        List<RealBetDiagnosticRow> rows,
        Function<RealBetDiagnosticRow, String> classifier
    ) {
        return rows.stream()
            .collect(Collectors.groupingBy(
                row -> {
                    String value = classifier.apply(row);
                    return value == null || value.isBlank() ? "UNKNOWN" : value;
                },
                LinkedHashMap::new,
                Collectors.toList()
            ))
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> performanceSegment(entry.getKey(), entry.getValue()),
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private DiagnosticsCandidateFilterResult candidateFilterResult(
        CandidateFilter filter,
        List<RealBetDiagnosticRow> settled,
        DiagnosticsStrategyPerformanceSegment baseline
    ) {
        List<RealBetDiagnosticRow> included = settled.stream().filter(filter.predicate()).toList();
        List<RealBetDiagnosticRow> excluded = settled.stream().filter(filter.predicate().negate()).toList();
        DiagnosticsStrategyPerformanceSegment includedMetrics = performanceSegment(filter.name(), included);
        BigDecimal volumeRetention = baseline.settled() == 0
            ? null
            : decimal(BigDecimal.valueOf(includedMetrics.settled())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(baseline.settled()), SCALE + 2, RoundingMode.HALF_UP));
        BigDecimal deltaPnl = subtract(includedMetrics.netPnl(), baseline.netPnl());
        BigDecimal deltaRoi = subtract(includedMetrics.roi(), baseline.roi());
        DiagnosticsCandidateFilterStatus status = candidateStatus(includedMetrics, baseline, deltaPnl, deltaRoi, volumeRetention);
        return new DiagnosticsCandidateFilterResult(
            filter.name(),
            "all-time",
            baseline.settled(),
            includedMetrics.settled(),
            excluded.size(),
            includedMetrics.turnover(),
            money(sumReal(excluded, this::stake)),
            includedMetrics.strikeRate(),
            includedMetrics.averageOdds(),
            includedMetrics.netPnl(),
            includedMetrics.roi(),
            includedMetrics.maxDrawdown(),
            baseline.netPnl(),
            baseline.roi(),
            baseline.maxDrawdown(),
            deltaPnl,
            deltaRoi,
            volumeRetention,
            status,
            riskNote(status, includedMetrics),
            sampleWarning(includedMetrics)
        );
    }

    private DiagnosticsCandidateFilterStatus candidateStatus(
        DiagnosticsStrategyPerformanceSegment included,
        DiagnosticsStrategyPerformanceSegment baseline,
        BigDecimal deltaPnl,
        BigDecimal deltaRoi,
        BigDecimal volumeRetention
    ) {
        if (included.settled() < 10) {
            return DiagnosticsCandidateFilterStatus.INSUFFICIENT_SAMPLE;
        }
        if (deltaPnl == null || deltaRoi == null || deltaPnl.compareTo(BigDecimal.ZERO) <= 0 || deltaRoi.compareTo(BigDecimal.ZERO) <= 0) {
            return DiagnosticsCandidateFilterStatus.REJECTED;
        }
        if (volumeRetention == null || volumeRetention.compareTo(BigDecimal.valueOf(50)) < 0) {
            return DiagnosticsCandidateFilterStatus.OVERFIT_RISK;
        }
        if (included.settled() < 30) {
            return DiagnosticsCandidateFilterStatus.WEAK_EVIDENCE;
        }
        BigDecimal drawdownDelta = subtract(baseline.maxDrawdown(), included.maxDrawdown());
        if (drawdownDelta != null && drawdownDelta.compareTo(BigDecimal.ZERO) < 0) {
            return DiagnosticsCandidateFilterStatus.WEAK_EVIDENCE;
        }
        return DiagnosticsCandidateFilterStatus.CANDIDATE;
    }

    private static String riskNote(DiagnosticsCandidateFilterStatus status, DiagnosticsStrategyPerformanceSegment included) {
        return switch (status) {
            case CANDIDATE -> "Diagnostics-only candidate; validate on more prospective sample before live use.";
            case WEAK_EVIDENCE -> "Improves historical metrics but evidence is still weak or drawdown is not clearly better.";
            case INSUFFICIENT_SAMPLE -> "Sample too small for statistical confidence.";
            case OVERFIT_RISK -> "Volume retained is too low; high overfit risk.";
            case REJECTED -> "Does not improve both PnL and ROI against baseline.";
        } + " Observations: " + included.settled() + ".";
    }

    private static String sampleWarning(DiagnosticsStrategyPerformanceSegment included) {
        if (included.settled() < 10) {
            return "sample size too small";
        }
        if (included.settled() < 30) {
            return "sample still small";
        }
        return "";
    }

    private DiagnosticsStrategyExperimentRecommendation recommendation(List<DiagnosticsCandidateFilterResult> results) {
        return results.stream()
            .filter(result -> result.status() == DiagnosticsCandidateFilterStatus.CANDIDATE
                || result.status() == DiagnosticsCandidateFilterStatus.WEAK_EVIDENCE)
            .max(Comparator.comparing(DiagnosticsCandidateFilterResult::deltaPnl, Comparator.nullsFirst(Comparator.naturalOrder())))
            .map(result -> new DiagnosticsStrategyExperimentRecommendation(
                result.filterName(),
                "Best conservative diagnostics-only delta PnL among non-rejected filters.",
                "delta_pnl=" + result.deltaPnl() + ", delta_roi=" + result.deltaRoi() + ", observations=" + result.includedBets(),
                result.riskNote(),
                false
            ))
            .orElseGet(DiagnosticsStrategyExperimentRecommendation::none);
    }

    private DiagnosticsCandidateFilterShadowValidation candidateFilterShadowValidation(DiagnosticsDataset dataset) {
        List<CandidateFilterEvaluation> evaluations = dataset.candidateFilterEvaluations();
        if (evaluations.isEmpty()) {
            return DiagnosticsCandidateFilterShadowValidation.empty();
        }
        List<RealBetDiagnosticRow> settled = settledRealBets(dataset.realBets());
        DiagnosticsStrategyPerformanceSegment baseline = performanceSegment("shadow baseline", settled);
        Map<String, List<RealBetDiagnosticRow>> realByRecommendationId = dataset.realBets().stream()
            .filter(row -> !isMissing(row.recommendationId()))
            .collect(Collectors.groupingBy(RealBetDiagnosticRow::recommendationId));
        List<DiagnosticsCandidateFilterShadowResult> filters = evaluations.stream()
            .collect(Collectors.groupingBy(
                CandidateFilterEvaluation::filterName,
                LinkedHashMap::new,
                Collectors.toList()
            ))
            .entrySet()
            .stream()
            .map(entry -> candidateFilterShadowResult(entry.getKey(), entry.getValue(), realByRecommendationId, baseline))
            .toList();
        Instant cutoff = evaluations.stream()
            .map(CandidateFilterEvaluation::createdAt)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);
        return new DiagnosticsCandidateFilterShadowValidation(true, false, cutoff, filters, false);
    }

    private DiagnosticsCandidateFilterShadowResult candidateFilterShadowResult(
        CandidateFilterName filterName,
        List<CandidateFilterEvaluation> evaluations,
        Map<String, List<RealBetDiagnosticRow>> realByRecommendationId,
        DiagnosticsStrategyPerformanceSegment baseline
    ) {
        long wouldPass = evaluations.stream().filter(evaluation -> evaluation.decision() == CandidateFilterDecision.WOULD_PASS).count();
        long wouldFilter = evaluations.stream().filter(evaluation -> evaluation.decision() == CandidateFilterDecision.WOULD_FILTER).count();
        List<RealBetDiagnosticRow> included = realRowsForDecision(evaluations, realByRecommendationId, CandidateFilterDecision.WOULD_PASS);
        List<RealBetDiagnosticRow> excluded = realRowsForDecision(evaluations, realByRecommendationId, CandidateFilterDecision.WOULD_FILTER);
        DiagnosticsStrategyPerformanceSegment includedMetrics = performanceSegment(filterName.name(), included);
        DiagnosticsStrategyPerformanceSegment excludedMetrics = performanceSegment(filterName.name() + " excluded", excluded);
        long paperObserved = evaluations.stream().filter(evaluation -> evaluation.source() == CandidateFilterSource.PAPER).count();
        long realObserved = evaluations.stream()
            .filter(evaluation -> evaluation.source() == CandidateFilterSource.REAL
                || realByRecommendationId.containsKey(evaluation.recommendationId()))
            .map(CandidateFilterEvaluation::recommendationId)
            .distinct()
            .count();
        BigDecimal deltaPnl = subtract(includedMetrics.netPnl(), baseline.netPnl());
        BigDecimal deltaRoi = subtract(includedMetrics.roi(), baseline.roi());
        BigDecimal volumeRetention = baseline.settled() == 0
            ? null
            : decimal(BigDecimal.valueOf(includedMetrics.settled())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(baseline.settled()), SCALE + 2, RoundingMode.HALF_UP));
        DiagnosticsCandidateFilterStatus status = shadowStatus(filterName, includedMetrics, baseline, deltaPnl, deltaRoi, volumeRetention);
        return new DiagnosticsCandidateFilterShadowResult(
            filterName.name(),
            "all-time shadow",
            evaluations.stream().mapToLong(CandidateFilterEvaluation::observedCount).sum(),
            wouldPass,
            wouldFilter,
            rate(BigDecimal.valueOf(wouldPass), BigDecimal.valueOf(Math.max(1, wouldPass + wouldFilter))),
            rate(BigDecimal.valueOf(wouldFilter), BigDecimal.valueOf(Math.max(1, wouldPass + wouldFilter))),
            realObserved,
            paperObserved,
            includedMetrics.settled(),
            excludedMetrics.settled(),
            baseline.netPnl(),
            includedMetrics.netPnl(),
            excludedMetrics.netPnl(),
            baseline.roi(),
            includedMetrics.roi(),
            excludedMetrics.roi(),
            deltaPnl,
            deltaRoi,
            includedMetrics.maxDrawdown(),
            volumeRetention,
            status,
            shadowWarning(filterName, status, includedMetrics),
            false
        );
    }

    private List<RealBetDiagnosticRow> realRowsForDecision(
        List<CandidateFilterEvaluation> evaluations,
        Map<String, List<RealBetDiagnosticRow>> realByRecommendationId,
        CandidateFilterDecision decision
    ) {
        return evaluations.stream()
            .filter(evaluation -> evaluation.source() == CandidateFilterSource.RECOMMENDATION)
            .filter(evaluation -> evaluation.decision() == decision)
            .flatMap(evaluation -> realByRecommendationId.getOrDefault(evaluation.recommendationId(), List.of()).stream())
            .distinct()
            .toList();
    }

    private DiagnosticsCandidateFilterStatus shadowStatus(
        CandidateFilterName filterName,
        DiagnosticsStrategyPerformanceSegment included,
        DiagnosticsStrategyPerformanceSegment baseline,
        BigDecimal deltaPnl,
        BigDecimal deltaRoi,
        BigDecimal volumeRetention
    ) {
        if (filterName == CandidateFilterName.EXCLUDE_NEGATIVE_SEGMENTS_CURRENT) {
            return DiagnosticsCandidateFilterStatus.OVERFIT_RISK;
        }
        if (included.settled() < 25) {
            return DiagnosticsCandidateFilterStatus.INSUFFICIENT_SAMPLE;
        }
        if (deltaPnl == null || deltaRoi == null || deltaPnl.compareTo(BigDecimal.ZERO) <= 0 || deltaRoi.compareTo(BigDecimal.ZERO) <= 0) {
            return DiagnosticsCandidateFilterStatus.REJECTED;
        }
        BigDecimal drawdownDelta = subtract(baseline.maxDrawdown(), included.maxDrawdown());
        if (drawdownDelta != null && drawdownDelta.compareTo(BigDecimal.ZERO) < 0) {
            return DiagnosticsCandidateFilterStatus.WEAK_EVIDENCE;
        }
        if (volumeRetention == null || volumeRetention.compareTo(BigDecimal.valueOf(50)) < 0) {
            return DiagnosticsCandidateFilterStatus.WEAK_EVIDENCE;
        }
        return DiagnosticsCandidateFilterStatus.CANDIDATE;
    }

    private static String shadowWarning(
        CandidateFilterName filterName,
        DiagnosticsCandidateFilterStatus status,
        DiagnosticsStrategyPerformanceSegment included
    ) {
        if (filterName == CandidateFilterName.EXCLUDE_NEGATIVE_SEGMENTS_CURRENT) {
            return "OVERFIT_RISK: derived from current negative historical segments; not recommended as first live candidate.";
        }
        if (status == DiagnosticsCandidateFilterStatus.INSUFFICIENT_SAMPLE) {
            return "INSUFFICIENT_SAMPLE: fewer than 25 settled real bets in shadow included cohort. Observations: "
                + included.settled() + ".";
        }
        return "Shadow-only validation; should_apply_live remains no.";
    }

    private static List<DiagnosticsCandidateFilterResult> top(
        List<DiagnosticsCandidateFilterResult> results,
        Comparator<DiagnosticsCandidateFilterResult> comparator
    ) {
        return results.stream().sorted(comparator).limit(3).toList();
    }

    private Set<String> negativeSegments(List<RealBetDiagnosticRow> rows, Function<RealBetDiagnosticRow, String> classifier) {
        return groupPerformance(rows, classifier).entrySet().stream()
            .filter(entry -> entry.getValue().roi() != null && entry.getValue().roi().compareTo(BigDecimal.ZERO) < 0)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    private List<RealBetDiagnosticRow> sinceFirstRecommendationId(List<RealBetDiagnosticRow> rows) {
        Instant cutoff = rows.stream()
            .filter(row -> !isMissing(row.recommendationId()))
            .map(RealBetDiagnosticRow::createdAt)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);
        return cutoff == null
            ? List.of()
            : rows.stream().filter(row -> row.createdAt() == null || !row.createdAt().isBefore(cutoff)).toList();
    }

    private List<RealBetDiagnosticRow> lastSettled(List<RealBetDiagnosticRow> rows, int limit) {
        List<RealBetDiagnosticRow> settled = settledRealBets(rows).stream()
            .sorted(Comparator.comparing(RealBetDiagnosticRow::settledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RealBetDiagnosticRow::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        int from = Math.max(0, settled.size() - limit);
        return settled.subList(from, settled.size());
    }

    private static List<RealBetDiagnosticRow> settledRealBets(List<RealBetDiagnosticRow> rows) {
        return rows.stream().filter(RealBetDiagnosticRow::settledWithPnl).toList();
    }

    private BigDecimal stake(RealBetDiagnosticRow row) {
        if (row.matchedStake() != null && row.matchedStake().compareTo(BigDecimal.ZERO) > 0) {
            return row.matchedStake();
        }
        if (row.requestedStake() != null) {
            return row.requestedStake();
        }
        return row.selectedStake();
    }

    private BigDecimal effectiveOdds(RealBetDiagnosticRow row) {
        if (row.averageExecutedOdds() != null) {
            return row.averageExecutedOdds();
        }
        if (row.requestedOdds() != null) {
            return row.requestedOdds();
        }
        return row.recordedOdds();
    }

    private static boolean oddsBetween(RealBetDiagnosticRow row, String min, String max) {
        BigDecimal odds = row.averageExecutedOdds() != null
            ? row.averageExecutedOdds()
            : row.requestedOdds() != null ? row.requestedOdds() : row.recordedOdds();
        return odds != null
            && odds.compareTo(new BigDecimal(min)) >= 0
            && odds.compareTo(new BigDecimal(max)) <= 0;
    }

    private static boolean lessThan(BigDecimal value, String limit) {
        return value != null && value.compareTo(new BigDecimal(limit)) < 0;
    }

    private static String oddsBucket(BigDecimal odds) {
        if (odds == null) {
            return "UNKNOWN";
        }
        if (odds.compareTo(new BigDecimal("1.50")) < 0) {
            return "1.00-1.49";
        }
        if (odds.compareTo(new BigDecimal("2.00")) < 0) {
            return "1.50-1.99";
        }
        if (odds.compareTo(new BigDecimal("2.50")) < 0) {
            return "2.00-2.49";
        }
        if (odds.compareTo(new BigDecimal("3.00")) < 0) {
            return "2.50-2.99";
        }
        if (odds.compareTo(new BigDecimal("4.00")) < 0) {
            return "3.00-3.99";
        }
        if (odds.compareTo(new BigDecimal("5.00")) < 0) {
            return "4.00-4.99";
        }
        return "5.00+";
    }

    private DiagnosticsStakeSizingShadowDiagnostics stakeSizingShadowDiagnostics(
        DiagnosticsDataset dataset,
        DiagnosticsLogSummary logs,
        Instant generatedAt
    ) {
        List<StakeSizingShadowDecision> decisions = dataset.stakeSizingShadowDecisions();
        boolean enabled = !decisions.isEmpty();
        DiagnosticsStakeSizingSummary summary = stakeSizingSummary(decisions, logs, generatedAt);
        Map<String, List<RealBetDiagnosticRow>> realByRecommendation = dataset.realBets().stream()
            .filter(row -> !isMissing(row.recommendationId()))
            .collect(Collectors.groupingBy(RealBetDiagnosticRow::recommendationId));
        Map<String, List<PaperTrade>> paperByRecommendation = dataset.paperTrades().stream()
            .filter(row -> !isMissing(row.recommendationId()))
            .collect(Collectors.groupingBy(PaperTrade::recommendationId));
        List<DiagnosticsStakeSizingPolicyResult> results = decisions.stream()
            .collect(Collectors.groupingBy(this::stakeSizingPolicyGroupKey, LinkedHashMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> stakeSizingPolicyResult(entry.getValue(), realByRecommendation, paperByRecommendation))
            .sorted(Comparator.comparing(DiagnosticsStakeSizingPolicyResult::policyName)
                .thenComparing(DiagnosticsStakeSizingPolicyResult::riskProfile)
                .thenComparing(DiagnosticsStakeSizingPolicyResult::source))
            .toList();
        return new DiagnosticsStakeSizingShadowDiagnostics(
            enabled,
            false,
            false,
            summary,
            results,
            "keep shadow running; do not enable live staking; collect more settled joined bets"
        );
    }

    private DiagnosticsStakeSizingScenarioSimulation stakeSizingScenarioSimulation(DiagnosticsDataset dataset) {
        List<StakeSizingShadowDecision> decisions = dataset.stakeSizingShadowDecisions();
        if (decisions.isEmpty()) {
            return DiagnosticsStakeSizingScenarioSimulation.empty();
        }
        Map<String, StakeSizingShadowDecision> representatives = decisions.stream()
            .filter(decision -> !isMissing(decision.recommendationId()))
            .collect(Collectors.toMap(
                StakeSizingShadowDecision::recommendationId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
        if (representatives.isEmpty()) {
            return DiagnosticsStakeSizingScenarioSimulation.empty();
        }
        Map<String, List<RealBetDiagnosticRow>> realByRecommendation = dataset.realBets().stream()
            .filter(row -> !isMissing(row.recommendationId()))
            .collect(Collectors.groupingBy(RealBetDiagnosticRow::recommendationId));
        List<StakeSizingRiskProfile> riskProfiles = decisions.stream()
            .map(StakeSizingShadowDecision::riskProfile)
            .filter(Objects::nonNull)
            .distinct()
            .sorted(Comparator.comparing(StakeSizingRiskProfile::name))
            .toList();
        List<StakeSizingRiskProfile> effectiveRiskProfiles = riskProfiles.isEmpty()
            ? List.of(StakeSizingRiskProfile.CONSERVATIVE, StakeSizingRiskProfile.BALANCED)
            : riskProfiles;
        StakeSizingEngine engine = StakeSizingEngine.defaultEngine();
        List<DiagnosticsStakeSizingScenario> scenarios = stakeSizingScenarios().stream()
            .map(scenario -> stakeSizingScenario(scenario, representatives.values().stream().toList(), effectiveRiskProfiles, realByRecommendation, engine))
            .toList();
        return new DiagnosticsStakeSizingScenarioSimulation(
            true,
            false,
            false,
            scenarios,
            stakeSizingScenarioRanking(scenarios),
            stakeSizingRankingEligibilitySummary(scenarios),
            stakeSizingEligibleScenarioRanking(scenarios),
            stakeSizingScenarioExclusions(scenarios),
            stakeSizingRankingSummary(scenarios),
            stakeSizingWatchScenarioRanking(scenarios),
            stakeSizingLiveEligibleRankings(scenarios),
            stakeSizingScenarioExclusions(scenarios),
            stakeSizingScenarioWatchCandidates(scenarios)
        );
    }

    private DiagnosticsStakeSizingScenario stakeSizingScenario(
        StakeSizingScenarioConfig scenario,
        List<StakeSizingShadowDecision> recommendations,
        List<StakeSizingRiskProfile> riskProfiles,
        Map<String, List<RealBetDiagnosticRow>> realByRecommendation,
        StakeSizingEngine engine
    ) {
        List<DiagnosticsStakeSizingScenarioPolicyResult> results = scenarioPolicies().stream()
            .flatMap(policy -> riskProfiles.stream()
                .map(riskProfile -> stakeSizingScenarioPolicyResult(scenario, policy, riskProfile, recommendations, realByRecommendation, engine)))
            .sorted(Comparator.comparing(DiagnosticsStakeSizingScenarioPolicyResult::policyName)
                .thenComparing(DiagnosticsStakeSizingScenarioPolicyResult::riskProfile))
            .toList();
        return new DiagnosticsStakeSizingScenario(scenario.name(), scenario.baseStake(), scenario.minStake(), scenario.maxStake(), results);
    }

    private DiagnosticsStakeSizingScenarioPolicyResult stakeSizingScenarioPolicyResult(
        StakeSizingScenarioConfig scenario,
        StakeSizingMode policy,
        StakeSizingRiskProfile riskProfile,
        List<StakeSizingShadowDecision> recommendations,
        Map<String, List<RealBetDiagnosticRow>> realByRecommendation,
        StakeSizingEngine engine
    ) {
        List<StakeSizingShadowDecision> simulated = recommendations.stream()
            .map(recommendation -> virtualScenarioDecision(scenario, policy, riskProfile, recommendation, engine))
            .toList();
        List<StakeSizingRealSimulationRow> joined = simulated.stream()
            .flatMap(decision -> realByRecommendation.getOrDefault(decision.recommendationId(), List.of())
                .stream()
                .map(real -> new StakeSizingRealSimulationRow(decision, real)))
            .toList();
        List<StakeSizingRealSimulationRow> settled = joined.stream()
            .filter(row -> row.real().settledWithPnl())
            .toList();
        List<StakeSizingRealSimulationRow> validSettled = settled.stream()
            .filter(row -> validStake(stakeForSimulation(row.real())) != null)
            .toList();
        long invalidStake = settled.size() - validSettled.size();
        BigDecimal baselineTurnover = validSettled.stream()
            .map(row -> stakeForSimulation(row.real()))
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal baselinePnl = validSettled.stream()
            .map(row -> valueOrZero(row.real().realizedProfitLoss()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal simulatedTurnover = validSettled.stream()
            .map(row -> row.decision().wouldBlock() ? BigDecimal.ZERO : valueOrZero(row.decision().finalStake()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal simulatedPnl = validSettled.stream()
            .map(row -> scenarioSimulatedPnl(row))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> baselinePnlCurve = validSettled.stream()
            .sorted(Comparator.comparing(row -> row.real().settledAt(), Comparator.nullsLast(Comparator.naturalOrder())))
            .map(row -> valueOrZero(row.real().realizedProfitLoss()))
            .toList();
        List<BigDecimal> simulatedPnlCurve = validSettled.stream()
            .sorted(Comparator.comparing(row -> row.real().settledAt(), Comparator.nullsLast(Comparator.naturalOrder())))
            .map(this::scenarioSimulatedPnl)
            .toList();
        long floorApplied = simulated.stream()
            .filter(decision -> decision.calculatedStake() != null && decision.finalStake() != null)
            .filter(decision -> decision.calculatedStake().compareTo(decision.minStake()) < 0)
            .filter(decision -> decision.finalStake().compareTo(decision.minStake()) == 0)
            .count();
        BigDecimal totalFloorUplift = simulated.stream()
            .filter(decision -> decision.calculatedStake() != null && decision.finalStake() != null)
            .filter(decision -> decision.calculatedStake().compareTo(decision.minStake()) < 0)
            .filter(decision -> decision.finalStake().compareTo(decision.minStake()) == 0)
            .map(decision -> decision.finalStake().subtract(decision.calculatedStake()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal baselineRoi = rate(baselinePnl, baselineTurnover);
        BigDecimal simulatedRoi = rate(simulatedPnl, simulatedTurnover);
        BigDecimal baselineDrawdown = drawdownFromPnl(baselinePnlCurve, false);
        BigDecimal simulatedDrawdown = drawdownFromPnl(simulatedPnlCurve, false);
        long fallbackRequestedStake = validSettled.stream()
            .filter(row -> validStake(row.real().matchedStake()) == null && validStake(row.real().requestedStake()) != null)
            .count();
        DiagnosticsStakeSizingPolicyStatus status = policy == StakeSizingMode.FRACTIONAL_KELLY_SHADOW
            ? DiagnosticsStakeSizingPolicyStatus.SHADOW_ONLY
            : validSettled.size() < 50 ? DiagnosticsStakeSizingPolicyStatus.INSUFFICIENT_SAMPLE : DiagnosticsStakeSizingPolicyStatus.CANDIDATE;
        long wouldBlockCount = simulated.stream().filter(StakeSizingShadowDecision::wouldBlock).count();
        BigDecimal wouldBlockRate = rate(BigDecimal.valueOf(wouldBlockCount), BigDecimal.valueOf(Math.max(1, simulated.size())));
        DiagnosticsStakeSizingRankingEligibility rankingEligibility = stakeSizingRankingEligibility(
            policy,
            status,
            simulated.size(),
            validSettled.size(),
            simulatedTurnover,
            wouldBlockCount,
            wouldBlockRate,
            invalidStake
        );
        boolean hasExposure = simulatedTurnover.compareTo(BigDecimal.ZERO) > 0 && !validSettled.isEmpty();
        boolean allBlocked = simulated.size() > 0 && wouldBlockCount >= simulated.size()
            || BigDecimal.ONE.compareTo(valueOrZero(wouldBlockRate)) <= 0;
        boolean shadowOnly = policy == StakeSizingMode.FRACTIONAL_KELLY_SHADOW
            || status == DiagnosticsStakeSizingPolicyStatus.SHADOW_ONLY;
        boolean validData = invalidStake == 0 || !validSettled.isEmpty();
        boolean sufficientSample = validSettled.size() >= 50;
        boolean highRisk = false;
        boolean usefulRanking = hasExposure && validData && !allBlocked && !shadowOnly;
        boolean watchCandidate = usefulRanking && !sufficientSample;
        boolean liveEligible = usefulRanking && sufficientSample && !highRisk;
        return new DiagnosticsStakeSizingScenarioPolicyResult(
            scenario.name(),
            policy.name(),
            riskProfile.name(),
            scenario.baseStake(),
            scenario.minStake(),
            scenario.maxStake(),
            simulated.size(),
            joined.size(),
            validSettled.size(),
            joined.stream().filter(row -> !row.real().settledWithPnl()).count(),
            validSettled.stream().filter(row -> row.real().settlementResult() == BetSettlementResult.WIN).count(),
            validSettled.stream().filter(row -> row.real().settlementResult() == BetSettlementResult.LOSE).count(),
            money(baselineTurnover),
            money(baselinePnl),
            baselineRoi,
            money(simulatedTurnover),
            money(simulatedPnl),
            simulatedRoi,
            money(simulatedPnl.subtract(baselinePnl)),
            subtract(simulatedRoi, baselineRoi),
            average(simulated.stream().map(StakeSizingShadowDecision::calculatedStake).filter(Objects::nonNull).toList()),
            average(simulated.stream().map(StakeSizingShadowDecision::finalStake).filter(Objects::nonNull).toList()),
            min(simulated.stream().map(StakeSizingShadowDecision::finalStake).filter(Objects::nonNull).toList()),
            max(simulated.stream().map(StakeSizingShadowDecision::finalStake).filter(Objects::nonNull).toList()),
            average(validSettled.stream().map(row -> rate(row.decision().finalStake(), stakeForSimulation(row.real()))).filter(Objects::nonNull).toList()),
            max(joined.stream().map(row -> row.decision().finalStake()).filter(Objects::nonNull).toList()),
            simulatedDrawdown,
            drawdownFromPnl(simulatedPnlCurve, true),
            baselineDrawdown,
            subtract(baselineDrawdown, simulatedDrawdown),
            wouldBlockCount,
            wouldBlockRate,
            floorApplied,
            rate(BigDecimal.valueOf(floorApplied), BigDecimal.valueOf(Math.max(1, simulated.size()))),
            floorApplied == 0 ? BigDecimal.ZERO : decimal(totalFloorUplift.divide(BigDecimal.valueOf(floorApplied), SCALE + 2, RoundingMode.HALF_UP)),
            decimal(totalFloorUplift),
            fallbackRequestedStake,
            invalidStake,
            status,
            stakeSizingScenarioWarning(policy, status, fallbackRequestedStake, invalidStake),
            rankingEligibility,
            hasExposure,
            allBlocked,
            shadowOnly,
            validData,
            sufficientSample,
            highRisk,
            usefulRanking,
            watchCandidate,
            liveEligible,
            false
        );
    }

    private StakeSizingShadowDecision virtualScenarioDecision(
        StakeSizingScenarioConfig scenario,
        StakeSizingMode policy,
        StakeSizingRiskProfile riskProfile,
        StakeSizingShadowDecision seed,
        StakeSizingEngine engine
    ) {
        StakeSizingDecision decision = engine.evaluate(policy, new StakeSizingContext(
            seed.recommendationId(),
            seed.canonicalKey(),
            seed.strategyName(),
            seed.selectionSide(),
            seed.odds() == null ? BigDecimal.valueOf(2) : seed.odds(),
            scenario.baseStake(),
            scenario.minStake(),
            scenario.maxStake(),
            seed.bankroll() == null ? BigDecimal.valueOf(500) : seed.bankroll(),
            riskProfile,
            StakeSizingSource.SHADOW,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            scenario.maxStake().multiply(BigDecimal.valueOf(100)),
            scenario.maxStake().multiply(BigDecimal.valueOf(100)),
            scenario.maxStake().multiply(BigDecimal.valueOf(100)),
            seed.createdAt()
        ));
        return new StakeSizingShadowDecision(
            seed.recommendationId() + "-" + scenario.name() + "-" + policy.name() + "-" + riskProfile.name(),
            seed.recommendationId(),
            seed.canonicalKey(),
            policy,
            riskProfile,
            StakeSizingSource.SHADOW,
            decision.selectionSide(),
            decision.odds(),
            seed.strategyName(),
            decision.baseStake(),
            decision.minStake(),
            decision.maxStake(),
            seed.bankroll(),
            decision.calculatedStake(),
            decision.finalStake(),
            decision.wouldBlock(),
            decision.blockReason(),
            decision.decisionReason(),
            adjustmentSummary(decision.adjustments()),
            seed.evaluatedAt(),
            seed.createdAt(),
            seed.lastEvaluatedAt(),
            seed.observedCount()
        );
    }

    private BigDecimal scenarioSimulatedPnl(StakeSizingRealSimulationRow row) {
        BigDecimal profitMultiplier = rate(row.real().realizedProfitLoss(), stakeForSimulation(row.real()));
        if (profitMultiplier == null || row.decision().wouldBlock()) {
            return BigDecimal.ZERO;
        }
        return profitMultiplier.multiply(valueOrZero(row.decision().finalStake()));
    }

    private String stakeSizingScenarioWarning(
        StakeSizingMode policy,
        DiagnosticsStakeSizingPolicyStatus status,
        long fallbackRequestedStake,
        long invalidStake
    ) {
        if (policy == StakeSizingMode.FRACTIONAL_KELLY_SHADOW) {
            return "SHADOW_ONLY: PROBABILITY_NOT_AVAILABLE; should_apply_live=false.";
        }
        if (invalidStake > 0) {
            return "Some settled bets were excluded because no valid matched/requested stake was available.";
        }
        if (fallbackRequestedStake > 0) {
            return "Used requested_stake fallback when matched_stake was unavailable.";
        }
        if (status == DiagnosticsStakeSizingPolicyStatus.INSUFFICIENT_SAMPLE) {
            return "not enough sample for live staking decision";
        }
        return null;
    }

    private DiagnosticsStakeSizingScenarioRanking stakeSizingScenarioRanking(List<DiagnosticsStakeSizingScenario> scenarios) {
        List<DiagnosticsStakeSizingScenarioPolicyResult> results = scenarios.stream()
            .flatMap(scenario -> scenario.policyResults().stream())
            .toList();
        long maxSettled = results.stream().mapToLong(DiagnosticsStakeSizingScenarioPolicyResult::realSettledJoined).max().orElse(0);
        return new DiagnosticsStakeSizingScenarioRanking(
            scenarioLabel(maxBy(results, DiagnosticsStakeSizingScenarioPolicyResult::simulatedRoi)),
            scenarioLabel(maxBy(results, DiagnosticsStakeSizingScenarioPolicyResult::simulatedPnl)),
            scenarioLabel(minBy(results, DiagnosticsStakeSizingScenarioPolicyResult::simulatedMaxDrawdown)),
            scenarioLabel(maxBy(results.stream().filter(result -> result.policyName().equals("RISK_ADJUSTED")).toList(), DiagnosticsStakeSizingScenarioPolicyResult::simulatedPnl)),
            scenarioLabel(maxBy(results, DiagnosticsStakeSizingScenarioPolicyResult::maxSimulatedExposure)),
            scenarioLabel(maxBy(results, result -> BigDecimal.valueOf(result.minStakeFloorAppliedCount()))),
            maxSettled < 50 ? "not enough sample for live staking decision" : null,
            false
        );
    }

    private DiagnosticsStakeSizingScenarioRanking stakeSizingEligibleScenarioRanking(List<DiagnosticsStakeSizingScenario> scenarios) {
        return stakeSizingWatchScenarioRanking(scenarios);
    }

    private DiagnosticsStakeSizingScenarioRanking stakeSizingWatchScenarioRanking(List<DiagnosticsStakeSizingScenario> scenarios) {
        List<DiagnosticsStakeSizingScenarioPolicyResult> results = scenarios.stream()
            .flatMap(scenario -> scenario.policyResults().stream())
            .filter(this::eligibleForScenarioRanking)
            .toList();
        long maxSettled = results.stream().mapToLong(DiagnosticsStakeSizingScenarioPolicyResult::realSettledJoined).max().orElse(0);
        return new DiagnosticsStakeSizingScenarioRanking(
            scenarioLabel(maxBy(results, DiagnosticsStakeSizingScenarioPolicyResult::simulatedRoi)),
            scenarioLabel(maxBy(results, DiagnosticsStakeSizingScenarioPolicyResult::simulatedPnl)),
            scenarioLabel(minBy(results, DiagnosticsStakeSizingScenarioPolicyResult::simulatedMaxDrawdown)),
            scenarioLabel(maxBy(results.stream().filter(result -> result.policyName().equals("RISK_ADJUSTED")).toList(), DiagnosticsStakeSizingScenarioPolicyResult::simulatedPnl)),
            scenarioLabel(maxBy(results, DiagnosticsStakeSizingScenarioPolicyResult::maxSimulatedExposure)),
            scenarioLabel(maxBy(results, result -> BigDecimal.valueOf(result.minStakeFloorAppliedCount()))),
            maxSettled < 50 ? "not enough sample for live staking decision" : null,
            false
        );
    }

    private DiagnosticsStakeSizingLiveEligibleRankings stakeSizingLiveEligibleRankings(List<DiagnosticsStakeSizingScenario> scenarios) {
        List<DiagnosticsStakeSizingScenarioPolicyResult> results = scenarios.stream()
            .flatMap(scenario -> scenario.policyResults().stream())
            .filter(DiagnosticsStakeSizingScenarioPolicyResult::eligibleForLive)
            .toList();
        if (results.isEmpty()) {
            String reason = scenarios.stream()
                .flatMap(scenario -> scenario.policyResults().stream())
                .anyMatch(DiagnosticsStakeSizingScenarioPolicyResult::watchCandidate)
                    ? "NO_LIVE_ELIGIBLE_RESULTS_INSUFFICIENT_SAMPLE"
                    : "NO_LIVE_ELIGIBLE_RESULTS";
            return new DiagnosticsStakeSizingLiveEligibleRankings(false, reason, DiagnosticsStakeSizingScenarioRanking.empty());
        }
        long maxSettled = results.stream().mapToLong(DiagnosticsStakeSizingScenarioPolicyResult::realSettledJoined).max().orElse(0);
        return new DiagnosticsStakeSizingLiveEligibleRankings(true, null, new DiagnosticsStakeSizingScenarioRanking(
            scenarioLabel(maxBy(results, DiagnosticsStakeSizingScenarioPolicyResult::simulatedRoi)),
            scenarioLabel(maxBy(results, DiagnosticsStakeSizingScenarioPolicyResult::simulatedPnl)),
            scenarioLabel(minBy(results, DiagnosticsStakeSizingScenarioPolicyResult::simulatedMaxDrawdown)),
            scenarioLabel(maxBy(results.stream().filter(result -> result.policyName().equals("RISK_ADJUSTED")).toList(), DiagnosticsStakeSizingScenarioPolicyResult::simulatedPnl)),
            scenarioLabel(maxBy(results, DiagnosticsStakeSizingScenarioPolicyResult::maxSimulatedExposure)),
            scenarioLabel(maxBy(results, result -> BigDecimal.valueOf(result.minStakeFloorAppliedCount()))),
            maxSettled < 50 ? "not enough sample for live staking decision" : null,
            false
        ));
    }

    private DiagnosticsStakeSizingRankingEligibilitySummary stakeSizingRankingEligibilitySummary(List<DiagnosticsStakeSizingScenario> scenarios) {
        List<DiagnosticsStakeSizingScenarioPolicyResult> results = scenarios.stream()
            .flatMap(scenario -> scenario.policyResults().stream())
            .toList();
        return new DiagnosticsStakeSizingRankingEligibilitySummary(
            results.stream().filter(result -> result.rankingEligibility() == DiagnosticsStakeSizingRankingEligibility.ELIGIBLE).count(),
            results.stream().filter(result -> result.rankingEligibility() == DiagnosticsStakeSizingRankingEligibility.INSUFFICIENT_SAMPLE).count(),
            results.stream().filter(this::hasNoExposure).count(),
            results.stream().filter(this::isAllBlocked).count(),
            results.stream().filter(this::isShadowOnlyRankingResult).count(),
            results.stream().filter(result -> result.rankingEligibility() == DiagnosticsStakeSizingRankingEligibility.INVALID_DATA).count(),
            results.stream().filter(result -> result.rankingEligibility() == DiagnosticsStakeSizingRankingEligibility.HIGH_RISK).count()
        );
    }

    private DiagnosticsStakeSizingRankingSummary stakeSizingRankingSummary(List<DiagnosticsStakeSizingScenario> scenarios) {
        List<DiagnosticsStakeSizingScenarioPolicyResult> results = scenarios.stream()
            .flatMap(scenario -> scenario.policyResults().stream())
            .toList();
        long excluded = results.stream().filter(result -> !result.eligibleForUsefulRanking()).count();
        return new DiagnosticsStakeSizingRankingSummary(
            results.stream().filter(DiagnosticsStakeSizingScenarioPolicyResult::eligibleForUsefulRanking).count(),
            results.stream().filter(DiagnosticsStakeSizingScenarioPolicyResult::watchCandidate).count(),
            results.stream().filter(DiagnosticsStakeSizingScenarioPolicyResult::eligibleForLive).count(),
            excluded,
            results.stream().filter(result -> !result.hasExposure()).count(),
            results.stream().filter(DiagnosticsStakeSizingScenarioPolicyResult::isAllBlocked).count(),
            results.stream().filter(DiagnosticsStakeSizingScenarioPolicyResult::isShadowOnly).count(),
            results.stream().filter(result -> !result.hasValidData()).count(),
            results.stream().filter(DiagnosticsStakeSizingScenarioPolicyResult::isHighRisk).count(),
            results.stream().filter(result -> !result.hasSufficientSample()).count()
        );
    }

    private List<DiagnosticsStakeSizingScenarioExclusion> stakeSizingScenarioExclusions(List<DiagnosticsStakeSizingScenario> scenarios) {
        return scenarios.stream()
            .flatMap(scenario -> scenario.policyResults().stream())
            .filter(result -> !eligibleForScenarioRanking(result))
            .map(result -> new DiagnosticsStakeSizingScenarioExclusion(
                result.scenarioName(),
                result.policyName(),
                result.riskProfile(),
                result.rankingEligibility(),
                stakeSizingScenarioExclusionReason(result)
            ))
            .sorted(Comparator.comparing(DiagnosticsStakeSizingScenarioExclusion::policyName)
                .thenComparing(DiagnosticsStakeSizingScenarioExclusion::scenarioName)
                .thenComparing(DiagnosticsStakeSizingScenarioExclusion::riskProfile))
            .toList();
    }

    private List<DiagnosticsStakeSizingScenarioPolicyResult> stakeSizingScenarioWatchCandidates(List<DiagnosticsStakeSizingScenario> scenarios) {
        return scenarios.stream()
            .flatMap(scenario -> scenario.policyResults().stream())
            .filter(DiagnosticsStakeSizingScenarioPolicyResult::watchCandidate)
            .sorted(Comparator.comparing((DiagnosticsStakeSizingScenarioPolicyResult result) -> result.policyName().equals("RISK_ADJUSTED") ? 0 : 1)
                .thenComparing(result -> result.riskProfile().equals("CONSERVATIVE") ? 0 : 1)
                .thenComparing(DiagnosticsStakeSizingScenarioPolicyResult::simulatedMaxDrawdown)
                .thenComparing(Comparator.comparing(DiagnosticsStakeSizingScenarioPolicyResult::simulatedRoi, Comparator.nullsLast(Comparator.naturalOrder())).reversed()))
            .toList();
    }

    private DiagnosticsStakeSizingRankingEligibility stakeSizingRankingEligibility(
        StakeSizingMode policy,
        DiagnosticsStakeSizingPolicyStatus status,
        long recommendationsEvaluated,
        long realSettledJoined,
        BigDecimal simulatedTurnover,
        long wouldBlockCount,
        BigDecimal wouldBlockRate,
        long invalidStake
    ) {
        if (invalidStake > 0 && realSettledJoined == 0) {
            return DiagnosticsStakeSizingRankingEligibility.INVALID_DATA;
        }
        if (valueOrZero(simulatedTurnover).compareTo(BigDecimal.ZERO) == 0 || realSettledJoined == 0) {
            return DiagnosticsStakeSizingRankingEligibility.NO_EXPOSURE;
        }
        if (wouldBlockCount >= recommendationsEvaluated || BigDecimal.ONE.compareTo(valueOrZero(wouldBlockRate)) <= 0) {
            return DiagnosticsStakeSizingRankingEligibility.ALL_BLOCKED;
        }
        if (policy == StakeSizingMode.FRACTIONAL_KELLY_SHADOW || status == DiagnosticsStakeSizingPolicyStatus.SHADOW_ONLY) {
            return DiagnosticsStakeSizingRankingEligibility.SHADOW_ONLY;
        }
        if (realSettledJoined < 50) {
            return DiagnosticsStakeSizingRankingEligibility.INSUFFICIENT_SAMPLE;
        }
        return DiagnosticsStakeSizingRankingEligibility.ELIGIBLE;
    }

    private boolean eligibleForScenarioRanking(DiagnosticsStakeSizingScenarioPolicyResult result) {
        return result.eligibleForUsefulRanking();
    }

    private boolean hasNoExposure(DiagnosticsStakeSizingScenarioPolicyResult result) {
        return valueOrZero(result.simulatedTurnover()).compareTo(BigDecimal.ZERO) == 0 || result.realSettledJoined() == 0;
    }

    private boolean isAllBlocked(DiagnosticsStakeSizingScenarioPolicyResult result) {
        return result.recommendationsEvaluated() > 0 && result.wouldBlockCount() >= result.recommendationsEvaluated()
            || BigDecimal.ONE.compareTo(valueOrZero(result.wouldBlockRate())) <= 0;
    }

    private boolean isShadowOnlyRankingResult(DiagnosticsStakeSizingScenarioPolicyResult result) {
        return result.policyName().equals(StakeSizingMode.FRACTIONAL_KELLY_SHADOW.name())
            || result.status() == DiagnosticsStakeSizingPolicyStatus.SHADOW_ONLY
            || containsIgnoreCase(result.warning(), "PROBABILITY_NOT_AVAILABLE");
    }

    private String stakeSizingScenarioExclusionReason(DiagnosticsStakeSizingScenarioPolicyResult result) {
        List<String> reasons = new ArrayList<>();
        if (isShadowOnlyRankingResult(result)) {
            reasons.add("SHADOW_ONLY");
        }
        if (containsIgnoreCase(result.warning(), "PROBABILITY_NOT_AVAILABLE")) {
            reasons.add("PROBABILITY_NOT_AVAILABLE");
        }
        if (hasNoExposure(result)) {
            reasons.add("NO_EXPOSURE");
        }
        if (isAllBlocked(result)) {
            reasons.add("ALL_BLOCKED");
        }
        if (result.rankingEligibility() == DiagnosticsStakeSizingRankingEligibility.INVALID_DATA) {
            reasons.add("INVALID_DATA");
        }
        return reasons.isEmpty() ? result.rankingEligibility().name() : String.join("_", reasons);
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && needle != null && value.toLowerCase().contains(needle.toLowerCase());
    }

    private DiagnosticsStakeSizingScenarioPolicyResult maxBy(
        List<DiagnosticsStakeSizingScenarioPolicyResult> results,
        Function<DiagnosticsStakeSizingScenarioPolicyResult, BigDecimal> value
    ) {
        return results.stream()
            .filter(result -> value.apply(result) != null)
            .max(Comparator.comparing(value))
            .orElse(null);
    }

    private DiagnosticsStakeSizingScenarioPolicyResult minBy(
        List<DiagnosticsStakeSizingScenarioPolicyResult> results,
        Function<DiagnosticsStakeSizingScenarioPolicyResult, BigDecimal> value
    ) {
        return results.stream()
            .filter(result -> value.apply(result) != null)
            .min(Comparator.comparing(value))
            .orElse(null);
    }

    private String scenarioLabel(DiagnosticsStakeSizingScenarioPolicyResult result) {
        return result == null ? null : result.scenarioName() + "/" + result.policyName() + "/" + result.riskProfile();
    }

    private List<StakeSizingMode> scenarioPolicies() {
        return List.of(
            StakeSizingMode.FLAT,
            StakeSizingMode.RISK_ADJUSTED,
            StakeSizingMode.TIERED_CONFIDENCE,
            StakeSizingMode.FRACTIONAL_KELLY_SHADOW
        );
    }

    private List<StakeSizingScenarioConfig> stakeSizingScenarios() {
        return List.of(
            new StakeSizingScenarioConfig("SCENARIO_CURRENT_1_MIN_1", new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("10.00")),
            new StakeSizingScenarioConfig("SCENARIO_BASE_5_MIN_1", new BigDecimal("5.00"), new BigDecimal("1.00"), new BigDecimal("50.00")),
            new StakeSizingScenarioConfig("SCENARIO_BASE_10_MIN_1", new BigDecimal("10.00"), new BigDecimal("1.00"), new BigDecimal("100.00")),
            new StakeSizingScenarioConfig("SCENARIO_BASE_10_MIN_0_10", new BigDecimal("10.00"), new BigDecimal("0.10"), new BigDecimal("100.00")),
            new StakeSizingScenarioConfig("SCENARIO_BASE_50_MIN_1", new BigDecimal("50.00"), new BigDecimal("1.00"), new BigDecimal("500.00")),
            new StakeSizingScenarioConfig("SCENARIO_BASE_100_MIN_1", new BigDecimal("100.00"), new BigDecimal("1.00"), new BigDecimal("1000.00"))
        );
    }

    private String adjustmentSummary(List<StakeSizingAdjustment> adjustments) {
        if (adjustments == null || adjustments.isEmpty()) {
            return "[]";
        }
        return adjustments.stream()
            .map(StakeSizingAdjustment::name)
            .collect(Collectors.joining("\",\"", "[\"", "\"]"));
    }

    private DiagnosticsStakeSizingSummary stakeSizingSummary(
        List<StakeSizingShadowDecision> decisions,
        DiagnosticsLogSummary logs,
        Instant generatedAt
    ) {
        Instant firstCreated = decisions.stream()
            .map(StakeSizingShadowDecision::createdAt)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);
        Instant lastEvaluated = decisions.stream()
            .map(StakeSizingShadowDecision::lastEvaluatedAt)
            .filter(Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(null);
        Duration freshness = lastEvaluated == null || generatedAt == null ? null : Duration.between(lastEvaluated, generatedAt);
        long duplicateKeys = decisions.stream()
            .collect(Collectors.groupingBy(this::stakeSizingPolicyKey, Collectors.counting()))
            .values()
            .stream()
            .filter(count -> count > 1)
            .mapToLong(count -> count - 1)
            .sum();
        long forbidden = logCount(logs, "stake_sizing.live_applied")
            + logCount(logs, "stake_sizing.changed_real_stake")
            + logCount(logs, "stake_sizing.order_stake_changed");
        return new DiagnosticsStakeSizingSummary(
            decisions.size(),
            decisions.stream().map(StakeSizingShadowDecision::recommendationId).filter(Objects::nonNull).distinct().count(),
            decisions.stream().map(decision -> decision.policyName().name()).collect(Collectors.toCollection(java.util.TreeSet::new)),
            decisions.stream().map(decision -> decision.riskProfile().name()).collect(Collectors.toCollection(java.util.TreeSet::new)),
            decisions.stream().map(decision -> decision.source().name()).collect(Collectors.toCollection(java.util.TreeSet::new)),
            decisions.stream().mapToLong(StakeSizingShadowDecision::observedCount).sum(),
            firstCreated,
            lastEvaluated,
            freshness,
            duplicateKeys,
            logCount(logs, "stake_sizing.shadow_failed"),
            forbidden
        );
    }

    private DiagnosticsStakeSizingPolicyResult stakeSizingPolicyResult(
        List<StakeSizingShadowDecision> decisions,
        Map<String, List<RealBetDiagnosticRow>> realByRecommendation,
        Map<String, List<PaperTrade>> paperByRecommendation
    ) {
        StakeSizingShadowDecision first = decisions.getFirst();
        DiagnosticsStakeSizingRealJoined real = stakeSizingRealJoined(decisions, realByRecommendation);
        DiagnosticsStakeSizingPaperJoined paper = stakeSizingPaperJoined(decisions, paperByRecommendation);
        DiagnosticsStakeSizingPolicyStatus status = stakeSizingStatus(first, real);
        String warning = stakeSizingWarning(first, status, real, paper);
        return new DiagnosticsStakeSizingPolicyResult(
            first.policyName().name(),
            first.riskProfile().name(),
            first.source().name(),
            decisions.size(),
            decisions.stream().map(StakeSizingShadowDecision::recommendationId).distinct().count(),
            decisions.stream().mapToLong(StakeSizingShadowDecision::observedCount).sum(),
            average(decisions.stream().map(StakeSizingShadowDecision::baseStake).filter(Objects::nonNull).toList()),
            average(decisions.stream().map(StakeSizingShadowDecision::calculatedStake).filter(Objects::nonNull).toList()),
            average(decisions.stream().map(StakeSizingShadowDecision::finalStake).filter(Objects::nonNull).toList()),
            min(decisions.stream().map(StakeSizingShadowDecision::calculatedStake).filter(Objects::nonNull).toList()),
            max(decisions.stream().map(StakeSizingShadowDecision::calculatedStake).filter(Objects::nonNull).toList()),
            min(decisions.stream().map(StakeSizingShadowDecision::finalStake).filter(Objects::nonNull).toList()),
            max(decisions.stream().map(StakeSizingShadowDecision::finalStake).filter(Objects::nonNull).toList()),
            decisions.stream().filter(StakeSizingShadowDecision::wouldBlock).count(),
            rate(BigDecimal.valueOf(decisions.stream().filter(StakeSizingShadowDecision::wouldBlock).count()), BigDecimal.valueOf(decisions.size())),
            breakdown(decisions, decision -> decision.decisionReason().name()),
            breakdown(decisions.stream().filter(decision -> decision.blockReason() != null).toList(), decision -> decision.blockReason().name()),
            adjustmentBreakdown(decisions),
            minStakeFloor(decisions),
            real,
            paper,
            probabilityAvailableCount(decisions),
            probabilityMissingCount(decisions),
            confidenceAvailableCount(decisions),
            confidenceMissingCount(decisions),
            strongestReductions(decisions, realByRecommendation),
            status,
            warning,
            false
        );
    }

    private DiagnosticsStakeSizingMinStakeFloor minStakeFloor(List<StakeSizingShadowDecision> decisions) {
        List<StakeSizingShadowDecision> floored = decisions.stream()
            .filter(decision -> decision.calculatedStake() != null && decision.finalStake() != null && decision.minStake() != null)
            .filter(decision -> decision.calculatedStake().compareTo(decision.minStake()) < 0)
            .filter(decision -> decision.finalStake().compareTo(decision.minStake()) == 0)
            .toList();
        BigDecimal totalUplift = floored.stream()
            .map(decision -> decision.finalStake().subtract(decision.calculatedStake()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DiagnosticsStakeSizingMinStakeFloor(
            floored.size(),
            rate(BigDecimal.valueOf(floored.size()), BigDecimal.valueOf(Math.max(1, decisions.size()))),
            average(floored.stream().map(StakeSizingShadowDecision::calculatedStake).toList()),
            average(floored.stream().map(StakeSizingShadowDecision::finalStake).toList()),
            floored.isEmpty() ? null : decimal(totalUplift.divide(BigDecimal.valueOf(floored.size()), SCALE + 2, RoundingMode.HALF_UP)),
            decimal(totalUplift)
        );
    }

    private DiagnosticsStakeSizingRealJoined stakeSizingRealJoined(
        List<StakeSizingShadowDecision> decisions,
        Map<String, List<RealBetDiagnosticRow>> realByRecommendation
    ) {
        List<StakeSizingRealSimulationRow> joined = decisions.stream()
            .flatMap(decision -> realByRecommendation.getOrDefault(decision.recommendationId(), List.of())
                .stream()
                .map(real -> new StakeSizingRealSimulationRow(decision, real)))
            .toList();
        List<StakeSizingRealSimulationRow> settled = joined.stream()
            .filter(row -> row.real().settledWithPnl())
            .filter(row -> validStake(stakeForSimulation(row.real())) != null)
            .toList();
        BigDecimal baselineTurnover = settled.stream()
            .map(row -> stakeForSimulation(row.real()))
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal baselinePnl = settled.stream()
            .map(row -> row.real().realizedProfitLoss())
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> multipliers = settled.stream()
            .map(row -> rate(row.decision().finalStake(), stakeForSimulation(row.real())))
            .filter(Objects::nonNull)
            .toList();
        BigDecimal simulatedTurnover = settled.stream()
            .map(row -> row.decision().wouldBlock() ? BigDecimal.ZERO : valueOrZero(row.decision().finalStake()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal simulatedPnl = settled.stream()
            .map(row -> {
                BigDecimal profitMultiplier = rate(row.real().realizedProfitLoss(), stakeForSimulation(row.real()));
                if (profitMultiplier == null || row.decision().wouldBlock()) {
                    return BigDecimal.ZERO;
                }
                return profitMultiplier.multiply(valueOrZero(row.decision().finalStake()));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> baselinePnlCurve = settled.stream()
            .sorted(Comparator.comparing(row -> row.real().settledAt(), Comparator.nullsLast(Comparator.naturalOrder())))
            .map(row -> valueOrZero(row.real().realizedProfitLoss()))
            .toList();
        List<BigDecimal> simulatedPnlCurve = settled.stream()
            .sorted(Comparator.comparing(row -> row.real().settledAt(), Comparator.nullsLast(Comparator.naturalOrder())))
            .map(row -> {
                BigDecimal profitMultiplier = rate(row.real().realizedProfitLoss(), stakeForSimulation(row.real()));
                if (profitMultiplier == null || row.decision().wouldBlock()) {
                    return BigDecimal.ZERO;
                }
                return profitMultiplier.multiply(valueOrZero(row.decision().finalStake()));
            })
            .toList();
        BigDecimal baselineDrawdown = drawdownFromPnl(baselinePnlCurve, false);
        BigDecimal simulatedMaxDrawdown = drawdownFromPnl(simulatedPnlCurve, false);
        BigDecimal simulatedCurrentDrawdown = drawdownFromPnl(simulatedPnlCurve, true);
        long fallbackRequestedStake = settled.stream()
            .filter(row -> validStake(row.real().matchedStake()) == null && validStake(row.real().requestedStake()) != null)
            .count();
        DiagnosticsStakeSizingPolicyStatus status = settled.size() < 25
            ? DiagnosticsStakeSizingPolicyStatus.INSUFFICIENT_SAMPLE
            : simulatedPnl.compareTo(baselinePnl) < 0
                ? DiagnosticsStakeSizingPolicyStatus.REJECTED
                : DiagnosticsStakeSizingPolicyStatus.CANDIDATE;
        return new DiagnosticsStakeSizingRealJoined(
            joined.size(),
            settled.size(),
            Math.max(0, joined.size() - settled.size()),
            settled.stream().filter(row -> row.real().settlementResult() == BetSettlementResult.WIN).count(),
            settled.stream().filter(row -> row.real().settlementResult() == BetSettlementResult.LOSE).count(),
            settled.stream().filter(row -> row.real().settlementResult() == BetSettlementResult.VOID
                || row.real().stage() == BetIntentStage.CANCELLED).count(),
            money(baselineTurnover),
            money(baselinePnl),
            rate(baselinePnl, baselineTurnover),
            money(simulatedTurnover),
            money(simulatedPnl),
            rate(simulatedPnl, simulatedTurnover),
            money(simulatedPnl.subtract(baselinePnl)),
            subtract(rate(simulatedPnl, simulatedTurnover), rate(baselinePnl, baselineTurnover)),
            average(multipliers),
            max(settled.stream().map(row -> row.decision().finalStake()).filter(Objects::nonNull).toList()),
            joined.stream().filter(row -> row.decision().wouldBlock()).count(),
            rate(BigDecimal.valueOf(joined.stream().filter(row -> row.decision().wouldBlock()).count()), BigDecimal.valueOf(Math.max(1, joined.size()))),
            simulatedCurrentDrawdown,
            simulatedMaxDrawdown,
            baselineDrawdown,
            subtract(baselineDrawdown, simulatedMaxDrawdown),
            status,
            settled.size() < 25 ? "INSUFFICIENT_SAMPLE: settled joined bets < 25." : null,
            fallbackRequestedStake > 0 ? "Used requested_stake fallback when matched_stake was unavailable." : null
        );
    }

    private DiagnosticsStakeSizingPaperJoined stakeSizingPaperJoined(
        List<StakeSizingShadowDecision> decisions,
        Map<String, List<PaperTrade>> paperByRecommendation
    ) {
        List<StakeSizingPaperSimulationRow> joined = decisions.stream()
            .flatMap(decision -> paperByRecommendation.getOrDefault(decision.recommendationId(), List.of())
                .stream()
                .map(paper -> new StakeSizingPaperSimulationRow(decision, paper)))
            .toList();
        List<StakeSizingPaperSimulationRow> settled = joined.stream()
            .filter(row -> row.paper().status() == PaperTradeStatus.SETTLED)
            .filter(row -> validStake(row.paper().stake()) != null)
            .toList();
        BigDecimal baselinePnl = settled.stream()
            .map(row -> valueOrZero(row.paper().netPnl()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal simulatedPnl = settled.stream()
            .map(row -> {
                BigDecimal multiplier = rate(row.paper().netPnl(), row.paper().stake());
                if (multiplier == null || row.decision().wouldBlock()) {
                    return BigDecimal.ZERO;
                }
                return multiplier.multiply(valueOrZero(row.decision().finalStake()));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal simulatedTurnover = settled.stream()
            .map(row -> row.decision().wouldBlock() ? BigDecimal.ZERO : valueOrZero(row.decision().finalStake()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long failed = joined.stream().filter(row -> row.paper().status() == PaperTradeStatus.EXECUTION_FAILED).count();
        String warning = failed > 0
            ? "Paper execution failures present; paper PnL sample may be incomplete."
            : settled.size() < 25 ? "INSUFFICIENT_SAMPLE: paper settled joined trades < 25." : null;
        return new DiagnosticsStakeSizingPaperJoined(
            joined.size(),
            settled.size(),
            joined.stream().filter(row -> row.paper().status() == PaperTradeStatus.EXECUTED
                || row.paper().status() == PaperTradeStatus.RECOMMENDED).count(),
            failed,
            money(baselinePnl),
            money(simulatedPnl),
            rate(simulatedPnl, simulatedTurnover),
            warning
        );
    }

    private DiagnosticsStakeSizingPolicyStatus stakeSizingStatus(
        StakeSizingShadowDecision first,
        DiagnosticsStakeSizingRealJoined real
    ) {
        if (first.policyName().name().contains("KELLY")) {
            return DiagnosticsStakeSizingPolicyStatus.SHADOW_ONLY;
        }
        return real.status();
    }

    private String stakeSizingWarning(
        StakeSizingShadowDecision first,
        DiagnosticsStakeSizingPolicyStatus status,
        DiagnosticsStakeSizingRealJoined real,
        DiagnosticsStakeSizingPaperJoined paper
    ) {
        if (first.policyName().name().contains("KELLY")) {
            return "Kelly cannot be evaluated without calibrated estimated_probability.";
        }
        if (first.policyName().name().equals("TIERED_CONFIDENCE")) {
            return "Confidence not available; tiered policy falls back to base stake.";
        }
        if (real.sampleWarning() != null) {
            return real.sampleWarning();
        }
        if (paper.sampleWarning() != null) {
            return paper.sampleWarning();
        }
        if (status == DiagnosticsStakeSizingPolicyStatus.REJECTED) {
            return "Simulated final-stake performance is worse than the current real stake baseline.";
        }
        return null;
    }

    private Map<String, Long> adjustmentBreakdown(List<StakeSizingShadowDecision> decisions) {
        Map<String, Long> counts = new LinkedHashMap<>();
        decisions.stream()
            .flatMap(decision -> adjustmentTokens(decision.adjustmentSummary()).stream())
            .forEach(token -> counts.merge(token, 1L, Long::sum));
        long minFloor = minStakeFloor(decisions).floorAppliedCount();
        if (minFloor > 0) {
            counts.merge("MIN_STAKE_FLOOR", minFloor, Long::sum);
        }
        return counts;
    }

    private List<String> adjustmentTokens(String summary) {
        if (summary == null || summary.isBlank() || "[]".equals(summary.strip())) {
            return List.of();
        }
        String cleaned = summary.replace("[", "")
            .replace("]", "")
            .replace("\"", "")
            .strip();
        if (cleaned.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(cleaned.split(","))
            .map(String::strip)
            .filter(token -> !token.isBlank())
            .toList();
    }

    private List<DiagnosticsStakeSizingReductionExample> strongestReductions(
        List<StakeSizingShadowDecision> decisions,
        Map<String, List<RealBetDiagnosticRow>> realByRecommendation
    ) {
        return decisions.stream()
            .filter(decision -> decision.baseStake() != null && decision.calculatedStake() != null)
            .filter(decision -> decision.calculatedStake().compareTo(decision.baseStake()) < 0)
            .sorted(Comparator.comparing(
                decision -> decision.baseStake().subtract(decision.calculatedStake()),
                Comparator.reverseOrder()
            ))
            .limit(10)
            .map(decision -> {
                RealBetDiagnosticRow real = realByRecommendation.getOrDefault(decision.recommendationId(), List.of())
                    .stream()
                    .findFirst()
                    .orElse(null);
                return new DiagnosticsStakeSizingReductionExample(
                    decision.recommendationId(),
                    real == null ? null : real.eventName(),
                    real == null ? null : real.runnerName(),
                    decision.selectionSide().name(),
                    decision.odds(),
                    decision.baseStake(),
                    decision.calculatedStake(),
                    decision.finalStake(),
                    decision.adjustmentSummary()
                );
            })
            .toList();
    }

    private long probabilityAvailableCount(List<StakeSizingShadowDecision> decisions) {
        return decisions.stream()
            .filter(decision -> decision.decisionReason().name().contains("PROBABILITY"))
            .filter(decision -> !decision.decisionReason().name().contains("NOT_AVAILABLE"))
            .count();
    }

    private long probabilityMissingCount(List<StakeSizingShadowDecision> decisions) {
        return decisions.stream().filter(decision -> decision.decisionReason().name().contains("PROBABILITY_NOT_AVAILABLE")).count();
    }

    private long confidenceAvailableCount(List<StakeSizingShadowDecision> decisions) {
        return decisions.stream()
            .filter(decision -> decision.decisionReason().name().contains("CONFIDENCE"))
            .filter(decision -> !decision.decisionReason().name().contains("NOT_AVAILABLE"))
            .count();
    }

    private long confidenceMissingCount(List<StakeSizingShadowDecision> decisions) {
        return decisions.stream().filter(decision -> decision.decisionReason().name().contains("CONFIDENCE_NOT_AVAILABLE")).count();
    }

    private String stakeSizingPolicyKey(StakeSizingShadowDecision decision) {
        return decision.recommendationId()
            + "|"
            + decision.policyName().name()
            + "|"
            + decision.riskProfile().name()
            + "|"
            + decision.source().name();
    }

    private String stakeSizingPolicyGroupKey(StakeSizingShadowDecision decision) {
        return decision.policyName().name()
            + "|"
            + decision.riskProfile().name()
            + "|"
            + decision.source().name();
    }

    private static <T> Map<String, Long> breakdown(List<T> rows, Function<T, String> classifier) {
        return rows.stream()
            .map(classifier)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
    }

    private static BigDecimal stakeForSimulation(RealBetDiagnosticRow row) {
        BigDecimal matched = validStake(row.matchedStake());
        if (matched != null) {
            return matched;
        }
        return validStake(row.requestedStake());
    }

    private static BigDecimal validStake(BigDecimal stake) {
        return stake == null || stake.compareTo(BigDecimal.ZERO) <= 0 ? null : stake;
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal drawdownFromPnl(List<BigDecimal> pnls, boolean currentOnly) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BigDecimal pnl : pnls) {
            equity = equity.add(valueOrZero(pnl));
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            BigDecimal drawdown = peak.subtract(equity);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        return money(currentOnly ? peak.subtract(equity) : maxDrawdown);
    }

    private static BigDecimal min(List<BigDecimal> values) {
        return values.stream().min(BigDecimal::compareTo).map(DiagnosticsService::decimal).orElse(null);
    }

    private static BigDecimal max(List<BigDecimal> values) {
        return values.stream().max(BigDecimal::compareTo).map(DiagnosticsService::decimal).orElse(null);
    }

    private record StakeSizingRealSimulationRow(StakeSizingShadowDecision decision, RealBetDiagnosticRow real) {
    }

    private record StakeSizingPaperSimulationRow(StakeSizingShadowDecision decision, PaperTrade paper) {
    }

    private record StakeSizingScenarioConfig(String name, BigDecimal baseStake, BigDecimal minStake, BigDecimal maxStake) {
    }

    private static BigDecimal maxDrawdown(List<RealBetDiagnosticRow> settled) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (RealBetDiagnosticRow row : settled.stream()
            .sorted(Comparator.comparing(RealBetDiagnosticRow::settledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RealBetDiagnosticRow::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList()) {
            equity = equity.add(row.realizedProfitLoss() == null ? BigDecimal.ZERO : row.realizedProfitLoss());
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            BigDecimal drawdown = peak.subtract(equity);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        return money(maxDrawdown);
    }

    private static BigDecimal currentDrawdown(List<RealBetDiagnosticRow> settled) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        for (RealBetDiagnosticRow row : settled.stream()
            .sorted(Comparator.comparing(RealBetDiagnosticRow::settledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RealBetDiagnosticRow::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList()) {
            equity = equity.add(row.realizedProfitLoss() == null ? BigDecimal.ZERO : row.realizedProfitLoss());
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
        }
        return money(peak.subtract(equity));
    }

    private record CandidateFilter(String name, Predicate<RealBetDiagnosticRow> predicate) {
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
        values.add("BetRecommendation is consumed by paper and real prospectively, but recommendation_id matching is not enabled yet.");
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
        DiagnosticsStrategyPerformance strategyPerformance,
        DiagnosticsCandidateFilterSimulation candidateFilterSimulation,
        DiagnosticsStakeSizingShadowDiagnostics stakeSizingShadowDiagnostics,
        List<DiagnosticFinding> findings
    ) {
        List<String> values = new ArrayList<>();
        values.add("Matched paper-real pairs: " + coverage.matchedPairs() + " observations.");
        DiagnosticsStrategyPerformanceSegment allTime = strategyPerformance.allTime();
        values.add("Strategy all-time settled sample: "
            + allTime.settled()
            + " observations, win rate "
            + allTime.strikeRate()
            + ", average odds "
            + allTime.averageOdds()
            + ", break-even odds "
            + allTime.expectedBreakEvenOdds()
            + ".");
        candidateFilterSimulation.bestDeltaPnl().stream().findFirst().ifPresent(result -> values.add(
            "Best candidate filter by historical delta PnL is "
                + result.filterName()
                + " with delta "
                + result.deltaPnl()
                + " across "
                + result.includedBets()
                + " included settled observations; status "
                + result.status()
                + "."
        ));
        if (stakeSizingShadowDiagnostics.enabled()) {
            values.add("Stake sizing shadow decisions: "
                + stakeSizingShadowDiagnostics.summary().decisions()
                + " rows across "
                + stakeSizingShadowDiagnostics.summary().distinctRecommendations()
                + " recommendations; live staking remains disabled.");
        }
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

    private record PreviewPair(String paperId, String realId, String marketId, Long selectionId) {
        private static PreviewPair of(PaperTrade paper, RealBetDiagnosticRow real) {
            return new PreviewPair(paper.id(), real.id(), real.marketId(), real.selectionId());
        }
    }

    private record EvidenceMatch(
        DiagnosticsRecommendationDivergenceReason reason,
        List<DiagnosticsRecommendationDivergenceEvidence> evidence
    ) {
    }

    private record DivergenceSubject(
        String recommendationId,
        String canonicalKey,
        String exchange,
        String marketId,
        long selectionId,
        String eventName,
        String runnerName,
        String selectionSide,
        String strategyName,
        Instant firstSeenAt,
        Instant lastSeenAt
    ) {
        private static DivergenceSubject from(
            String recommendationId,
            List<PaperTrade> papers,
            List<RealBetDiagnosticRow> reals
        ) {
            RealBetDiagnosticRow real = reals.isEmpty() ? null : reals.getFirst();
            PaperTrade paper = papers.isEmpty() ? null : papers.getFirst();
            String exchange = real == null ? paper.exchange() : real.exchange();
            String marketId = real == null ? paper.marketId() : real.marketId();
            long selectionId = real == null ? paper.selectionId() : real.selectionId();
            String side = real == null ? "UNKNOWN" : real.selectionSide().name();
            String strategy = real == null ? "value-football" : real.strategyName();
            Instant firstSeen = firstSeen(papers, reals);
            Instant lastSeen = lastSeen(papers, reals);
            return new DivergenceSubject(
                recommendationId,
                BetRecommendation.canonicalKey(exchange, marketId, selectionId, real == null ? SelectionSide.UNKNOWN : real.selectionSide(), strategy),
                exchange,
                marketId,
                selectionId,
                real == null ? paper.eventName() : real.eventName(),
                real == null ? paper.runnerName() : real.runnerName(),
                side,
                strategy,
                firstSeen,
                lastSeen == null ? firstSeen : lastSeen
            );
        }

        private static Instant firstSeen(List<PaperTrade> papers, List<RealBetDiagnosticRow> reals) {
            return java.util.stream.Stream.concat(
                    papers.stream().map(PaperTrade::recommendationTimestamp),
                    reals.stream().map(RealBetDiagnosticRow::createdAt)
                )
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        }

        private static Instant lastSeen(List<PaperTrade> papers, List<RealBetDiagnosticRow> reals) {
            return java.util.stream.Stream.concat(
                    papers.stream().map(PaperTrade::recommendationTimestamp),
                    reals.stream().map(RealBetDiagnosticRow::createdAt)
                )
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        }
    }
}
