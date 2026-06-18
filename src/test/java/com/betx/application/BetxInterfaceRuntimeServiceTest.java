package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class BetxInterfaceRuntimeServiceTest {
    private static final ConfigPath CONFIG = new ConfigPath(Path.of("betx.yml"));

    @Test
    void startsPaused() {
        BetxInterfaceRuntimeService service = service(BetxConfig.defaults(), new RecordingDryRunService());

        assertThat(service.status()).isEqualTo(InterfaceStatus.PAUSED);
    }

    @Test
    void activateValidatesConfigAndRunsOneCycleImmediately() {
        RecordingDryRunService dryRun = new RecordingDryRunService();
        BetxInterfaceRuntimeService service = service(configWithEnabledExchange(), dryRun);

        BetxInterfaceRuntimeService.RuntimeState state = service.activate(CONFIG);

        assertThat(state.status()).isEqualTo(InterfaceStatus.ACTIVE);
        assertThat(dryRun.runs).isEqualTo(1);
        assertThat(state.message()).isEqualTo("BetX esta activo.");
        assertThat(state.lastCycleAt()).isEqualTo(Instant.parse("2026-06-18T10:00:00Z"));
    }

    @Test
    void activateLogsInterfaceCycleSummary() {
        List<String> logs = new ArrayList<>();
        RecordingDryRunService dryRun = new RecordingDryRunService(new DryRunSignalsResult(
            List.of(),
            List.of(),
            false,
            4,
            2,
            List.of(),
            List.of(),
            3,
            1,
            5,
            2
        ));
        BetxInterfaceRuntimeService service = service(configWithEnabledExchange(), dryRun, logs);

        service.activate(CONFIG);

        assertThat(logs).singleElement()
            .asString()
            .contains("BetX interface cycle complete")
            .contains("events=5")
            .contains("markets=3")
            .contains("snapshots=4")
            .contains("signals=0")
            .contains("failures=0");
    }

    @Test
    void activateReportsNeedsAttentionWhenNoExchangeIsEnabled() {
        RecordingDryRunService dryRun = new RecordingDryRunService();
        BetxInterfaceRuntimeService service = service(BetxConfig.defaults(), dryRun);

        BetxInterfaceRuntimeService.RuntimeState state = service.activate(CONFIG);

        assertThat(state.status()).isEqualTo(InterfaceStatus.NEEDS_ATTENTION);
        assertThat(state.message()).isEqualTo("Activa al menos una conexion de apuestas antes de iniciar BetX.");
        assertThat(dryRun.runs).isZero();
    }

    @Test
    void pauseStopsActiveState() {
        BetxInterfaceRuntimeService service = service(configWithEnabledExchange(), new RecordingDryRunService());
        service.activate(CONFIG);

        BetxInterfaceRuntimeService.RuntimeState state = service.pause();

        assertThat(state.status()).isEqualTo(InterfaceStatus.PAUSED);
        assertThat(state.message()).isEqualTo("BetX esta pausado.");
    }

    private BetxInterfaceRuntimeService service(BetxConfig config, RecordingDryRunService dryRun) {
        return service(config, dryRun, new ArrayList<>());
    }

    private BetxInterfaceRuntimeService service(BetxConfig config, RecordingDryRunService dryRun, List<String> logs) {
        StaticConfigRepository configRepository = new StaticConfigRepository(config);
        return new BetxInterfaceRuntimeService(
            new StartBetxService(configRepository),
            configRepository,
            dryRun,
            Clock.fixed(Instant.parse("2026-06-18T10:00:00Z"), ZoneOffset.UTC),
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "test-betx-interface-runtime");
                thread.setDaemon(true);
                return thread;
            }),
            logs::add
        );
    }

    private BetxConfig configWithEnabledExchange() {
        return BetxConfig.defaults().withExchanges(List.of(new ExchangeConfig(
            "betfair",
            true,
            new BetfairConfig("user", "password", "app-key")
        )));
    }

    private static final class RecordingDryRunService extends RunDryRunSignalsService {
        private int runs;
        private final DryRunSignalsResult result;

        private RecordingDryRunService() {
            this(new DryRunSignalsResult(List.of(), List.of(), false));
        }

        private RecordingDryRunService(DryRunSignalsResult result) {
            super(
                new StaticConfigRepository(BetxConfig.defaults()),
                List.<ExchangeMarketDataGateway>of(),
                null,
                new NoopBetExecutionGateway()
            );
            this.result = result;
        }

        @Override
        public DryRunSignalsResult run(ConfigPath configPath, boolean sendTelegramAlerts, boolean logSuppressedTelegramAlerts) {
            runs++;
            return result;
        }
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
}
