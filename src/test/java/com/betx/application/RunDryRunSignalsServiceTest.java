package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.BetRecommendationRepository;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.application.port.out.ExternalMatchIntelligenceGateway;
import com.betx.application.port.out.MarketSnapshotRepository;
import com.betx.application.port.out.SignalHistoryRepository;
import com.betx.application.port.out.StructuredEventSink;
import com.betx.application.port.out.TelegramParseMode;
import com.betx.application.observability.BetxEvent;
import com.betx.application.observability.BetxEventCategory;
import com.betx.application.observability.BetxEventLogger;
import com.betx.domain.betfair.BetfairAutoBettingConfig;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.config.IntelligenceAutoBettingPolicy;
import com.betx.domain.config.StorageConfig;
import com.betx.domain.config.TelegramAlertsConfig;
import com.betx.domain.config.TelegramConfig;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.ObservedMarketSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunDryRunSignalsServiceTest {
    private static final ConfigPath CONFIG_PATH = new ConfigPath(Path.of("betx.yml"));

    @Test
    void combinesAnalysesFromEnabledExchangeGateways() {
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(
            exchange("betfair", true),
            exchange("matchbook", true)
        ));
        RunDryRunSignalsService service = service(config, List.of(
            gateway("betfair", List.of(snapshot("betfair", "1.1")), null),
            gateway("matchbook", List.of(snapshot("matchbook", "m-1")), null)
        ), new RecordingBetExecutionGateway());

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.runnerAnalyses()).hasSize(2);
        assertThat(result.runnerAnalyses()).extracting("exchange").containsExactly("betfair", "matchbook");
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void exchangeFailureDoesNotStopOtherExchanges() {
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(
            exchange("betfair", true),
            exchange("matchbook", true)
        ));
        RunDryRunSignalsService service = service(config, List.of(
            gateway("betfair", List.of(), new IllegalStateException("credentials missing")),
            gateway("matchbook", List.of(snapshot("matchbook", "m-1")), null)
        ), new RecordingBetExecutionGateway());

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.runnerAnalyses()).singleElement().satisfies(analysis -> assertThat(analysis.exchange()).isEqualTo("matchbook"));
        assertThat(result.failures()).containsExactly("Exchange betfair failed: credentials missing");
    }

    @Test
    void skipsTestMarketsBeforeSavingOrAnalyzing() {
        RecordingSnapshotRepository repository = new RecordingSnapshotRepository();
        BetxConfig config = withAllTelegramSignals(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true))));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(testSnapshot()), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            repository,
            new MarketSnapshotChangeDetector(),
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC)
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.marketsRead()).isEqualTo(1);
        assertThat(result.ignoredMarkets()).isEqualTo(1);
        assertThat(result.snapshotsSaved()).isZero();
        assertThat(result.runnerAnalyses()).isEmpty();
        assertThat(repository.saved()).isEmpty();
    }

    @Test
    void includesEventDiscoveryCountersFromExchangeGateway() {
        BetxConfig config = withAllTelegramSignals(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true))));
        RunDryRunSignalsService service = service(config, List.of(gateway(
            "betfair",
            new ExchangeMarketDataResult(List.of(snapshot("betfair", "1.1")), 192, 2),
            null
        )), new RecordingBetExecutionGateway());

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.eventsRead()).isEqualTo(192);
        assertThat(result.ignoredEvents()).isEqualTo(2);
    }

    @Test
    void cleansExpiredMarketSnapshotsBeforeReadingMarketDataWhenEnabled() {
        RecordingSnapshotRepository repository = new RecordingSnapshotRepository();
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            List.of(exchange("betfair", true)),
            defaults.marketData(),
            new StorageConfig("sqlite", "data.db", true, 48),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml(),
            defaults.intelligence()
        );
        RunDryRunSignalsService service = service(
            config,
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new RecordingBetExecutionGateway(),
            new NoopExternalMatchIntelligenceGateway(),
            repository
        );

        service.run(CONFIG_PATH);

        assertThat(repository.expiredCutoffs()).containsExactly(
            Instant.parse("2026-05-29T10:01:00Z"),
            Instant.parse("2026-05-29T10:01:00Z")
        );
    }

    @Test
    void cleansExpiredMarketSnapshotsAfterSavingReturnedMarketData() {
        RecordingSnapshotRepository repository = new RecordingSnapshotRepository();
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            List.of(exchange("betfair", true)),
            defaults.marketData(),
            new StorageConfig("sqlite", "data.db", true, 48),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml(),
            defaults.intelligence()
        );
        RunDryRunSignalsService service = service(
            config,
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.old")), null)),
            new RecordingBetExecutionGateway(),
            new NoopExternalMatchIntelligenceGateway(),
            repository
        );

        service.run(CONFIG_PATH);

        assertThat(repository.operations()).containsExactly("cleanup", "save", "cleanup");
    }

    @Test
    void sendsTelegramOnlyForDryRunBetSignals() {
        RecordingSnapshotRepository repository = new RecordingSnapshotRepository();
        repository.putPrevious(new ObservedMarketSnapshot(
            Instant.parse("2026-05-31T10:00:00Z"),
            new MarketSnapshot(
                "betfair",
                "1.1",
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                42L,
                "Team A",
                BigDecimal.valueOf(2.60),
                BigDecimal.valueOf(2.70),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_000)
            )
        ));
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        BetxConfig config = withAllTelegramSignals(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true))));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            telegram,
            new RecordingBetExecutionGateway(),
            repository,
            new MarketSnapshotChangeDetector(),
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC)
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.signals()).hasSize(1);
        assertThat(result.runnerAnalyses()).singleElement()
            .satisfies(analysis -> assertThat(analysis.score().value()).isGreaterThanOrEqualTo(70));
        assertThat(telegram.messages()).isEmpty();
        assertThat(telegram.formattedMessages()).singleElement()
            .satisfies(message -> {
                assertThat(message.parseMode()).isEqualTo(TelegramParseMode.HTML);
                assertThat(message.text())
                    .contains("<b>BETX SIGNAL</b>")
                    .contains("SIGNAL ONLY")
                    .contains("Trigger: Odds moved favourably (-3.85%)")
                    .contains("<b>Team A vs Team B</b>")
                    .contains("Bet: Team A to win @ 2.50")
                    .contains("Previous odds: 2.60 -> 2.50 (-3.85%)")
                    .contains("Score:")
                    .contains("- Odds moved from 2.60 -&gt; 2.50")
                    .contains("- Liquidity increased +20.00%")
                    .contains("- Volatility is low")
                    .contains("SIGNAL ONLY. No real bet placed.");
            });
    }

    @Test
    void usesRecentSnapshotHistoryAndRequiresScoreThresholdForBetSignals() {
        RecordingSnapshotRepository repository = new RecordingSnapshotRepository();
        repository.putRecent(
            observed("betfair", "1.1", 42L, "2026-05-31T10:02:00Z", BigDecimal.valueOf(2.48), BigDecimal.valueOf(1_220)),
            observed("betfair", "1.1", 42L, "2026-05-31T10:01:00Z", BigDecimal.valueOf(2.50), BigDecimal.valueOf(1_200))
        );
        BetxConfig config = withAllTelegramSignals(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true))));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(new MarketSnapshot(
                "betfair",
                "1.1",
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                42L,
                "Runner A",
                BigDecimal.valueOf(2.47),
                BigDecimal.valueOf(2.57),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_235)
            )), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            repository,
            new MarketSnapshotChangeDetector(),
            Clock.fixed(Instant.parse("2026-05-31T10:03:00Z"), ZoneOffset.UTC)
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(repository.recentRequests()).containsExactly("betfair|1.1|42|10");
        assertThat(result.runnerAnalyses()).singleElement()
            .satisfies(analysis -> {
                assertThat(analysis.score().value()).isLessThan(70);
                assertThat(analysis.recommendation()).isEqualTo(com.betx.domain.signal.RecommendationType.WATCH);
            });
        assertThat(result.signals()).isEmpty();
    }

    @Test
    void doesNotFailCycleWhenBetSignalHasNoTelegramAlertTrigger() {
        RecordingSnapshotRepository repository = new RecordingSnapshotRepository();
        repository.putRecent(
            observed("betfair", "1.1", 42L, "2026-05-31T10:02:00Z", BigDecimal.valueOf(2.50), BigDecimal.valueOf(1_230)),
            observed("betfair", "1.1", 42L, "2026-05-31T10:01:00Z", BigDecimal.valueOf(2.55), BigDecimal.valueOf(1_220)),
            observed("betfair", "1.1", 42L, "2026-05-31T10:00:00Z", BigDecimal.valueOf(2.57), BigDecimal.valueOf(1_200))
        );
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(new MarketSnapshot(
                "betfair",
                "1.1",
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                42L,
                "Team A",
                BigDecimal.valueOf(2.485),
                BigDecimal.valueOf(2.57),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_235)
            )), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            repository,
            new MarketSnapshotChangeDetector(),
            Clock.fixed(Instant.parse("2026-05-31T10:03:00Z"), ZoneOffset.UTC)
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.signals()).hasSize(1);
        assertThat(result.failures()).isEmpty();
        assertThat(result.runnerAnalyses()).singleElement()
            .satisfies(analysis -> assertThat(analysis.reason()).doesNotContain("favorable_odds_movement", "favorable_liquidity_movement"));
    }

    @Test
    void sendsOnlyOneTelegramAlertForLiquiditySignalsInTheSameMarket() {
        RecordingSnapshotRepository repository = new RecordingSnapshotRepository();
        repository.putPrevious(new ObservedMarketSnapshot(
            Instant.parse("2026-05-31T10:00:00Z"),
            new MarketSnapshot(
                "betfair",
                "1.1",
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                42L,
                "Team A",
                BigDecimal.valueOf(2.50),
                BigDecimal.valueOf(2.60),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_000)
            )
        ));
        repository.putPrevious(new ObservedMarketSnapshot(
            Instant.parse("2026-05-31T10:00:00Z"),
            new MarketSnapshot(
                "betfair",
                "1.1",
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                43L,
                "The Draw",
                BigDecimal.valueOf(3.20),
                BigDecimal.valueOf(3.30),
                BigDecimal.valueOf(0.02),
                BigDecimal.valueOf(1_000)
            )
        ));
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        BetxConfig config = withAllTelegramSignals(BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true))));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway(
                "betfair",
                List.of(
                    new MarketSnapshot(
                        "betfair",
                        "1.1",
                        "Match Odds",
                        "Team A v Team B",
                        "La Liga",
                        Instant.parse("2026-06-01T18:00:00Z"),
                        42L,
                        "Team A",
                        BigDecimal.valueOf(2.50),
                        BigDecimal.valueOf(2.60),
                        BigDecimal.valueOf(0.04),
                        BigDecimal.valueOf(1_250)
                    ),
                    new MarketSnapshot(
                        "betfair",
                        "1.1",
                        "Match Odds",
                        "Team A v Team B",
                        "La Liga",
                        Instant.parse("2026-06-01T18:00:00Z"),
                        43L,
                        "The Draw",
                        BigDecimal.valueOf(3.20),
                        BigDecimal.valueOf(3.30),
                        BigDecimal.valueOf(0.02),
                        BigDecimal.valueOf(1_350)
                    )
                ),
                null
            )),
            telegram,
            new RecordingBetExecutionGateway(),
            repository,
            new MarketSnapshotChangeDetector(),
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC)
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.signals()).hasSize(2);
        assertThat(telegram.formattedMessages()).singleElement()
            .satisfies(message -> assertThat(message.text()).contains("Bet: Team A to win @ 2.50"));
    }

    @Test
    void suppressesTelegramAlertsDuringWarmupCycle() {
        RecordingSnapshotRepository repository = new RecordingSnapshotRepository();
        repository.previous = new ObservedMarketSnapshot(
            Instant.parse("2026-05-31T10:00:00Z"),
            new MarketSnapshot(
                "betfair",
                "1.1",
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                42L,
                "Team A",
                BigDecimal.valueOf(2.60),
                BigDecimal.valueOf(2.70),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_000)
            )
        );
        RecordingTelegramConnectionService telegram = new RecordingTelegramConnectionService();
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            telegram,
            new RecordingBetExecutionGateway(),
            repository,
            new MarketSnapshotChangeDetector(),
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC)
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH, false);

        assertThat(result.signals()).hasSize(1);
        assertThat(telegram.formattedMessages()).isEmpty();
        assertThat(telegram.messages()).isEmpty();
    }

    @Test
    void reportsNoEnabledExchanges() {
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", false)));
        RunDryRunSignalsService service = service(config, List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)), new RecordingBetExecutionGateway());

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.noEnabledExchanges()).isTrue();
        assertThat(result.signals()).isEmpty();
    }

    @Test
    void dryRunNeverCallsOrderExecutionGateway() {
        RecordingBetExecutionGateway executionGateway = new RecordingBetExecutionGateway();
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = service(config, List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)), executionGateway);

        service.run(CONFIG_PATH);

        assertThat(executionGateway.orders()).isEmpty();
    }

    @Test
    void savesSnapshotsAndComparesAgainstPreviousRunnerSnapshot() {
        RecordingSnapshotRepository repository = new RecordingSnapshotRepository();
        repository.previous = new ObservedMarketSnapshot(
            Instant.parse("2026-05-31T10:00:00Z"),
            new MarketSnapshot(
                "betfair",
                "1.1",
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                42L,
                BigDecimal.valueOf(2.00),
                BigDecimal.valueOf(2.60),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_000)
            )
        );
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            repository,
            new MarketSnapshotChangeDetector(),
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC)
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.snapshotsSaved()).isEqualTo(1);
        assertThat(result.comparisonsCalculated()).isEqualTo(1);
        assertThat(result.changes()).singleElement()
            .satisfies(change -> assertThat(change.back().percentageDelta()).isEqualByComparingTo("25.00000000"));
        assertThat(repository.saved()).singleElement()
            .satisfies(snapshot -> assertThat(snapshot.observedAt()).isEqualTo(Instant.parse("2026-05-31T10:01:00Z")));
    }

    @Test
    void autoBettingEnabledStillDoesNotExecuteInsideSignalScan() {
        RecordingBetExecutionGateway executionGateway = new RecordingBetExecutionGateway();
        BetxConfig config = BetxConfig.defaults()
            .withExchanges(List.of(new ExchangeConfig(
                "betfair",
                true,
                new BetfairConfig(
                    "user",
                    "password",
                    "app-key",
                    null,
                    new BetfairAutoBettingConfig(true, false, BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3)
                )
            )));
        RunDryRunSignalsService service = service(config, List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)), executionGateway);

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.failures()).isEmpty();
        assertThat(executionGateway.orders()).isEmpty();
    }

    @Test
    void intelligenceRejectBlocksSignalsWhenAutoBettingDoesNotRequireConfirmation() {
        BetxConfig config = BetxConfig.defaults()
            .withIntelligence(new com.betx.domain.config.IntelligenceConfig(
                true,
                "openrouter",
                "x-ai/grok-4.3",
                "OPENROUTER_API_KEY",
                null,
                20,
                70
            ))
            .withExchanges(List.of(new ExchangeConfig(
                "betfair",
                true,
                new BetfairConfig(
                    "user",
                    "password",
                    "app-key",
                    null,
                    new BetfairAutoBettingConfig(true, false, BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3)
                )
            )));
        RunDryRunSignalsService service = service(
            config,
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new RecordingBetExecutionGateway(),
            new StaticIntelligenceGateway(new MatchIntelligenceAssessment(
                "betfair",
                "1.1",
                42L,
                MatchIntelligenceDecision.REJECT,
                88,
                "Recent team news contradicts the signal.",
                List.of("Key striker ruled out"),
                List.of("Price looks poor after injury news"),
                List.of(MatchIntelligenceSource.fromUrl("https://example.com/news"))
            )),
            repositoryWithPrevious("betfair", "1.1")
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.signals()).isEmpty();
        assertThat(result.intelligenceAssessments()).singleElement()
            .satisfies(assessment -> assertThat(assessment.decision()).isEqualTo(MatchIntelligenceDecision.REJECT));
    }

    @Test
    void strictApprovePolicyAllowsApproveForUnattendedAutoBetting() {
        DryRunSignalsResult result = runWithUnattendedIntelligence(
            IntelligenceAutoBettingPolicy.STRICT_APPROVE,
            MatchIntelligenceDecision.APPROVE
        );

        assertThat(result.signals()).hasSize(1);
    }

    @Test
    void strictApprovePolicyBlocksWatchForUnattendedAutoBetting() {
        DryRunSignalsResult result = runWithUnattendedIntelligence(
            IntelligenceAutoBettingPolicy.STRICT_APPROVE,
            MatchIntelligenceDecision.WATCH
        );

        assertThat(result.signals()).isEmpty();
    }

    @Test
    void strictApprovePolicyBlocksRejectForUnattendedAutoBetting() {
        DryRunSignalsResult result = runWithUnattendedIntelligence(
            IntelligenceAutoBettingPolicy.STRICT_APPROVE,
            MatchIntelligenceDecision.REJECT
        );

        assertThat(result.signals()).isEmpty();
    }

    @Test
    void strictApprovePolicyBlocksUnavailableForUnattendedAutoBetting() {
        DryRunSignalsResult result = runWithUnattendedIntelligence(
            IntelligenceAutoBettingPolicy.STRICT_APPROVE,
            MatchIntelligenceDecision.UNAVAILABLE
        );

        assertThat(result.signals()).isEmpty();
    }

    @Test
    void blockOnlyOnRejectPolicyAllowsApproveForUnattendedAutoBetting() {
        DryRunSignalsResult result = runWithUnattendedIntelligence(
            IntelligenceAutoBettingPolicy.BLOCK_ONLY_ON_REJECT,
            MatchIntelligenceDecision.APPROVE
        );

        assertThat(result.signals()).hasSize(1);
    }

    @Test
    void blockOnlyOnRejectPolicyAllowsWatchForUnattendedAutoBetting() {
        BetxConfig config = unattendedAutoBettingConfig(IntelligenceAutoBettingPolicy.BLOCK_ONLY_ON_REJECT);
        RunDryRunSignalsService service = service(
            config,
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new RecordingBetExecutionGateway(),
            new StaticIntelligenceGateway(assessment(MatchIntelligenceDecision.WATCH)),
            repositoryWithPrevious("betfair", "1.1")
        );

        List<String> output = new ArrayList<>();
        DryRunSignalsResult result = service.run(CONFIG_PATH, true, true, output::add);

        assertThat(result.signals()).hasSize(1);
        assertThat(output)
            .anySatisfy(message -> assertThat(message)
                .contains("INTELLIGENCE BET ALLOWED | provider=openrouter")
                .contains("policy=block_only_on_reject")
                .contains("decision=WATCH"));
    }

    @Test
    void blockOnlyOnRejectPolicyBlocksRejectForUnattendedAutoBetting() {
        DryRunSignalsResult result = runWithUnattendedIntelligence(
            IntelligenceAutoBettingPolicy.BLOCK_ONLY_ON_REJECT,
            MatchIntelligenceDecision.REJECT
        );

        assertThat(result.signals()).isEmpty();
    }

    @Test
    void blockOnlyOnRejectPolicyBlocksUnavailableForUnattendedAutoBetting() {
        DryRunSignalsResult result = runWithUnattendedIntelligence(
            IntelligenceAutoBettingPolicy.BLOCK_ONLY_ON_REJECT,
            MatchIntelligenceDecision.UNAVAILABLE
        );

        assertThat(result.signals()).isEmpty();
    }

    @Test
    void intelligenceRejectIsAdvisoryWhenAutoBettingRequiresConfirmation() {
        BetxConfig config = BetxConfig.defaults()
            .withIntelligence(new com.betx.domain.config.IntelligenceConfig(
                true,
                "openrouter",
                "x-ai/grok-4.3",
                "OPENROUTER_API_KEY",
                null,
                20,
                70
            ))
            .withExchanges(List.of(new ExchangeConfig(
                "betfair",
                true,
                new BetfairConfig(
                    "user",
                    "password",
                    "app-key",
                    null,
                    new BetfairAutoBettingConfig(true, true, BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3)
                )
            )));
        RunDryRunSignalsService service = service(
            config,
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new RecordingBetExecutionGateway(),
            new StaticIntelligenceGateway(new MatchIntelligenceAssessment(
                "betfair",
                "1.1",
                42L,
                MatchIntelligenceDecision.REJECT,
                88,
                "Recent team news contradicts the signal.",
                List.of("Key striker ruled out"),
                List.of("Price looks poor after injury news"),
                List.of(MatchIntelligenceSource.fromUrl("https://example.com/news"))
            )),
            repositoryWithPrevious("betfair", "1.1")
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.signals()).hasSize(1);
        assertThat(result.intelligenceAssessments()).singleElement()
            .satisfies(assessment -> assertThat(assessment.decision()).isEqualTo(MatchIntelligenceDecision.REJECT));
    }

    @Test
    void logsIntelligenceAssessmentForBetSignals() {
        BetxConfig config = BetxConfig.defaults()
            .withIntelligence(new com.betx.domain.config.IntelligenceConfig(
                true,
                "openrouter",
                "x-ai/grok-4.3",
                "OPENROUTER_API_KEY",
                null,
                20,
                70
            ))
            .withExchanges(List.of(new ExchangeConfig(
                "betfair",
                true,
                new BetfairConfig(
                    "user",
                    "password",
                    "app-key",
                    null,
                    new BetfairAutoBettingConfig(true, true, BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3)
                )
            )));
        RunDryRunSignalsService service = service(
            config,
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new RecordingBetExecutionGateway(),
            new StaticIntelligenceGateway(new MatchIntelligenceAssessment(
                "betfair",
                "1.1",
                42L,
                MatchIntelligenceDecision.APPROVE,
                84,
                "No negative team news found.",
                List.of("No major injuries reported"),
                List.of(),
                List.of(MatchIntelligenceSource.fromUrl("https://example.com/report"))
            )),
            repositoryWithPrevious("betfair", "1.1")
        );

        List<String> output = new ArrayList<>();
        service.run(CONFIG_PATH, true, true, output::add);

        assertThat(output)
            .anySatisfy(message -> assertThat(message)
                .contains("INTELLIGENCE ASSESSMENT | provider=openrouter")
                .contains("decision=APPROVE")
                .contains("confidence=84")
                .contains("marketId=1.1")
                .contains("selectionId=42"));
    }

    @Test
    void savesSignalHistoryForBetAndWatchDecisionsWithContext() {
        RecordingSnapshotRepository snapshotRepository = repositoryWithPrevious("betfair", "1.1");
        RecordingSignalHistoryRepository historyRepository = new RecordingSignalHistoryRepository();
        BetxConfig config = BetxConfig.defaults()
            .withIntelligence(new com.betx.domain.config.IntelligenceConfig(
                true,
                "openrouter",
                "x-ai/grok-4.3",
                "OPENROUTER_API_KEY",
                null,
                20,
                70
            ))
            .withExchanges(List.of(new ExchangeConfig(
                "betfair",
                true,
                new BetfairConfig(
                    "user",
                    "password",
                    "app-key",
                    null,
                    new BetfairAutoBettingConfig(true, true, BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3)
                )
            )));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1"), weakSnapshot()), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            snapshotRepository,
            new MarketSnapshotChangeDetector(),
            new StaticIntelligenceGateway(new MatchIntelligenceAssessment(
                "betfair",
                "1.1",
                42L,
                MatchIntelligenceDecision.APPROVE,
                84,
                "No negative team news found.",
                List.of(),
                List.of(),
                List.of()
            )),
            historyRepository,
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC)
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.signalHistoryEntries()).hasSize(2);
        assertThat(historyRepository.saved()).hasSize(2);
        assertThat(historyRepository.saved().getFirst()).satisfies(entry -> {
            assertThat(entry.recommendation()).isEqualTo(com.betx.domain.signal.RecommendationType.BET);
            assertThat(entry.observedAt()).isEqualTo(Instant.parse("2026-05-31T10:01:00Z"));
            assertThat(entry.score()).isGreaterThanOrEqualTo(70);
            assertThat(entry.reason()).contains("favorable_odds_movement");
            assertThat(entry.bestBackPrice()).isEqualByComparingTo("2.50");
            assertThat(entry.backPercentageDelta()).isEqualByComparingTo("-3.84615385");
            assertThat(entry.liquidityPercentageDelta()).isEqualByComparingTo("20.00000000");
            assertThat(entry.intelligenceDecision()).isEqualTo(MatchIntelligenceDecision.APPROVE);
            assertThat(entry.intelligenceConfidence()).isEqualTo(84);
            assertThat(entry.intelligenceSummary()).isEqualTo("No negative team news found.");
        });
        assertThat(historyRepository.saved().get(1).recommendation()).isEqualTo(com.betx.domain.signal.RecommendationType.WATCH);
    }

    @Test
    void shadowPersistsBetRecommendationWhenStrategyEmitsActionableSignal() {
        RecordingSnapshotRepository snapshotRepository = repositoryWithPrevious("betfair", "1.1");
        RecordingBetRecommendationRepository recommendations = new RecordingBetRecommendationRepository();
        RecordingEventSink sink = new RecordingEventSink();
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            snapshotRepository,
            new MarketSnapshotChangeDetector(),
            new NoopExternalMatchIntelligenceGateway(),
            new RecordingSignalHistoryRepository(),
            recommendations,
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC),
            new BetxEventLogger(sink, Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC))
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.signals()).singleElement()
            .satisfies(signal -> assertThat(signal.evaluationId()).isEqualTo(recommendations.upserted().getFirst().evaluationId()));
        assertThat(recommendations.saved()).isEmpty();
        assertThat(recommendations.upserted()).singleElement().satisfies(recommendation -> {
            assertThat(recommendation.source()).isEqualTo(BetRecommendationSource.SHADOW);
            assertThat(recommendation.status()).isEqualTo(BetRecommendationStatus.ACTIVE);
            assertThat(recommendation.evaluationId()).isNotBlank();
            assertThat(recommendation.exchange()).isEqualTo("betfair");
            assertThat(recommendation.marketId()).isEqualTo("1.1");
            assertThat(recommendation.selectionId()).isEqualTo(42L);
            assertThat(recommendation.selectionSide()).isEqualTo(com.betx.domain.order.SelectionSide.HOME);
            assertThat(recommendation.strategyName()).isEqualTo("value-football");
            assertThat(recommendation.recommendedOdds()).isEqualByComparingTo("2.50");
            assertThat(recommendation.observedAt()).isEqualTo(Instant.parse("2026-05-31T10:01:00Z"));
            assertThat(recommendation.recommendedAt()).isEqualTo(Instant.parse("2026-05-31T10:01:00Z"));
            assertThat(recommendation.confidence()).isNull();
            assertThat(recommendation.edge()).isNull();
        });
        assertThat(sink.events())
            .filteredOn(event -> event.event().equals("bet_recommendation.created"))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.category()).isEqualTo(BetxEventCategory.ANALYTICS);
                assertThat(event.fields()).containsEntry("recommendationId", recommendations.upserted().getFirst().id());
                assertThat(event.fields()).containsEntry("evaluationId", recommendations.upserted().getFirst().evaluationId());
                assertThat(event.fields()).containsEntry("source", "SHADOW");
                assertThat(event.fields()).containsEntry("side", "HOME");
                assertThat(event.fields()).containsEntry("status", "ACTIVE");
                assertThat(event.fields()).containsEntry("observedCount", 1L);
            });
    }

    @Test
    void logsObservedRecommendationWhenPollingSeesSameCanonicalOpportunityAgain() {
        RecordingSnapshotRepository snapshotRepository = repositoryWithPrevious("betfair", "1.1");
        RecordingBetRecommendationRepository recommendations = new RecordingBetRecommendationRepository();
        RecordingEventSink sink = new RecordingEventSink();
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            snapshotRepository,
            new MarketSnapshotChangeDetector(),
            new NoopExternalMatchIntelligenceGateway(),
            new RecordingSignalHistoryRepository(),
            recommendations,
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC),
            new BetxEventLogger(sink, Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC))
        );

        service.run(CONFIG_PATH);
        service.run(CONFIG_PATH);

        assertThat(recommendations.upserted()).hasSize(2);
        assertThat(recommendations.upserted().get(1).observedCount()).isEqualTo(2);
        assertThat(sink.events())
            .filteredOn(event -> event.event().equals("bet_recommendation.created"))
            .hasSize(1);
        assertThat(sink.events())
            .filteredOn(event -> event.event().equals("bet_recommendation.observed"))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.fields()).containsEntry("recommendationId", recommendations.upserted().get(1).id());
                assertThat(event.fields()).containsEntry("canonicalKey", recommendations.upserted().get(1).canonicalKey());
                assertThat(event.fields()).containsEntry("observedCount", 2L);
                assertThat(event.fields()).containsEntry("status", "ACTIVE");
            });
    }

    @Test
    void doesNotLogCoveredRecommendationWhenCanonicalRecommendationWasAlreadyCovered() {
        RecordingSnapshotRepository snapshotRepository = repositoryWithPrevious("betfair", "1.1");
        RecordingBetRecommendationRepository recommendations = new RecordingBetRecommendationRepository();
        recommendations.nextAction = BetRecommendationUpsertAction.ALREADY_COVERED;
        RecordingEventSink sink = new RecordingEventSink();
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            snapshotRepository,
            new MarketSnapshotChangeDetector(),
            new NoopExternalMatchIntelligenceGateway(),
            new RecordingSignalHistoryRepository(),
            recommendations,
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC),
            new BetxEventLogger(sink, Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC))
        );

        service.run(CONFIG_PATH);

        assertThat(sink.events())
            .filteredOn(event -> event.event().equals("bet_recommendation.covered"))
            .isEmpty();
        assertThat(sink.events())
            .filteredOn(event -> event.event().equals("bet_recommendation.created"))
            .isEmpty();
    }

    @Test
    void doesNotShadowPersistRecommendationForRejectedStrategyEvaluation() {
        RecordingBetRecommendationRepository recommendations = new RecordingBetRecommendationRepository();
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(noBetSnapshot()), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            new RecordingSnapshotRepository(),
            new MarketSnapshotChangeDetector(),
            new NoopExternalMatchIntelligenceGateway(),
            new RecordingSignalHistoryRepository(),
            recommendations,
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC),
            new BetxEventLogger(StructuredEventSink.noop(), Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC))
        );

        service.run(CONFIG_PATH);

        assertThat(recommendations.saved()).isEmpty();
    }

    @Test
    void shadowRecommendationFailureDoesNotFailSignalCycle() {
        RecordingSnapshotRepository snapshotRepository = repositoryWithPrevious("betfair", "1.1");
        RecordingBetRecommendationRepository recommendations = new RecordingBetRecommendationRepository();
        recommendations.failSaves = true;
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            snapshotRepository,
            new MarketSnapshotChangeDetector(),
            new NoopExternalMatchIntelligenceGateway(),
            new RecordingSignalHistoryRepository(),
            recommendations,
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC),
            new BetxEventLogger(StructuredEventSink.noop(), Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC))
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.signals()).hasSize(1);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void doesNotSaveSignalHistoryForNoBetDecisions() {
        RecordingSignalHistoryRepository historyRepository = new RecordingSignalHistoryRepository();
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(noBetSnapshot()), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            new RecordingSnapshotRepository(),
            new MarketSnapshotChangeDetector(),
            new NoopExternalMatchIntelligenceGateway(),
            historyRepository,
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC)
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.runnerAnalyses()).singleElement()
            .satisfies(analysis -> assertThat(analysis.recommendation()).isEqualTo(com.betx.domain.signal.RecommendationType.NO_BET));
        assertThat(result.signalHistoryEntries()).isEmpty();
        assertThat(historyRepository.saved()).isEmpty();
    }

    @Test
    void signalHistoryFailureDoesNotFailSignalCycle() {
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
        RecordingSignalHistoryRepository historyRepository = new RecordingSignalHistoryRepository();
        historyRepository.failSaves = true;
        RunDryRunSignalsService service = new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new NoopTelegramConnectionService(),
            new RecordingBetExecutionGateway(),
            repositoryWithPrevious("betfair", "1.1"),
            new MarketSnapshotChangeDetector(),
            new NoopExternalMatchIntelligenceGateway(),
            historyRepository,
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC)
        );

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.signals()).hasSize(1);
        assertThat(result.failures()).isEmpty();
        assertThat(result.snapshotsSaved()).isEqualTo(1);
    }

    private RunDryRunSignalsService service(
        BetxConfig config,
        List<ExchangeMarketDataGateway> gateways,
        BetExecutionGateway executionGateway
    ) {
        return new RunDryRunSignalsService(new StaticConfigRepository(config), gateways, new NoopTelegramConnectionService(), executionGateway);
    }

    private BetxConfig withAllTelegramSignals(BetxConfig config) {
        TelegramConfig telegram = config.telegram();
        return new BetxConfig(
            config.app(),
            new TelegramConfig(
                telegram.enabled(),
                telegram.botToken(),
                telegram.botTokenEnv(),
                telegram.chatIdEnv(),
                telegram.botUsername(),
                telegram.chatId(),
                telegram.connectedAt(),
                telegram.username(),
                telegram.firstName(),
                telegram.pendingLinkCode(),
                new TelegramAlertsConfig("all_signals", "30m")
            ),
            config.betfair(),
            config.exchanges(),
            config.marketData(),
            config.storage(),
            config.paper(),
            config.risk(),
            config.strategies(),
            config.ml(),
            config.intelligence(),
            config.resilience()
        );
    }

    private RunDryRunSignalsService service(
        BetxConfig config,
        List<ExchangeMarketDataGateway> gateways,
        BetExecutionGateway executionGateway,
        ExternalMatchIntelligenceGateway intelligenceGateway
    ) {
        return service(config, gateways, executionGateway, intelligenceGateway, new RecordingSnapshotRepository());
    }

    private RunDryRunSignalsService service(
        BetxConfig config,
        List<ExchangeMarketDataGateway> gateways,
        BetExecutionGateway executionGateway,
        ExternalMatchIntelligenceGateway intelligenceGateway,
        MarketSnapshotRepository snapshotRepository
    ) {
        return new RunDryRunSignalsService(
            new StaticConfigRepository(config),
            gateways,
            new NoopTelegramConnectionService(),
            executionGateway,
            snapshotRepository,
            new MarketSnapshotChangeDetector(),
            intelligenceGateway,
            Clock.fixed(Instant.parse("2026-05-31T10:01:00Z"), ZoneOffset.UTC)
        );
    }

    private DryRunSignalsResult runWithUnattendedIntelligence(
        IntelligenceAutoBettingPolicy policy,
        MatchIntelligenceDecision decision
    ) {
        BetxConfig config = unattendedAutoBettingConfig(policy);
        RunDryRunSignalsService service = service(
            config,
            List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)),
            new RecordingBetExecutionGateway(),
            new StaticIntelligenceGateway(assessment(decision)),
            repositoryWithPrevious("betfair", "1.1")
        );
        return service.run(CONFIG_PATH);
    }

    private BetxConfig unattendedAutoBettingConfig(IntelligenceAutoBettingPolicy policy) {
        return BetxConfig.defaults()
            .withIntelligence(new com.betx.domain.config.IntelligenceConfig(
                true,
                "openrouter",
                "x-ai/grok-4.3",
                "OPENROUTER_API_KEY",
                null,
                20,
                70,
                policy
            ))
            .withExchanges(List.of(new ExchangeConfig(
                "betfair",
                true,
                new BetfairConfig(
                    "user",
                    "password",
                    "app-key",
                    null,
                    new BetfairAutoBettingConfig(true, false, BigDecimal.valueOf(5), BigDecimal.valueOf(25), 3)
                )
            )));
    }

    private MatchIntelligenceAssessment assessment(MatchIntelligenceDecision decision) {
        return new MatchIntelligenceAssessment(
            "betfair",
            "1.1",
            42L,
            decision,
            decision == MatchIntelligenceDecision.UNAVAILABLE ? 0 : 88,
            "Assessment summary.",
            List.of("Assessment reason"),
            List.of(),
            List.of(MatchIntelligenceSource.fromUrl("https://example.com/news"))
        );
    }

    private RecordingSnapshotRepository repositoryWithPrevious(String exchange, String marketId) {
        RecordingSnapshotRepository repository = new RecordingSnapshotRepository();
        repository.putPrevious(new ObservedMarketSnapshot(
            Instant.parse("2026-05-31T10:00:00Z"),
            new MarketSnapshot(
                exchange,
                marketId,
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                42L,
                "Team A",
                BigDecimal.valueOf(2.60),
                BigDecimal.valueOf(2.70),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_000)
            )
        ));
        return repository;
    }

    private String captureOutput(Runnable runnable) {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            runnable.run();
        } finally {
            System.setOut(original);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private ExchangeConfig exchange(String name, boolean enabled) {
        return new ExchangeConfig(name, enabled, new BetfairConfig("user", "password", "app-key"));
    }

    private ExchangeMarketDataGateway gateway(String name, List<MarketSnapshot> snapshots, RuntimeException failure) {
        return gateway(name, new ExchangeMarketDataResult(snapshots, 0, 0), failure);
    }

    private ExchangeMarketDataGateway gateway(String name, ExchangeMarketDataResult result, RuntimeException failure) {
        return new ExchangeMarketDataGateway() {
            @Override
            public String exchangeName() {
                return name;
            }

            @Override
            public List<MarketSnapshot> listSnapshots(ExchangeConfig exchange) {
                return listMarketData(exchange).snapshots();
            }

            @Override
            public ExchangeMarketDataResult listMarketData(ExchangeConfig exchange) {
                if (failure != null) {
                    throw failure;
                }
                return result;
            }
        };
    }

    private MarketSnapshot snapshot(String exchange, String marketId) {
        return new MarketSnapshot(
            exchange,
            marketId,
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            42L,
            "Team A",
            BigDecimal.valueOf(2.50),
            BigDecimal.valueOf(2.60),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(1_200)
        );
    }

    private MarketSnapshot weakSnapshot() {
        return new MarketSnapshot(
            "betfair",
            "1.2",
            "Match Odds",
            "Team C v Team D",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            43L,
            "Team C",
            BigDecimal.valueOf(2.50),
            BigDecimal.valueOf(2.60),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(1_200)
        );
    }

    private MarketSnapshot noBetSnapshot() {
        return new MarketSnapshot(
            "betfair",
            "1.3",
            "Match Odds",
            "Team E v Team F",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            44L,
            "Team E",
            BigDecimal.valueOf(2.50),
            BigDecimal.valueOf(4.00),
            BigDecimal.valueOf(0.60),
            BigDecimal.valueOf(50)
        );
    }

    private ObservedMarketSnapshot observed(
        String exchange,
        String marketId,
        long selectionId,
        String observedAt,
        BigDecimal back,
        BigDecimal liquidity
    ) {
        return new ObservedMarketSnapshot(
            Instant.parse(observedAt),
            new MarketSnapshot(
                exchange,
                marketId,
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                Instant.parse("2026-06-01T18:00:00Z"),
                selectionId,
                "Team A",
                back,
                BigDecimal.valueOf(2.60),
                BigDecimal.valueOf(0.04),
                liquidity
            )
        );
    }

    private MarketSnapshot testSnapshot() {
        return new MarketSnapshot(
            "betfair",
            "1.test",
            "Match Odds",
            "Test C v Test V",
            "",
            Instant.parse("2026-06-01T18:00:00Z"),
            42L,
            "Runner A",
            BigDecimal.valueOf(2.50),
            BigDecimal.valueOf(2.60),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(1_200)
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
        public void saveTelegramFields(ConfigPath path, java.util.Map<String, Object> fields) {
        }
    }

    private static final class NoopTelegramConnectionService extends TelegramConnectionService {
        private NoopTelegramConnectionService() {
            super(null, null, null);
        }

        @Override
        public boolean sendMessageIfConnected(ConfigPath configPath, String text) {
            return false;
        }

        @Override
        public boolean sendMessageIfConnected(ConfigPath configPath, String text, TelegramParseMode parseMode) {
            return false;
        }
    }

    private static final class RecordingTelegramConnectionService extends TelegramConnectionService {
        private final List<String> messages = new ArrayList<>();
        private final List<FormattedMessage> formattedMessages = new ArrayList<>();

        private RecordingTelegramConnectionService() {
            super(null, null, null);
        }

        @Override
        public boolean sendMessageIfConnected(ConfigPath configPath, String text) {
            messages.add(text);
            return true;
        }

        @Override
        public boolean sendMessageIfConnected(ConfigPath configPath, String text, TelegramParseMode parseMode) {
            formattedMessages.add(new FormattedMessage(text, parseMode));
            return true;
        }

        private List<String> messages() {
            return messages;
        }

        private List<FormattedMessage> formattedMessages() {
            return formattedMessages;
        }
    }

    private record FormattedMessage(String text, TelegramParseMode parseMode) {
    }

    private static final class RecordingBetExecutionGateway implements BetExecutionGateway {
        private final List<Object> orders = new ArrayList<>();

        @Override
        public com.betx.domain.order.BetExecutionResult execute(com.betx.domain.order.BetOrder order) {
            orders.add(order);
            return com.betx.domain.order.BetExecutionResult.rejected("not expected");
        }

        private List<Object> orders() {
            return orders;
        }
    }

    private record StaticIntelligenceGateway(MatchIntelligenceAssessment assessment) implements ExternalMatchIntelligenceGateway {
        @Override
        public MatchIntelligenceAssessment assess(MatchIntelligenceRequest request) {
            return assessment;
        }
    }

    private static final class RecordingSnapshotRepository implements MarketSnapshotRepository {
        private ObservedMarketSnapshot previous;
        private final java.util.Map<String, ObservedMarketSnapshot> previousSnapshots = new java.util.HashMap<>();
        private final java.util.Map<String, List<ObservedMarketSnapshot>> recentSnapshots = new java.util.HashMap<>();
        private final List<String> recentRequests = new ArrayList<>();
        private final List<ObservedMarketSnapshot> saved = new ArrayList<>();
        private final List<Instant> expiredCutoffs = new ArrayList<>();
        private final List<String> operations = new ArrayList<>();

        @Override
        public Optional<ObservedMarketSnapshot> findLatest(String databasePath, String exchange, String marketId, long selectionId) {
            ObservedMarketSnapshot stored = previousSnapshots.get(key(exchange, marketId, selectionId));
            if (stored != null) {
                return Optional.of(stored);
            }
            return Optional.ofNullable(previous)
                .filter(snapshot -> snapshot.snapshot().exchange().equals(exchange))
                .filter(snapshot -> snapshot.snapshot().marketId().equals(marketId))
                .filter(snapshot -> snapshot.snapshot().selectionId() == selectionId);
        }

        @Override
        public List<ObservedMarketSnapshot> findRecent(String databasePath, String exchange, String marketId, long selectionId, int limit) {
            recentRequests.add(key(exchange, marketId, selectionId) + "|" + limit);
            List<ObservedMarketSnapshot> stored = recentSnapshots.get(key(exchange, marketId, selectionId));
            if (stored != null) {
                return stored.stream().limit(limit).toList();
            }
            return findLatest(databasePath, exchange, marketId, selectionId).stream().toList();
        }

        @Override
        public void save(String databasePath, ObservedMarketSnapshot snapshot) {
            operations.add("save");
            saved.add(snapshot);
        }

        @Override
        public int deleteExpiredMarkets(String databasePath, Instant marketStartTimeBefore) {
            operations.add("cleanup");
            expiredCutoffs.add(marketStartTimeBefore);
            return 0;
        }

        private List<ObservedMarketSnapshot> saved() {
            return saved;
        }

        private List<Instant> expiredCutoffs() {
            return expiredCutoffs;
        }

        private List<String> operations() {
            return operations;
        }

        private void putPrevious(ObservedMarketSnapshot snapshot) {
            previousSnapshots.put(
                key(snapshot.snapshot().exchange(), snapshot.snapshot().marketId(), snapshot.snapshot().selectionId()),
                snapshot
            );
        }

        private void putRecent(ObservedMarketSnapshot... snapshots) {
            for (ObservedMarketSnapshot snapshot : snapshots) {
                recentSnapshots.computeIfAbsent(
                    key(snapshot.snapshot().exchange(), snapshot.snapshot().marketId(), snapshot.snapshot().selectionId()),
                    ignored -> new ArrayList<>()
                ).add(snapshot);
                putPrevious(snapshot);
            }
        }

        private List<String> recentRequests() {
            return recentRequests;
        }

        private String key(String exchange, String marketId, long selectionId) {
            return exchange + "|" + marketId + "|" + selectionId;
        }
    }

    private static final class RecordingSignalHistoryRepository implements SignalHistoryRepository {
        private final List<SignalHistoryEntry> saved = new ArrayList<>();
        private boolean failSaves;

        @Override
        public void saveDecision(String databasePath, SignalHistoryEntry entry) {
            if (failSaves) {
                throw new IllegalStateException("database unavailable");
            }
            saved.add(entry);
        }

        @Override
        public void linkIntent(String databasePath, SignalHistoryKey key, com.betx.domain.order.BetIntent intent) {
        }

        @Override
        public void updateOrderState(String databasePath, com.betx.domain.order.BetIntent intent) {
        }

        private List<SignalHistoryEntry> saved() {
            return saved;
        }
    }

    private static final class RecordingBetRecommendationRepository implements BetRecommendationRepository {
        private final List<BetRecommendation> saved = new ArrayList<>();
        private final java.util.Map<String, BetRecommendation> canonical = new java.util.LinkedHashMap<>();
        private final List<BetRecommendation> upserted = new ArrayList<>();
        private boolean failSaves;
        private BetRecommendationUpsertAction nextAction;

        @Override
        public void save(String databasePath, BetRecommendation recommendation) {
            if (failSaves) {
                throw new IllegalStateException("recommendation database unavailable");
            }
            saved.add(recommendation);
        }

        @Override
        public BetRecommendationUpsertResult upsertActiveRecommendation(String databasePath, BetRecommendation recommendation) {
            if (failSaves) {
                throw new IllegalStateException("recommendation database unavailable");
            }
            BetRecommendation existing = canonical.get(recommendation.canonicalKey());
            if (existing == null) {
                canonical.put(recommendation.canonicalKey(), recommendation);
                upserted.add(recommendation);
                return new BetRecommendationUpsertResult(
                    recommendation,
                    nextAction == null ? BetRecommendationUpsertAction.CREATED : nextAction
                );
            }
            BetRecommendation updated = existing.observedAgain(
                recommendation.evaluationId(),
                recommendation.recommendedOdds(),
                recommendation.observedAt()
            );
            canonical.put(updated.canonicalKey(), updated);
            upserted.add(updated);
            return new BetRecommendationUpsertResult(
                updated,
                nextAction == null ? BetRecommendationUpsertAction.OBSERVED : nextAction
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

        private List<BetRecommendation> saved() {
            return saved;
        }

        private List<BetRecommendation> upserted() {
            return upserted;
        }
    }

    private static final class RecordingEventSink implements StructuredEventSink {
        private final List<BetxEvent> events = new ArrayList<>();

        @Override
        public void emit(BetxEvent event) {
            events.add(event);
        }

        private List<BetxEvent> events() {
            return events;
        }
    }
}
