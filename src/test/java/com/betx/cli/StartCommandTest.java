package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.DryRunSignalsResult;
import com.betx.application.MatchIntelligenceAssessment;
import com.betx.application.MatchIntelligenceDecision;
import com.betx.application.MarketSnapshotChange;
import com.betx.application.NumericChange;
import com.betx.application.RunDryRunSignalsService;
import com.betx.application.StartBetxService;
import com.betx.application.TelegramBetConfirmationService;
import com.betx.application.TelegramConnectionService;
import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.domain.betfair.BetfairAutoBettingConfig;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.config.MarketDataConfig;
import com.betx.domain.order.BetExecutionResult;
import com.betx.domain.order.BetOrder;
import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.BetSignal;
import com.betx.domain.signal.MarketSnapshot;
import com.betx.domain.signal.RecommendationType;
import com.betx.domain.signal.RunnerAnalysis;
import com.betx.startup.StartupStatusRenderer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StartCommandTest {
    @Test
    void printsNoEnabledExchangesMessage() {
        StartCommand command = command(BetxConfig.defaults(), List.of());

        String output = captureOutput(command::run);

        assertThat(output).contains("BetX startup status");
        assertThat(output).contains("No enabled exchanges configured.");
    }

    @Test
    void printsExchangeFailuresAndSignals() {
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(
            new ExchangeConfig("betfair", true, null),
            new ExchangeConfig("matchbook", true, null)
        ));
        StartCommand command = command(config, List.of(
            new FailingGateway("betfair"),
            new SignalGateway("matchbook")
        ));

        String output = captureOutput(command::run);

        assertThat(output).contains("Exchange betfair failed: unavailable");
        assertThat(output).contains("EVENT ANALYSIS | Team A v Team B | La Liga | marketId=m-1");
        assertThat(output).contains("WATCH | runner=Team A | back=2.5 | lay=2.6 | liquidity=1200 | score=35/100 | reason=valid_market_waiting_for_movement");
    }

    @Test
    void autoBettingWithConfirmationSyncsConfirmations() {
        BetxConfig config = configWithAutoBetting(2, true, true);
        RecordingTelegramBetConfirmationService confirmations = new RecordingTelegramBetConfirmationService();
        SequencedDryRunSignalsService dryRunSignalsService = new SequencedDryRunSignalsService(
            config,
            new DryRunSignalsResult(
                List.of(new BetSignal("matchbook", "m-1", 42L, BetSide.BACK, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5), "test", "live")),
                List.of(),
                false,
                0,
                0,
                List.of(),
                List.of(analysis()),
                0,
                0,
                0,
                0
            )
        );
        StartCommand command = command(config, dryRunSignalsService, confirmations);

        String output = captureOutput(command::run);

        assertThat(output).contains("BetX is running with auto-betting confirmations.");
        assertThat(output)
            .contains("SIGNAL")
            .contains("BET CONFIRMATION")
            .doesNotContain("TELEGRAM ALERTS SUPPRESSED");
        assertThat(output)
            .doesNotContain("BET SIGNAL")
            .doesNotContain("BET AUTO");
        assertThat(dryRunSignalsService.logSuppressedTelegramAlerts()).containsExactly(false);
        assertThat(confirmations.syncedResults()).isNotEmpty();
        assertThat(confirmations.syncedResults().getFirst().signals()).hasSize(1);
    }

    @Test
    void autoBettingWithoutConfirmationSyncsAutomaticExecution() {
        BetxConfig config = configWithAutoBetting(2, true, false);
        RecordingTelegramBetConfirmationService confirmations = new RecordingTelegramBetConfirmationService();
        SequencedDryRunSignalsService dryRunSignalsService = new SequencedDryRunSignalsService(
            config,
            new DryRunSignalsResult(
                List.of(new BetSignal("betfair", "m-1", 42L, BetSide.BACK, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5), "test", "live")),
                List.of(),
                false
            )
        );
        StartCommand command = command(config, dryRunSignalsService, confirmations);

        String output = captureOutput(command::run);

        assertThat(output).contains("BetX auto-betting is enabled without Telegram confirmation.");
        assertThat(confirmations.syncedResults()).isNotEmpty();
        assertThat(confirmations.syncedResults().getFirst().signals()).hasSize(1);
    }

    @Test
    void automaticBettingSyncFailureDoesNotStopStartLoop() {
        BetxConfig config = configWithAutoBetting(2, true, false);
        FailingOnceTelegramBetConfirmationService confirmations = new FailingOnceTelegramBetConfirmationService();
        SequencedDryRunSignalsService dryRunSignalsService = new SequencedDryRunSignalsService(
            config,
            new DryRunSignalsResult(
                List.of(new BetSignal("betfair", "m-1", 42L, BetSide.BACK, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5), "test", "live")),
                List.of(),
                false
            ),
            new DryRunSignalsResult(List.of(), List.of(), true)
        );
        StartCommand command = command(config, dryRunSignalsService, confirmations);
        command.once = false;

        String output = captureOutput(command::run);

        assertThat(output).contains("TELEGRAM BET SYNC WARNING | message=Telegram API request failed.");
        assertThat(dryRunSignalsService.runs()).isEqualTo(2);
        assertThat(confirmations.calls()).isEqualTo(1);
    }

    @Test
    void continuousAutoBettingWithoutConfirmationSkipsStartupCycleExecution() {
        BetxConfig config = configWithAutoBetting(2, true, false);
        RecordingTelegramBetConfirmationService confirmations = new RecordingTelegramBetConfirmationService();
        SequencedDryRunSignalsService dryRunSignalsService = new SequencedDryRunSignalsService(
            config,
            new DryRunSignalsResult(
                List.of(new BetSignal("betfair", "m-1", 42L, BetSide.BACK, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5), "startup", "live")),
                List.of(),
                false
            ),
            new DryRunSignalsResult(List.of(), List.of(), true)
        );
        StartCommand command = command(config, dryRunSignalsService, confirmations);
        command.once = false;

        captureOutput(command::run);

        assertThat(confirmations.syncedResults()).singleElement()
            .satisfies(result -> assertThat(result.signals()).isEmpty());
    }

    @Test
    void onceRunWithLargeAnalysisSetPrintsOnlyBetAnalyses() {
        BetxConfig config = configWithAutoBetting(2, true, true);
        List<RunnerAnalysis> analyses = new java.util.ArrayList<>(java.util.stream.IntStream.range(0, 31)
            .mapToObj(index -> analysis("No Bet " + index, RecommendationType.NO_BET, "liquidity_below_minimum"))
            .toList());
        analyses.add(analysis("Bet Candidate", RecommendationType.BET, "liquidity_ok, dry_run_only"));
        SequencedDryRunSignalsService dryRunSignalsService = new SequencedDryRunSignalsService(
            config,
            new DryRunSignalsResult(
                List.of(new BetSignal("matchbook", "m-1", 42L, BetSide.BACK, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5), "test", "live")),
                List.of(),
                false,
                0,
                0,
                List.of(),
                analyses,
                0,
                0,
                0,
                0
            )
        );
        StartCommand command = command(config, dryRunSignalsService, new NoopTelegramBetConfirmationService());

        String output = captureOutput(command::run);

        assertThat(output)
            .contains("BET CONFIRMATION | runner=Bet Candidate")
            .doesNotContain("NO BET | runner=No Bet 0")
            .doesNotContain("WATCH |");
    }

    @Test
    void printsIntelligenceRecommendationForSignalAnalyses() {
        BetxConfig config = configWithAutoBetting(2, true, true);
        SequencedDryRunSignalsService dryRunSignalsService = new SequencedDryRunSignalsService(
            config,
            new DryRunSignalsResult(
                List.of(new BetSignal("matchbook", "m-1", 42L, BetSide.BACK, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5), "test", "live")),
                List.of(),
                false,
                0,
                0,
                List.of(),
                List.of(analysis("Team A", RecommendationType.BET, "liquidity_ok")),
                List.of(new MatchIntelligenceAssessment(
                    "matchbook",
                    "m-1",
                    42L,
                    MatchIntelligenceDecision.WATCH,
                    55,
                    "Context is unclear.",
                    List.of("Promotion tie is balanced"),
                    List.of("Lineups are incomplete"),
                    List.of()
                )),
                0,
                0,
                0,
                0
            )
        );
        StartCommand command = command(config, dryRunSignalsService, new NoopTelegramBetConfirmationService());

        String output = captureOutput(command::run);

        assertThat(output)
            .contains("BET CONFIRMATION | runner=Team A")
            .contains("INTELLIGENCE | runner=Team A | decision=WATCH | confidence=55/100 | summary=Context is unclear.");
    }

    @Test
    void largeCyclePrintsOnlySnapshotChangesForSignals() {
        BetxConfig config = configWithAutoBetting(2, true, true);
        List<RunnerAnalysis> analyses = new java.util.ArrayList<>(java.util.stream.IntStream.range(0, 31)
            .mapToObj(index -> analysis("No Bet " + index, RecommendationType.NO_BET, "liquidity_below_minimum"))
            .toList());
        analyses.add(analysis("Bet Candidate", RecommendationType.BET, "liquidity_ok, dry_run_only"));
        SequencedDryRunSignalsService dryRunSignalsService = new SequencedDryRunSignalsService(
            config,
            new DryRunSignalsResult(
                List.of(new BetSignal("matchbook", "m-1", 42L, BetSide.BACK, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5), "test", "live")),
                List.of(),
                false,
                0,
                0,
                List.of(change("m-1", 42L), change("m-2", 43L)),
                analyses,
                0,
                0,
                0,
                0
            )
        );
        StartCommand command = command(config, dryRunSignalsService, new NoopTelegramBetConfirmationService());

        String output = captureOutput(command::run);

        assertThat(output)
            .contains("SNAPSHOT CHANGE | exchange=matchbook | marketId=m-1 | selectionId=42")
            .contains("Snapshot changes summarized | relevant=2 | shown=1 | hidden=1")
            .doesNotContain("SNAPSHOT CHANGE | exchange=matchbook | marketId=m-2 | selectionId=43");
    }

    @Test
    void autoBettingWithConfirmationProcessesTelegramCallbacksDuringPollWait() {
        BetxConfig config = configWithAutoBetting(10, true, true);
        RecordingTelegramBetConfirmationService confirmations = new RecordingTelegramBetConfirmationService();
        SequencedDryRunSignalsService dryRunSignalsService = new SequencedDryRunSignalsService(
            config,
            new DryRunSignalsResult(List.of(), List.of(), false),
            new DryRunSignalsResult(List.of(), List.of(), true)
        );
        StartCommand command = command(config, dryRunSignalsService, confirmations);
        command.once = false;

        captureOutput(command::run);

        assertThat(confirmations.syncedResults()).hasSize(4);
        assertThat(confirmations.syncedResults().get(0).noEnabledExchanges()).isFalse();
        assertThat(confirmations.syncedResults().get(1).signals()).isEmpty();
        assertThat(confirmations.syncedResults().get(2).signals()).isEmpty();
        assertThat(confirmations.syncedResults().get(3).noEnabledExchanges()).isTrue();
    }

    @Test
    void autoBettingWithConfirmationOnceKeepsProcessingTelegramCallbacksAfterSignals() {
        BetxConfig config = configWithAutoBetting(10, true, true);
        RecordingTelegramBetConfirmationService confirmations = new RecordingTelegramBetConfirmationService();
        SequencedDryRunSignalsService dryRunSignalsService = new SequencedDryRunSignalsService(
            config,
            new DryRunSignalsResult(
                List.of(new BetSignal("matchbook", "m-1", 42L, BetSide.BACK, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5), "test", "live")),
                List.of(),
                false
            )
        );
        StartCommand command = command(config, dryRunSignalsService, confirmations);

        captureOutput(command::run);

        assertThat(confirmations.syncedResults()).hasSize(2);
        assertThat(confirmations.syncedResults().get(0).signals()).hasSize(1);
        assertThat(confirmations.syncedResults().get(1).signals()).isEmpty();
    }

    @Test
    void autoBettingWithConfirmationOnceUsesShortDrainInsteadOfFullPollInterval() {
        BetxConfig config = configWithAutoBetting(60, true, true);
        RecordingTelegramBetConfirmationService confirmations = new RecordingTelegramBetConfirmationService();
        SequencedDryRunSignalsService dryRunSignalsService = new SequencedDryRunSignalsService(
            config,
            new DryRunSignalsResult(
                List.of(new BetSignal("matchbook", "m-1", 42L, BetSide.BACK, BigDecimal.valueOf(2.5), BigDecimal.valueOf(5), "test", "live")),
                List.of(),
                false
            )
        );
        StartCommand command = command(config, dryRunSignalsService, confirmations);

        captureOutput(command::run);

        assertThat(confirmations.syncedResults()).hasSize(2);
        assertThat(confirmations.syncedResults().get(0).signals()).hasSize(1);
        assertThat(confirmations.syncedResults().get(1).signals()).isEmpty();
    }

    private StartCommand command(BetxConfig config, List<ExchangeMarketDataGateway> gateways) {
        return command(config, gateways, new NoopTelegramBetConfirmationService());
    }

    private StartCommand command(
        BetxConfig config,
        List<ExchangeMarketDataGateway> gateways,
        TelegramBetConfirmationService confirmationService
    ) {
        return command(
            config,
            new RunDryRunSignalsService(
                new StaticConfigRepository(config),
                gateways,
                new NoopTelegramConnectionService(),
                new NoopExecutionGateway()
            ),
            confirmationService
        );
    }

    private StartCommand command(
        BetxConfig config,
        RunDryRunSignalsService dryRunSignalsService,
        TelegramBetConfirmationService confirmationService
    ) {
        StaticConfigRepository repository = new StaticConfigRepository(config);
        StartCommand command = new StartCommand(
            new StartBetxService(repository),
            dryRunSignalsService,
            new StartupStatusRenderer(),
            confirmationService,
            ignored -> {
            },
            5
        );
        command.configPath = Path.of("betx.yml");
        command.once = true;
        return command;
    }

    private BetxConfig configWithPollInterval(int seconds) {
        BetxConfig defaults = BetxConfig.defaults();
        return new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            new MarketDataConfig(
                seconds,
                defaults.marketData().maxMarkets(),
                defaults.marketData().eventTypeIds(),
                defaults.marketData().marketTypeCodes(),
                defaults.marketData().scanAllMarkets(),
                defaults.marketData().betfairEventBatchSize()
            ),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );
    }

    private BetxConfig configWithAutoBetting(int seconds, boolean enabled, boolean requestConfirmation) {
        return configWithPollInterval(seconds).withExchanges(List.of(new ExchangeConfig(
            "betfair",
            true,
            new BetfairConfig(
                "user",
                "password",
                "app-key",
                null,
                new BetfairAutoBettingConfig(
                    enabled,
                    requestConfirmation,
                    BigDecimal.valueOf(5),
                    BigDecimal.valueOf(25),
                    3
                )
            )
        )));
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

    private RunnerAnalysis analysis() {
        return analysis("Team A", RecommendationType.BET, "test");
    }

    private RunnerAnalysis analysis(String runner, RecommendationType recommendation, String reason) {
        return new RunnerAnalysis(
            "matchbook",
            "m-1",
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            java.time.Instant.parse("2026-06-01T18:00:00Z"),
            42L,
            runner,
            BigDecimal.valueOf(2.5),
            BigDecimal.valueOf(2.6),
            BigDecimal.valueOf(0.04),
            BigDecimal.valueOf(1_200),
            recommendation,
            reason
        );
    }

    private MarketSnapshotChange change(String marketId, long selectionId) {
        MarketSnapshot previous = snapshot(marketId, selectionId, BigDecimal.valueOf(2.50));
        MarketSnapshot current = snapshot(marketId, selectionId, BigDecimal.valueOf(2.40));
        return new MarketSnapshotChange(
            previous,
            current,
            new NumericChange(BigDecimal.valueOf(2.50), BigDecimal.valueOf(2.40), BigDecimal.valueOf(-0.10), BigDecimal.valueOf(-4.00)),
            new NumericChange(BigDecimal.valueOf(2.60), BigDecimal.valueOf(2.60), BigDecimal.ZERO, BigDecimal.ZERO),
            new NumericChange(BigDecimal.valueOf(0.04), BigDecimal.valueOf(0.04), BigDecimal.ZERO, BigDecimal.ZERO),
            new NumericChange(BigDecimal.valueOf(1_200), BigDecimal.valueOf(1_200), BigDecimal.ZERO, BigDecimal.ZERO)
        );
    }

    private MarketSnapshot snapshot(String marketId, long selectionId, BigDecimal backPrice) {
        return new MarketSnapshot(
            "matchbook",
            marketId,
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            java.time.Instant.parse("2026-06-01T18:00:00Z"),
            selectionId,
            "Team A",
            backPrice,
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
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
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
    }

    private static final class NoopExecutionGateway implements BetExecutionGateway {
        @Override
        public BetExecutionResult execute(BetOrder order) {
            return BetExecutionResult.rejected("not implemented");
        }
    }

    private static final class NoopTelegramBetConfirmationService extends TelegramBetConfirmationService {
        private NoopTelegramBetConfirmationService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void sync(ConfigPath configPath, DryRunSignalsResult result) {
        }

        @Override
        public void sync(
            ConfigPath configPath,
            DryRunSignalsResult result,
            java.util.function.Consumer<String> outputConsumer
        ) {
        }
    }

    private static final class RecordingTelegramBetConfirmationService extends TelegramBetConfirmationService {
        private final List<DryRunSignalsResult> syncedResults = new java.util.ArrayList<>();

        private RecordingTelegramBetConfirmationService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void sync(ConfigPath configPath, DryRunSignalsResult result) {
            syncedResults.add(result);
        }

        @Override
        public void sync(
            ConfigPath configPath,
            DryRunSignalsResult result,
            java.util.function.Consumer<String> outputConsumer
        ) {
            syncedResults.add(result);
        }

        private List<DryRunSignalsResult> syncedResults() {
            return syncedResults;
        }
    }

    private static final class FailingOnceTelegramBetConfirmationService extends TelegramBetConfirmationService {
        private int calls;

        private FailingOnceTelegramBetConfirmationService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void sync(
            ConfigPath configPath,
            DryRunSignalsResult result,
            java.util.function.Consumer<String> outputConsumer
        ) {
            calls++;
            throw new IllegalStateException("Telegram API request failed.");
        }

        private int calls() {
            return calls;
        }
    }

    private static final class SequencedDryRunSignalsService extends RunDryRunSignalsService {
        private final List<DryRunSignalsResult> results;
        private int index;

        private SequencedDryRunSignalsService(BetxConfig config, DryRunSignalsResult... results) {
            super(new StaticConfigRepository(config), List.of(), new NoopTelegramConnectionService(), new NoopExecutionGateway());
            this.results = List.of(results);
        }

        private final List<Boolean> logSuppressedTelegramAlerts = new java.util.ArrayList<>();

        @Override
        public DryRunSignalsResult run(ConfigPath configPath, boolean sendTelegramAlerts, boolean logSuppressedTelegramAlerts) {
            this.logSuppressedTelegramAlerts.add(logSuppressedTelegramAlerts);
            DryRunSignalsResult result = results.get(Math.min(index, results.size() - 1));
            index++;
            return result;
        }

        private int runs() {
            return index;
        }

        @Override
        public DryRunSignalsResult run(
            ConfigPath configPath,
            boolean sendTelegramAlerts,
            boolean logSuppressedTelegramAlerts,
            java.util.function.Consumer<String> outputConsumer
        ) {
            return run(configPath, sendTelegramAlerts, logSuppressedTelegramAlerts);
        }

        private List<Boolean> logSuppressedTelegramAlerts() {
            return logSuppressedTelegramAlerts;
        }
    }

    private record FailingGateway(String exchangeName) implements ExchangeMarketDataGateway {
        @Override
        public List<com.betx.domain.signal.MarketSnapshot> listSnapshots(ExchangeConfig exchange) {
            throw new IllegalStateException("unavailable");
        }
    }

    private record SignalGateway(String exchangeName) implements ExchangeMarketDataGateway {
        @Override
        public List<com.betx.domain.signal.MarketSnapshot> listSnapshots(ExchangeConfig exchange) {
            return List.of(new com.betx.domain.signal.MarketSnapshot(
                exchangeName,
                "m-1",
                "Match Odds",
                "Team A v Team B",
                "La Liga",
                java.time.Instant.parse("2026-06-01T18:00:00Z"),
                42L,
                "Team A",
                BigDecimal.valueOf(2.50),
                BigDecimal.valueOf(2.60),
                BigDecimal.valueOf(0.04),
                BigDecimal.valueOf(1_200)
            ));
        }
    }
}
