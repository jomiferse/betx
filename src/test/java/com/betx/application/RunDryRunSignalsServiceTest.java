package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.application.port.out.MarketSnapshotRepository;
import com.betx.application.port.out.TelegramParseMode;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.ObservedMarketSnapshot;
import java.math.BigDecimal;
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
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
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
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
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
    void sendsTelegramOnlyForDryRunBetSignals() {
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

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.signals()).hasSize(1);
        assertThat(telegram.messages()).isEmpty();
        assertThat(telegram.formattedMessages()).singleElement()
            .satisfies(message -> {
                assertThat(message.parseMode()).isEqualTo(TelegramParseMode.HTML);
                assertThat(message.text())
                    .contains("<b>BETX SIGNAL</b>")
                    .contains("DRY-RUN ONLY")
                    .contains("Trigger: Odds moved favourably (-3.85%)")
                    .contains("<b>Team A v Team B</b>")
                    .contains("Bet: Team A to win @ 2.50")
                    .contains("Previous odds: 2.60 -> 2.50 (-3.85%)")
                    .contains("- Liquidity OK")
                    .contains("- Spread OK")
                    .contains("DRY-RUN ONLY. No real bet placed.");
            });
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
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(exchange("betfair", true)));
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
    void liveWithLiveBettingEnabledStillRejectsRealExecution() {
        RecordingBetExecutionGateway executionGateway = new RecordingBetExecutionGateway();
        BetxConfig config = BetxConfig.defaults()
            .withMode("live")
            .withLiveBettingEnabled(true)
            .withExchanges(List.of(exchange("betfair", true)));
        RunDryRunSignalsService service = service(config, List.of(gateway("betfair", List.of(snapshot("betfair", "1.1")), null)), executionGateway);

        DryRunSignalsResult result = service.run(CONFIG_PATH);

        assertThat(result.failures()).isEmpty();
        assertThat(executionGateway.orders()).isEmpty();
    }

    private RunDryRunSignalsService service(
        BetxConfig config,
        List<ExchangeMarketDataGateway> gateways,
        BetExecutionGateway executionGateway
    ) {
        return new RunDryRunSignalsService(new StaticConfigRepository(config), gateways, new NoopTelegramConnectionService(), executionGateway);
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

    private static final class RecordingSnapshotRepository implements MarketSnapshotRepository {
        private ObservedMarketSnapshot previous;
        private final java.util.Map<String, ObservedMarketSnapshot> previousSnapshots = new java.util.HashMap<>();
        private final List<ObservedMarketSnapshot> saved = new ArrayList<>();

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
        public void save(String databasePath, ObservedMarketSnapshot snapshot) {
            saved.add(snapshot);
        }

        private List<ObservedMarketSnapshot> saved() {
            return saved;
        }

        private void putPrevious(ObservedMarketSnapshot snapshot) {
            previousSnapshots.put(
                key(snapshot.snapshot().exchange(), snapshot.snapshot().marketId(), snapshot.snapshot().selectionId()),
                snapshot
            );
        }

        private String key(String exchange, String marketId, long selectionId) {
            return exchange + "|" + marketId + "|" + selectionId;
        }
    }
}
