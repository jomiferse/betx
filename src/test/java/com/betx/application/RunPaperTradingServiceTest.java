package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.BetRecommendationRepository;
import com.betx.application.port.out.CandidateFilterEvaluationRepository;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.application.port.out.MarketSnapshotRepository;
import com.betx.application.port.out.PaperSignalEvaluationRepository;
import com.betx.application.port.out.PaperTradeRepository;
import com.betx.application.port.out.PaperTradeSettlementGateway;
import com.betx.application.port.out.StructuredEventSink;
import com.betx.application.observability.BetxEvent;
import com.betx.application.observability.BetxEventLogger;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.ObservedMarketSnapshot;
import com.betx.domain.signal.RunnerType;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunPaperTradingServiceTest {
    @Test
    void recordsDrawOnlyPaperRecommendationsWithImmediatePaperExecution() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        PaperSignalEvaluationRepositoryStub evaluations = new PaperSignalEvaluationRepositoryStub();
        repository.recent.add(new ObservedMarketSnapshot(
            observedAt.minusSeconds(3600),
            snapshot(2L, "Draw", "3.70")
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(snapshot(2L, "Draw", "3.70"), snapshot(1L, "Home", "2.10")))),
            repository,
            paperTrades,
            evaluations,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        PaperTradingResult result = service.run(
            new ConfigPath(Path.of("betx.yml")),
            new BigDecimal("0.02"),
            BacktestSlippageModel.PROFIT_HAIRCUT
        );

        assertThat(result.paperTrades()).singleElement().satisfies(trade -> {
            assertThat(trade.marketId()).isEqualTo("market-1");
            assertThat(trade.runner()).isEqualTo("Draw");
            assertThat(trade.side()).isEqualTo(BetSide.BACK);
            assertThat(trade.recommendationTimestamp()).isEqualTo(observedAt);
            assertThat(trade.executionTimestamp()).isEqualTo(observedAt);
            assertThat(trade.closingTimestamp()).isNull();
            assertThat(trade.availableBackOdds()).isEqualByComparingTo("3.70");
            assertThat(trade.executionOdds()).isEqualByComparingTo("3.646");
            assertThat(trade.closingOdds()).isNull();
            assertThat(trade.decimalClvRatio()).isNull();
            assertThat(trade.result()).isNull();
            assertThat(trade.netPnl()).isEqualByComparingTo("0");
        });
        assertThat(paperTrades.saved).singleElement().satisfies(trade -> {
            assertThat(trade.status()).isEqualTo(PaperTradeStatus.EXECUTED);
            assertThat(trade.matched()).isTrue();
            assertThat(trade.paperMode()).isTrue();
            assertThat(trade.side()).isEqualTo(BetSide.BACK);
        });
        assertThat(result.marketsScanned()).isEqualTo(1);
        assertThat(result.recommendationsGenerated()).isEqualTo(1);
        assertThat(result.executionFailures()).isZero();
        assertThat(result.runnersAnalyzed()).isEqualTo(2);
        assertThat(repository.saved).hasSize(2);
        assertThat(evaluations.saved).hasSize(1);
        assertThat(result.paperSignalEvaluations()).hasSize(1);
        assertThat(evaluations.saved)
            .extracting(PaperSignalEvaluation::analyzerReason)
            .containsExactly(PaperTradeAnalyzerRejectionReason.ACCEPTED);
    }

    @Test
    void linksNewPaperTradeToCreatedCanonicalRecommendation() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        PaperSignalEvaluationRepositoryStub evaluations = new PaperSignalEvaluationRepositoryStub();
        BetRecommendationRepositoryStub recommendations = new BetRecommendationRepositoryStub();
        repository.recent.add(new ObservedMarketSnapshot(
            observedAt.minusSeconds(3600),
            snapshot(2L, "Draw", "3.70")
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(snapshot(2L, "Draw", "3.70")))),
            repository,
            paperTrades,
            evaluations,
            recommendations,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        PaperTradingResult result = service.run(
            new ConfigPath(Path.of("betx.yml")),
            new BigDecimal("0.02"),
            BacktestSlippageModel.PROFIT_HAIRCUT
        );

        assertThat(result.recommendationsGenerated()).isEqualTo(1);
        assertThat(recommendations.saved).isEmpty();
        assertThat(recommendations.upserted).singleElement().satisfies(recommendation -> {
            assertThat(recommendation.evaluationId()).isEqualTo(evaluations.saved.getFirst().evaluationId());
            assertThat(recommendation.selectionSide()).isEqualTo(com.betx.domain.order.SelectionSide.DRAW);
            assertThat(recommendation.strategyName()).isEqualTo("value-football");
            assertThat(recommendation.source()).isEqualTo(BetRecommendationSource.SHADOW);
            assertThat(recommendation.status()).isEqualTo(BetRecommendationStatus.ACTIVE);
            assertThat(recommendation.canonicalKey()).isEqualTo("betfair|market-1|2|DRAW|value-football");
            assertThat(recommendation.recommendedOdds()).isEqualByComparingTo("3.70");
        });
        assertThat(paperTrades.saved).singleElement()
            .satisfies(trade -> assertThat(trade.recommendationId()).isEqualTo(recommendations.upserted.getFirst().id()));
        assertThat(evaluations.saved).singleElement()
            .satisfies(evaluation -> assertThat(evaluation.recommendationId()).isEqualTo(recommendations.upserted.getFirst().id()));
    }

    @Test
    void linksNewPaperTradeToObservedCanonicalRecommendationWithoutCreatingExtraTrade() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        PaperSignalEvaluationRepositoryStub evaluations = new PaperSignalEvaluationRepositoryStub();
        BetRecommendationRepositoryStub recommendations = new BetRecommendationRepositoryStub();
        recommendations.nextAction = BetRecommendationUpsertAction.OBSERVED;
        repository.recent.add(new ObservedMarketSnapshot(
            observedAt.minusSeconds(3600),
            snapshot(2L, "Draw", "3.70")
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(snapshot(2L, "Draw", "3.70")))),
            repository,
            paperTrades,
            evaluations,
            recommendations,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        PaperTradingResult result = service.run(
            new ConfigPath(Path.of("betx.yml")),
            new BigDecimal("0.02"),
            BacktestSlippageModel.PROFIT_HAIRCUT
        );

        assertThat(result.recommendationsGenerated()).isEqualTo(1);
        assertThat(paperTrades.saved).singleElement().satisfies(trade -> {
            assertThat(trade.recommendationId()).isEqualTo(recommendations.upserted.getFirst().id());
            assertThat(trade.requestedOdds()).isEqualByComparingTo("3.70");
            assertThat(trade.stake()).isEqualByComparingTo("5");
            assertThat(trade.side()).isEqualTo(BetSide.BACK);
            assertThat(trade.eventName()).isEqualTo("Team A v Team B");
            assertThat(trade.runnerName()).isEqualTo("Draw");
        });
        assertThat(evaluations.saved).singleElement()
            .satisfies(evaluation -> assertThat(evaluation.recommendationId()).isEqualTo(recommendations.upserted.getFirst().id()));
    }

    @Test
    void paperTradeEventsIncludeCanonicalRecommendationId() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        PaperSignalEvaluationRepositoryStub evaluations = new PaperSignalEvaluationRepositoryStub();
        BetRecommendationRepositoryStub recommendations = new BetRecommendationRepositoryStub();
        RecordingEventSink sink = new RecordingEventSink();
        repository.recent.add(new ObservedMarketSnapshot(
            observedAt.minusSeconds(3600),
            snapshot(2L, "Draw", "3.70")
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(snapshot(2L, "Draw", "3.70")))),
            repository,
            paperTrades,
            evaluations,
            recommendations,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC),
            new BetxEventLogger(sink, Clock.fixed(observedAt, ZoneOffset.UTC))
        );

        service.run(
            new ConfigPath(Path.of("betx.yml")),
            new BigDecimal("0.02"),
            BacktestSlippageModel.PROFIT_HAIRCUT
        );

        String recommendationId = recommendations.upserted.getFirst().id();
        assertThat(sink.events)
            .filteredOn(event -> event.event().equals("paper_trade.recommended"))
            .singleElement()
            .satisfies(event -> assertThat(event.fields()).containsEntry("recommendationId", recommendationId));
        assertThat(sink.events)
            .filteredOn(event -> event.event().equals("paper_trade.executed"))
            .singleElement()
            .satisfies(event -> assertThat(event.fields()).containsEntry("recommendationId", recommendationId));
    }

    @Test
    void evaluatesCandidateFiltersInShadowAndDoesNotEmitLiveSkipEvents() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        PaperSignalEvaluationRepositoryStub evaluations = new PaperSignalEvaluationRepositoryStub();
        BetRecommendationRepositoryStub recommendations = new BetRecommendationRepositoryStub();
        CandidateFilterEvaluationRepositoryStub candidateFilters = new CandidateFilterEvaluationRepositoryStub();
        RecordingEventSink sink = new RecordingEventSink();
        repository.recent.add(new ObservedMarketSnapshot(
            observedAt.minusSeconds(3600),
            snapshot(2L, "Draw", "3.70")
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(snapshot(2L, "Draw", "3.70")))),
            repository,
            paperTrades,
            evaluations,
            recommendations,
            candidateFilters,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC),
            new BetxEventLogger(sink, Clock.fixed(observedAt, ZoneOffset.UTC))
        );

        service.run(
            new ConfigPath(Path.of("betx.yml")),
            new BigDecimal("0.02"),
            BacktestSlippageModel.PROFIT_HAIRCUT
        );

        String recommendationId = recommendations.upserted.getFirst().id();
        assertThat(candidateFilters.saved)
            .hasSize(5)
            .allSatisfy(evaluation -> assertThat(evaluation.recommendationId()).isEqualTo(recommendationId));
        assertThat(candidateFilters.saved)
            .filteredOn(evaluation -> evaluation.filterName() == CandidateFilterName.EXCLUDE_DRAW_AND_ODDS_4_PLUS)
            .singleElement()
            .satisfies(evaluation -> {
                assertThat(evaluation.decision()).isEqualTo(CandidateFilterDecision.WOULD_FILTER);
                assertThat(evaluation.reason()).isEqualTo(CandidateFilterDecisionReason.SELECTION_SIDE_DRAW);
                assertThat(evaluation.source()).isEqualTo(CandidateFilterSource.RECOMMENDATION);
            });
        assertThat(sink.events)
            .filteredOn(event -> event.event().equals("candidate_filter.shadow_evaluated"))
            .hasSize(5)
            .allSatisfy(event -> assertThat(event.fields())
                .containsEntry("recommendationId", recommendationId)
                .containsKeys("filterName", "decision", "reason"));
        assertThat(sink.events)
            .noneSatisfy(event -> assertThat(event.event()).contains("skipped"));
        assertThat(paperTrades.saved).hasSize(1);
    }

    @Test
    void doesNotLogCandidateFilterShadowEvaluationWhenUpsertOnlyObservesUnchangedEvaluation() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        PaperSignalEvaluationRepositoryStub evaluations = new PaperSignalEvaluationRepositoryStub();
        BetRecommendationRepositoryStub recommendations = new BetRecommendationRepositoryStub();
        CandidateFilterEvaluationRepositoryStub candidateFilters = new CandidateFilterEvaluationRepositoryStub();
        candidateFilters.nextAction = CandidateFilterEvaluationUpsertAction.OBSERVED_UNCHANGED;
        RecordingEventSink sink = new RecordingEventSink();
        repository.recent.add(new ObservedMarketSnapshot(
            observedAt.minusSeconds(3600),
            snapshot(2L, "Draw", "3.70")
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(snapshot(2L, "Draw", "3.70")))),
            repository,
            paperTrades,
            evaluations,
            recommendations,
            candidateFilters,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC),
            new BetxEventLogger(sink, Clock.fixed(observedAt, ZoneOffset.UTC))
        );

        service.run(
            new ConfigPath(Path.of("betx.yml")),
            new BigDecimal("0.02"),
            BacktestSlippageModel.PROFIT_HAIRCUT
        );

        assertThat(candidateFilters.saved).hasSize(5);
        assertThat(sink.events)
            .filteredOn(event -> event.event().equals("candidate_filter.shadow_evaluated"))
            .isEmpty();
        assertThat(sink.events)
            .noneSatisfy(event -> assertThat(event.event()).contains("skipped"));
        assertThat(paperTrades.saved).hasSize(1);
    }

    @Test
    void linksNewPaperTradeToAlreadyCoveredCanonicalRecommendationWithoutLoggingCoveredAgain() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        PaperSignalEvaluationRepositoryStub evaluations = new PaperSignalEvaluationRepositoryStub();
        BetRecommendationRepositoryStub recommendations = new BetRecommendationRepositoryStub();
        recommendations.nextAction = BetRecommendationUpsertAction.ALREADY_COVERED;
        RecordingEventSink sink = new RecordingEventSink();
        repository.recent.add(new ObservedMarketSnapshot(
            observedAt.minusSeconds(3600),
            snapshot(2L, "Draw", "3.70")
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(snapshot(2L, "Draw", "3.70")))),
            repository,
            paperTrades,
            evaluations,
            recommendations,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC),
            new BetxEventLogger(sink, Clock.fixed(observedAt, ZoneOffset.UTC))
        );

        service.run(
            new ConfigPath(Path.of("betx.yml")),
            new BigDecimal("0.02"),
            BacktestSlippageModel.PROFIT_HAIRCUT
        );

        assertThat(sink.events)
            .filteredOn(event -> event.event().equals("bet_recommendation.covered"))
            .isEmpty();
        assertThat(sink.events)
            .filteredOn(event -> event.event().equals("bet_recommendation.created"))
            .isEmpty();
        assertThat(paperTrades.saved).singleElement()
            .satisfies(trade -> assertThat(trade.recommendationId()).isEqualTo(recommendations.upserted.getFirst().id()));
        assertThat(evaluations.saved).singleElement()
            .satisfies(evaluation -> assertThat(evaluation.recommendationId()).isEqualTo(recommendations.upserted.getFirst().id()));
    }

    @Test
    void skipsTestMarketsBeforeSavingSnapshotsOrEvaluations() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        PaperSignalEvaluationRepositoryStub evaluations = new PaperSignalEvaluationRepositoryStub();
        MarketSnapshot testHome = testSnapshot(1L, "Test Home", RunnerType.HOME, "2.10");
        MarketSnapshot testDraw = testSnapshot(2L, "The Draw", RunnerType.DRAW, "3.70");
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(testHome, testDraw))),
            repository,
            paperTrades,
            evaluations,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        PaperTradingResult result = service.run(new ConfigPath(Path.of("betx.yml")), BigDecimal.ZERO, BacktestSlippageModel.PROFIT_HAIRCUT);

        assertThat(result.marketsScanned()).isEqualTo(1);
        assertThat(result.runnersAnalyzed()).isZero();
        assertThat(result.snapshotsSaved()).isZero();
        assertThat(result.recommendationsGenerated()).isZero();
        assertThat(result.paperSignalEvaluations()).isEmpty();
        assertThat(evaluations.saved).isEmpty();
        assertThat(repository.saved).isEmpty();
        assertThat(result.historyDiagnostics().runnerClassificationSample()).isEmpty();
        assertThat(result.historyDiagnostics().analyzerRejectionCounts())
            .containsEntry(PaperTradeAnalyzerRejectionReason.TEST_MARKET, 2);
    }

    @Test
    void skipsDuplicatePaperTradeForSameMarketSelection() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        MarketSnapshot draw = snapshot(2L, "Draw", "3.70");
        repository.recent.add(new ObservedMarketSnapshot(observedAt.minusSeconds(3600), draw));
        paperTrades.saved.add(PaperTrade.recommended(draw, observedAt.minusSeconds(60), BigDecimal.valueOf(5)));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(draw))),
            repository,
            paperTrades,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        PaperTradingResult result = service.run(new ConfigPath(Path.of("betx.yml")), BigDecimal.ZERO, BacktestSlippageModel.PROFIT_HAIRCUT);

        assertThat(result.recommendationsGenerated()).isZero();
        assertThat(result.duplicatesSkipped()).isEqualTo(1);
        assertThat(paperTrades.saved).hasSize(1);
    }

    @Test
    void doesNotCreateShadowRecommendationWhenPaperTradeAlreadyCoversSelection() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        BetRecommendationRepositoryStub recommendations = new BetRecommendationRepositoryStub();
        MarketSnapshot draw = snapshot(2L, "Draw", "3.70");
        repository.recent.add(new ObservedMarketSnapshot(observedAt.minusSeconds(3600), draw));
        paperTrades.saved.add(PaperTrade.recommended(draw, observedAt.minusSeconds(60), BigDecimal.valueOf(5)));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(draw))),
            repository,
            paperTrades,
            new PaperSignalEvaluationRepositoryStub(),
            recommendations,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        service.run(new ConfigPath(Path.of("betx.yml")), BigDecimal.ZERO, BacktestSlippageModel.PROFIT_HAIRCUT);

        assertThat(recommendations.saved).isEmpty();
        assertThat(paperTrades.saved).singleElement()
            .satisfies(trade -> assertThat(trade.recommendationId()).isNull());
    }

    @Test
    void recordsExecutionFailureWhenLiquidityIsUnavailable() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        MarketSnapshot previous = snapshot(2L, "Draw", "3.70");
        MarketSnapshot current = snapshot(2L, "Draw", "3.70", null, "0");
        paperTrades.saved.add(PaperTrade.recommended(previous, observedAt.minusSeconds(60), BigDecimal.valueOf(5)));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(current))),
            repository,
            paperTrades,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        PaperTradingResult result = service.run(new ConfigPath(Path.of("betx.yml")), BigDecimal.ZERO, BacktestSlippageModel.PROFIT_HAIRCUT);

        assertThat(result.executionFailures()).isEqualTo(1);
        assertThat(paperTrades.saved).singleElement()
            .satisfies(trade -> assertThat(trade.status()).isEqualTo(PaperTradeStatus.EXECUTION_FAILED));
    }

    @Test
    void capturesClosingPriceAndSettlesWithoutExposingCloseToRecommendation() {
        Instant recommendationAt = Instant.parse("2026-06-15T10:00:00Z");
        Instant closingAt = Instant.parse("2026-06-15T17:59:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        MarketSnapshot draw = snapshot(2L, "Draw", "3.70");
        PaperTrade executed = PaperTrade.recommended(draw, recommendationAt, BigDecimal.valueOf(5))
            .withExecuted(recommendationAt, new BigDecimal("3.70"), true);
        paperTrades.saved.add(executed);
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(snapshot(2L, "Draw", "3.50")))),
            repository,
            paperTrades,
            List.of(new StaticSettlementGateway(BacktestOutcome.WIN)),
            Clock.fixed(closingAt, ZoneOffset.UTC)
        );

        PaperTradingResult result = service.run(new ConfigPath(Path.of("betx.yml")), BigDecimal.ZERO, BacktestSlippageModel.PROFIT_HAIRCUT);

        assertThat(result.settledTrades()).isEqualTo(1);
        assertThat(result.paperTrades()).singleElement().satisfies(trade -> {
            assertThat(trade.closingTimestamp()).isEqualTo(closingAt);
            assertThat(trade.closingOdds()).isEqualByComparingTo("3.50");
            assertThat(trade.decimalClvRatio()).isEqualByComparingTo("0.05714286");
            assertThat(trade.result()).isEqualTo(BacktestOutcome.WIN);
            assertThat(trade.grossPnl()).isEqualByComparingTo("13.50");
            assertThat(trade.netPnl()).isEqualByComparingTo("13.50");
        });
        assertThat(paperTrades.saved).singleElement()
            .satisfies(trade -> assertThat(trade.status()).isEqualTo(PaperTradeStatus.SETTLED));
        assertThat(repository.findRecentRequests).isEmpty();
    }

    @Test
    void continuousSecondCycleSeesSnapshotsSavedByFirstCycleAndDoesNotDuplicateTrades() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        StatefulGateway gateway = new StatefulGateway(List.of(
            List.of(snapshot(2L, "Draw", "3.70")),
            List.of(snapshot(2L, "Draw", "3.70"))
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(gateway),
            repository,
            paperTrades,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        List<PaperTradingResult> results = service.runContinuous(
            new ConfigPath(Path.of("betx.yml")),
            BigDecimal.ZERO,
            BacktestSlippageModel.PROFIT_HAIRCUT,
            BigDecimal.ZERO,
            Duration.ofSeconds(60),
            PaperTradingLoopControl.fixedCycles(2)
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).recommendationsGenerated()).isZero();
        assertThat(results.get(1).recommendationsGenerated()).isEqualTo(1);
        assertThat(results.get(1).duplicatesSkipped()).isZero();
        assertThat(repository.saved).hasSize(2);
        assertThat(repository.findRecentRequests).containsExactly(
            "betfair|market-1|2",
            "betfair|market-1|2"
        );
        assertThat(paperTrades.saved).hasSize(1);
    }

    @Test
    void secondCycleLoadsPreviousSnapshotBeforeAnalysisAndCurrentSnapshotIsNotInOwnHistory() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        MarketSnapshot openingDraw = snapshot(2L, "Draw", "3.70");
        MarketSnapshot changedDraw = snapshot(2L, "Draw", "3.68");
        StatefulGateway gateway = new StatefulGateway(List.of(
            List.of(openingDraw),
            List.of(changedDraw)
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(gateway),
            repository,
            paperTrades,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        List<PaperTradingResult> results = service.runContinuous(
            new ConfigPath(Path.of("betx.yml")),
            BigDecimal.ZERO,
            BacktestSlippageModel.PROFIT_HAIRCUT,
            BigDecimal.ZERO,
            Duration.ofSeconds(60),
            PaperTradingLoopControl.fixedCycles(2)
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).historyDiagnostics()).satisfies(diagnostics -> {
            assertThat(diagnostics.previousSnapshotsLoaded()).isZero();
            assertThat(diagnostics.runnersWithoutPreviousSnapshot()).isEqualTo(1);
            assertThat(diagnostics.analyzerRejectionCounts())
                .containsEntry(PaperTradeAnalyzerRejectionReason.INSUFFICIENT_HISTORY, 1);
        });
        assertThat(results.get(1).historyDiagnostics()).satisfies(diagnostics -> {
            assertThat(diagnostics.previousSnapshotsLoaded()).isEqualTo(1);
            assertThat(diagnostics.runnersWithPreviousSnapshot()).isEqualTo(1);
            assertThat(diagnostics.runnersWithSufficientHistory()).isEqualTo(1);
            assertThat(diagnostics.runnersWithChangedOdds()).isEqualTo(1);
            assertThat(diagnostics.runnersWithUnchangedOdds()).isZero();
            assertThat(diagnostics.oldestPreviousSnapshot()).isEqualTo(observedAt);
            assertThat(diagnostics.newestPreviousSnapshot()).isEqualTo(observedAt);
            assertThat(diagnostics.stableMarketKeys()).isEqualTo(1);
            assertThat(diagnostics.stableSelectionKeys()).isEqualTo(1);
            assertThat(diagnostics.analyzerRejectionCounts())
                .containsEntry(PaperTradeAnalyzerRejectionReason.ACCEPTED, 1);
        });
        assertThat(repository.findRecentResponses).containsExactly(
            List.of(),
            List.of(openingDraw.bestBackPrice())
        );
        assertThat(paperTrades.saved).singleElement()
            .satisfies(trade -> assertThat(trade.availableBackOdds()).isEqualByComparingTo("3.68"));
    }

    @Test
    void paperCycleWithBetfairDrawRunnerDoesNotRejectAllRunnersAsNotDraw() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        MarketSnapshot home = snapshot(551L, "Team A", RunnerType.HOME, "2.10");
        MarketSnapshot draw = snapshot(170940L, "The Draw", RunnerType.DRAW, "3.70");
        MarketSnapshot away = snapshot(998L, "Team B", RunnerType.AWAY, "3.90");
        repository.recent.add(new ObservedMarketSnapshot(observedAt.minusSeconds(60), home));
        repository.recent.add(new ObservedMarketSnapshot(observedAt.minusSeconds(60), draw));
        repository.recent.add(new ObservedMarketSnapshot(observedAt.minusSeconds(60), away));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(home, draw, away))),
            repository,
            paperTrades,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        PaperTradingResult result = service.run(new ConfigPath(Path.of("betx.yml")), BigDecimal.ZERO, BacktestSlippageModel.PROFIT_HAIRCUT);

        assertThat(result.historyDiagnostics().analyzerRejectionCounts())
            .containsEntry(PaperTradeAnalyzerRejectionReason.NOT_DRAW, 2)
            .containsEntry(PaperTradeAnalyzerRejectionReason.ACCEPTED, 1);
        assertThat(paperTrades.saved).singleElement()
            .satisfies(trade -> assertThat(trade.selectionId()).isEqualTo(170940L));
    }

    @Test
    void warnsWhenCompleteMatchOddsMarketHasNoDrawRunner() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(
                snapshot(551L, "Team A", RunnerType.HOME, "2.10"),
                snapshot(170940L, "Market Name Missing Draw", RunnerType.UNKNOWN, "3.70"),
                snapshot(998L, "Team B", RunnerType.AWAY, "3.90")
            ))),
            repository,
            paperTrades,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        PaperTradingResult result = service.run(new ConfigPath(Path.of("betx.yml")), BigDecimal.ZERO, BacktestSlippageModel.PROFIT_HAIRCUT);

        assertThat(result.historyDiagnostics().warnings())
            .singleElement()
            .isEqualTo("PAPER_WARNING | complete Match Odds market has zero DRAW runners"
                + " | marketId=market-1 | runnerNames=Team A, Market Name Missing Draw, Team B");
    }

    @Test
    void continuousRestartResumesExistingLifecycleStateWithoutDuplicateRecommendation() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        MarketSnapshot draw = snapshot(2L, "Draw", "3.70");
        paperTrades.saved.add(PaperTrade.recommended(draw, observedAt.minusSeconds(60), BigDecimal.valueOf(5))
            .withExecuted(observedAt.minusSeconds(30), new BigDecimal("3.70"), true));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(draw))),
            repository,
            paperTrades,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        List<PaperTradingResult> results = service.runContinuous(
            new ConfigPath(Path.of("betx.yml")),
            BigDecimal.ZERO,
            BacktestSlippageModel.PROFIT_HAIRCUT,
            BigDecimal.ZERO,
            Duration.ofSeconds(60),
            PaperTradingLoopControl.fixedCycles(1)
        );

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.recommendationsGenerated()).isZero();
            assertThat(result.duplicatesSkipped()).isEqualTo(1);
        });
        assertThat(paperTrades.saved).hasSize(1);
        assertThat(paperTrades.saved.get(0).status()).isEqualTo(PaperTradeStatus.EXECUTED);
    }

    @Test
    void settlesPersistedTradesEvenWhenMarketIsNoLongerReturnedByLiveScan() {
        Instant observedAt = Instant.parse("2026-06-15T19:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        MarketSnapshot draw = snapshot(2L, "Draw", "3.70");
        paperTrades.saved.add(PaperTrade.recommended(draw, observedAt.minusSeconds(3600), BigDecimal.valueOf(5))
            .withExecuted(observedAt.minusSeconds(3590), new BigDecimal("3.70"), true)
            .withClosed(observedAt.minusSeconds(120), new BigDecimal("3.50")));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of())),
            repository,
            paperTrades,
            List.of(new StaticSettlementGateway(BacktestOutcome.LOSE)),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        PaperTradingResult result = service.run(new ConfigPath(Path.of("betx.yml")), BigDecimal.ZERO, BacktestSlippageModel.PROFIT_HAIRCUT);

        assertThat(result.settledTrades()).isEqualTo(1);
        assertThat(paperTrades.saved).singleElement().satisfies(trade -> {
            assertThat(trade.status()).isEqualTo(PaperTradeStatus.SETTLED);
            assertThat(trade.netPnl()).isEqualByComparingTo("-5");
        });
    }

    @Test
    void continuousShutdownFinishesCurrentCycleBeforeExiting() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        PaperTradeRepositoryStub paperTrades = new PaperTradeRepositoryStub();
        repository.recent.add(new ObservedMarketSnapshot(
            observedAt.minusSeconds(3600),
            snapshot(2L, "Draw", "3.70")
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(snapshot(2L, "Draw", "3.70")))),
            repository,
            paperTrades,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );
        PaperTradingLoopControl control = new PaperTradingLoopControl() {
            private int cycles;

            @Override
            public boolean shouldRunNextCycle() {
                return cycles++ == 0;
            }

            @Override
            public void waitBeforeNextCycle(Duration pollInterval) {
                requestStop();
            }
        };

        List<PaperTradingResult> results = service.runContinuous(
            new ConfigPath(Path.of("betx.yml")),
            BigDecimal.ZERO,
            BacktestSlippageModel.PROFIT_HAIRCUT,
            BigDecimal.ZERO,
            Duration.ofSeconds(60),
            control
        );

        assertThat(results).hasSize(1);
        assertThat(paperTrades.saved).hasSize(1);
    }

    @Test
    void continuousModeReportsFailedCycleAndRetriesNextCycle() {
        Instant observedAt = Instant.parse("2026-06-15T10:00:00Z");
        MarketSnapshotRepositoryStub repository = new MarketSnapshotRepositoryStub();
        ThrowingOncePaperTradeRepository paperTrades = new ThrowingOncePaperTradeRepository(
            "Betfair API request failed."
        );
        repository.recent.add(new ObservedMarketSnapshot(
            observedAt.minusSeconds(3600),
            snapshot(2L, "Draw", "3.70")
        ));
        RunPaperTradingService service = new RunPaperTradingService(
            new StaticConfigRepository(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)))),
            List.of(new StaticGateway(List.of(snapshot(2L, "Draw", "3.70")))),
            repository,
            paperTrades,
            List.of(),
            Clock.fixed(observedAt, ZoneOffset.UTC)
        );

        List<PaperTradingResult> results = service.runContinuous(
            new ConfigPath(Path.of("betx.yml")),
            BigDecimal.ZERO,
            BacktestSlippageModel.PROFIT_HAIRCUT,
            BigDecimal.ZERO,
            Duration.ofSeconds(60),
            PaperTradingLoopControl.fixedCycles(2)
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).failures())
            .containsExactly("Paper trading cycle failed: Betfair API request failed.");
        assertThat(results.get(1).failures()).isEmpty();
        assertThat(results.get(1).duplicatesSkipped()).isEqualTo(1);
        assertThat(paperTrades.saved()).hasSize(1);
    }

    private static ExchangeConfig exchange(String name, boolean enabled) {
        return new ExchangeConfig(name, enabled, new BetfairConfig("user", "password", "app-key"));
    }

    private static MarketSnapshot snapshot(long selectionId, String runnerName, String odds) {
        return snapshot(selectionId, runnerName, odds, new BigDecimal(odds).add(new BigDecimal("0.10")), "1200");
    }

    private static MarketSnapshot snapshot(long selectionId, String runnerName, RunnerType runnerType, String odds) {
        BigDecimal price = new BigDecimal(odds);
        return new MarketSnapshot(
            "betfair",
            "market-1",
            "Match Odds",
            "Team A v Team B",
            "SP1",
            Instant.parse("2026-06-15T18:00:00Z"),
            selectionId,
            runnerName,
            runnerType,
            price,
            price.add(new BigDecimal("0.10")),
            new BigDecimal("0.04"),
            new BigDecimal("1200")
        );
    }

    private static MarketSnapshot testSnapshot(long selectionId, String runnerName, RunnerType runnerType, String odds) {
        BigDecimal price = new BigDecimal(odds);
        return new MarketSnapshot(
            "betfair",
            "test-market-1",
            "Match Odds",
            "Test A v Test B",
            "SP1",
            Instant.parse("2026-06-15T18:00:00Z"),
            selectionId,
            runnerName,
            runnerType,
            price,
            price.add(new BigDecimal("0.10")),
            new BigDecimal("0.04"),
            new BigDecimal("1200")
        );
    }

    private static MarketSnapshot snapshot(long selectionId, String runnerName, String odds, BigDecimal layPrice, String liquidity) {
        BigDecimal price = new BigDecimal(odds);
        return new MarketSnapshot(
            "betfair",
            "market-1",
            "Match Odds",
            "Team A v Team B",
            "SP1",
            Instant.parse("2026-06-15T18:00:00Z"),
            selectionId,
            runnerName,
            price,
            layPrice,
            new BigDecimal("0.04"),
            new BigDecimal(liquidity)
        );
    }

    private record StaticConfigRepository(BetxConfig config) implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return config;
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }

    private record StaticGateway(List<MarketSnapshot> snapshots) implements ExchangeMarketDataGateway {
        @Override
        public String exchangeName() {
            return "betfair";
        }

        @Override
        public List<MarketSnapshot> listSnapshots(ExchangeConfig exchange) {
            return snapshots;
        }
    }

    private static final class StatefulGateway implements ExchangeMarketDataGateway {
        private final List<List<MarketSnapshot>> cycles;
        private int calls;

        private StatefulGateway(List<List<MarketSnapshot>> cycles) {
            this.cycles = cycles;
        }

        @Override
        public String exchangeName() {
            return "betfair";
        }

        @Override
        public List<MarketSnapshot> listSnapshots(ExchangeConfig exchange) {
            List<MarketSnapshot> snapshots = cycles.get(Math.min(calls, cycles.size() - 1));
            calls++;
            return snapshots;
        }
    }

    private static final class MarketSnapshotRepositoryStub implements MarketSnapshotRepository {
        private final List<ObservedMarketSnapshot> recent = new ArrayList<>();
        private final List<ObservedMarketSnapshot> saved = new ArrayList<>();
        private final List<String> findRecentRequests = new ArrayList<>();
        private final List<List<BigDecimal>> findRecentResponses = new ArrayList<>();

        @Override
        public Optional<ObservedMarketSnapshot> findLatest(String databasePath, String exchange, String marketId, long selectionId) {
            return recent.stream()
                .filter(snapshot -> snapshot.snapshot().selectionId() == selectionId)
                .findFirst();
        }

        @Override
        public List<ObservedMarketSnapshot> findRecent(String databasePath, String exchange, String marketId, long selectionId, int limit) {
            findRecentRequests.add(exchange + "|" + marketId + "|" + selectionId);
            List<ObservedMarketSnapshot> response = recent.stream()
                .filter(snapshot -> snapshot.snapshot().selectionId() == selectionId)
                .limit(limit)
                .toList();
            findRecentResponses.add(response.stream()
                .map(ObservedMarketSnapshot::snapshot)
                .map(MarketSnapshot::bestBackPrice)
                .toList());
            return response;
        }

        @Override
        public void save(String databasePath, ObservedMarketSnapshot snapshot) {
            saved.add(snapshot);
            recent.add(0, snapshot);
        }
    }

    private static final class PaperTradeRepositoryStub implements PaperTradeRepository {
        private final List<PaperTrade> saved = new ArrayList<>();

        @Override
        public Optional<PaperTrade> findByMarketSelection(String databasePath, String exchange, String marketId, long selectionId) {
            return saved.stream()
                .filter(trade -> trade.exchange().equals(exchange))
                .filter(trade -> trade.marketId().equals(marketId))
                .filter(trade -> trade.selectionId() == selectionId)
                .findFirst();
        }

        @Override
        public void upsert(String databasePath, PaperTrade trade) {
            findByMarketSelection(databasePath, trade.exchange(), trade.marketId(), trade.selectionId())
                .ifPresent(saved::remove);
            saved.add(trade);
        }

        @Override
        public List<PaperTrade> listAll(String databasePath) {
            return List.copyOf(saved);
        }
    }

    private static final class ThrowingOncePaperTradeRepository implements PaperTradeRepository {
        private final List<PaperTrade> saved = new ArrayList<>();
        private final String message;
        private boolean thrown;

        private ThrowingOncePaperTradeRepository(String message) {
            this.message = message;
        }

        @Override
        public Optional<PaperTrade> findByMarketSelection(String databasePath, String exchange, String marketId, long selectionId) {
            return saved.stream()
                .filter(trade -> trade.exchange().equals(exchange))
                .filter(trade -> trade.marketId().equals(marketId))
                .filter(trade -> trade.selectionId() == selectionId)
                .findFirst();
        }

        @Override
        public void upsert(String databasePath, PaperTrade trade) {
            findByMarketSelection(databasePath, trade.exchange(), trade.marketId(), trade.selectionId())
                .ifPresent(saved::remove);
            saved.add(trade);
        }

        @Override
        public List<PaperTrade> listAll(String databasePath) {
            if (!thrown) {
                thrown = true;
                throw new IllegalStateException(message);
            }
            return List.copyOf(saved);
        }

        private List<PaperTrade> saved() {
            return List.copyOf(saved);
        }
    }

    private static final class PaperSignalEvaluationRepositoryStub implements PaperSignalEvaluationRepository {
        private final List<PaperSignalEvaluation> saved = new ArrayList<>();

        @Override
        public void save(String databasePath, PaperSignalEvaluation evaluation) {
            saved.add(evaluation);
        }

        @Override
        public List<PaperSignalEvaluation> listLatest(String databasePath, int limit) {
            return List.copyOf(saved);
        }
    }

    private static final class BetRecommendationRepositoryStub implements BetRecommendationRepository {
        private final List<BetRecommendation> saved = new ArrayList<>();
        private final List<BetRecommendation> upserted = new ArrayList<>();
        private BetRecommendationUpsertAction nextAction;

        @Override
        public void save(String databasePath, BetRecommendation recommendation) {
            saved.add(recommendation);
        }

        @Override
        public BetRecommendationUpsertResult upsertActiveRecommendation(String databasePath, BetRecommendation recommendation) {
            upserted.add(recommendation);
            return new BetRecommendationUpsertResult(
                recommendation,
                nextAction == null ? BetRecommendationUpsertAction.CREATED : nextAction
            );
        }

        @Override
        public Optional<BetRecommendation> findById(String databasePath, String id) {
            return saved.stream().filter(recommendation -> recommendation.id().equals(id)).findFirst();
        }

        @Override
        public List<BetRecommendation> findByEvaluationId(String databasePath, String evaluationId) {
            return saved.stream()
                .filter(recommendation -> java.util.Objects.equals(recommendation.evaluationId(), evaluationId))
                .toList();
        }
    }

    private static final class CandidateFilterEvaluationRepositoryStub implements CandidateFilterEvaluationRepository {
        private final List<CandidateFilterEvaluation> saved = new ArrayList<>();
        private CandidateFilterEvaluationUpsertAction nextAction = CandidateFilterEvaluationUpsertAction.CREATED;

        @Override
        public CandidateFilterEvaluationUpsertResult upsert(String databasePath, CandidateFilterEvaluation evaluation) {
            saved.add(evaluation);
            return new CandidateFilterEvaluationUpsertResult(evaluation, nextAction);
        }

        @Override
        public List<CandidateFilterEvaluation> list(String databasePath, Instant from, Instant to) {
            return saved;
        }
    }

    private static final class RecordingEventSink implements StructuredEventSink {
        private final List<BetxEvent> events = new ArrayList<>();

        @Override
        public void emit(BetxEvent event) {
            events.add(event);
        }
    }

    private record StaticSettlementGateway(BacktestOutcome outcome) implements PaperTradeSettlementGateway {
        @Override
        public String exchangeName() {
            return "betfair";
        }

        @Override
        public Optional<BacktestOutcome> outcome(BetxConfig config, PaperTrade trade) {
            return Optional.of(outcome);
        }
    }
}
