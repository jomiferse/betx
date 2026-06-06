package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.DryRunSignalsResult;
import com.betx.application.RunDryRunSignalsService;
import com.betx.application.StartBetxService;
import com.betx.application.TelegramBetConfirmationService;
import com.betx.application.TelegramConnectionService;
import com.betx.application.port.out.BetExecutionGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.config.MarketDataConfig;
import com.betx.domain.order.BetExecutionResult;
import com.betx.domain.order.BetOrder;
import com.betx.domain.signal.BetSide;
import com.betx.domain.signal.BetSignal;
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
        assertThat(output).contains("WATCH | runner=Team A | back=2.5 | lay=2.6 | liquidity=1200 | reason=valid_market_waiting_for_movement");
    }

    @Test
    void liveModeWithLiveBettingDisabledRunsAsPreviewAndSyncsConfirmations() {
        BetxConfig config = BetxConfig.defaults()
            .withMode("live")
            .withExchanges(List.of(new ExchangeConfig("matchbook", true, null)));
        RecordingTelegramBetConfirmationService confirmations = new RecordingTelegramBetConfirmationService();
        StartCommand command = command(config, List.of(new SignalGateway("matchbook")), confirmations);

        String output = captureOutput(command::run);

        assertThat(output).contains("BetX is running in LIVE PREVIEW mode.");
        assertThat(output).contains("No real bets will be placed.");
        assertThat(confirmations.syncedResults()).hasSize(1);
    }

    @Test
    void liveModeProcessesTelegramCallbacksDuringPollWait() {
        BetxConfig config = configWithPollInterval(10)
            .withMode("live")
            .withExchanges(List.of(new ExchangeConfig("matchbook", true, null)));
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
    void liveModeOnceKeepsProcessingTelegramCallbacksAfterSignals() {
        BetxConfig config = configWithPollInterval(10)
            .withMode("live")
            .withExchanges(List.of(new ExchangeConfig("matchbook", true, null)));
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

        assertThat(confirmations.syncedResults()).hasSize(3);
        assertThat(confirmations.syncedResults().get(0).signals()).hasSize(1);
        assertThat(confirmations.syncedResults().get(1).signals()).isEmpty();
        assertThat(confirmations.syncedResults().get(2).signals()).isEmpty();
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

        private List<DryRunSignalsResult> syncedResults() {
            return syncedResults;
        }
    }

    private static final class SequencedDryRunSignalsService extends RunDryRunSignalsService {
        private final List<DryRunSignalsResult> results;
        private int index;

        private SequencedDryRunSignalsService(BetxConfig config, DryRunSignalsResult... results) {
            super(new StaticConfigRepository(config), List.of(), new NoopTelegramConnectionService(), new NoopExecutionGateway());
            this.results = List.of(results);
        }

        @Override
        public DryRunSignalsResult run(ConfigPath configPath, boolean sendTelegramAlerts) {
            DryRunSignalsResult result = results.get(Math.min(index, results.size() - 1));
            index++;
            return result;
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
