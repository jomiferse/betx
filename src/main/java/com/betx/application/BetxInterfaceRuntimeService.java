package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.common.BetxException;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.startup.StartupStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BetxInterfaceRuntimeService {
    private static final Logger LOG = LoggerFactory.getLogger(BetxInterfaceRuntimeService.class);

    private final StartBetxService startBetxService;
    private final BetxConfigRepository configRepository;
    private final RunDryRunSignalsService dryRunSignalsService;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final Consumer<String> runtimeLog;
    private final AtomicReference<RuntimeState> state;
    private ScheduledFuture<?> scheduledRun;

    @Autowired
    public BetxInterfaceRuntimeService(
        StartBetxService startBetxService,
        BetxConfigRepository configRepository,
        RunDryRunSignalsService dryRunSignalsService
    ) {
        this(startBetxService, configRepository, dryRunSignalsService, Clock.systemUTC(), daemonExecutor());
    }

    BetxInterfaceRuntimeService(
        StartBetxService startBetxService,
        BetxConfigRepository configRepository,
        RunDryRunSignalsService dryRunSignalsService,
        Clock clock,
        ScheduledExecutorService executor
    ) {
        this(
            startBetxService,
            configRepository,
            dryRunSignalsService,
            clock,
            executor,
            message -> LOG.info("{}", message)
        );
    }

    BetxInterfaceRuntimeService(
        StartBetxService startBetxService,
        BetxConfigRepository configRepository,
        RunDryRunSignalsService dryRunSignalsService,
        Clock clock,
        ScheduledExecutorService executor,
        Consumer<String> runtimeLog
    ) {
        this.startBetxService = startBetxService;
        this.configRepository = configRepository;
        this.dryRunSignalsService = dryRunSignalsService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.executor = executor == null ? daemonExecutor() : executor;
        this.runtimeLog = runtimeLog == null ? message -> LOG.info("{}", message) : runtimeLog;
        this.state = new AtomicReference<>(new RuntimeState(
            InterfaceStatus.PAUSED,
            "BetX esta pausado.",
            Instant.now(this.clock),
            null
        ));
    }

    public synchronized RuntimeState activate(ConfigPath configPath) {
        try {
            BetxConfig config = configRepository.load(configPath);
            if (config.enabledExchanges().isEmpty()) {
                return update(InterfaceStatus.NEEDS_ATTENTION, "Activa al menos una conexion de apuestas antes de iniciar BetX.");
            }
            StartupStatus startup = startBetxService.start(configPath);
            if (startup.storagePath() == null || startup.storagePath().isBlank()) {
                return update(InterfaceStatus.NEEDS_ATTENTION, "Completa la configuracion de almacenamiento antes de iniciar BetX.");
            }
            runCycle(configPath, startup);
            scheduleNextCycles(configPath, startup);
            return update(InterfaceStatus.ACTIVE, "BetX esta activo.");
        } catch (BetxException | IllegalStateException exc) {
            return update(InterfaceStatus.NEEDS_ATTENTION, safeMessage(exc));
        }
    }

    public synchronized RuntimeState pause() {
        if (scheduledRun != null) {
            scheduledRun.cancel(false);
            scheduledRun = null;
        }
        return update(InterfaceStatus.PAUSED, "BetX esta pausado.");
    }

    public InterfaceStatus status() {
        return state.get().status();
    }

    public RuntimeState state() {
        return state.get();
    }

    private RuntimeState update(InterfaceStatus status, String message) {
        RuntimeState updated = new RuntimeState(status, message, Instant.now(clock), state.get().lastCycleAt());
        state.set(updated);
        return updated;
    }

    private RuntimeState updateLastCycle() {
        RuntimeState current = state.get();
        RuntimeState updated = new RuntimeState(
            current.status(),
            current.message(),
            Instant.now(clock),
            Instant.now(clock)
        );
        state.set(updated);
        return updated;
    }

    private void runCycle(ConfigPath configPath, StartupStatus startup) {
        DryRunSignalsResult result = dryRunSignalsService.run(
            configPath,
            !startup.requestConfirmation(),
            !startup.requestConfirmation()
        );
        logCycleResult(result);
        updateLastCycle();
    }

    private void logCycleResult(DryRunSignalsResult result) {
        runtimeLog.accept(String.format(
            "BetX interface cycle complete | snapshots=%d | comparisons=%d | events=%d | ignoredEvents=%d | markets=%d | ignoredMarkets=%d | signals=%d | signalHistory=%d | failures=%d",
            result.snapshotsSaved(),
            result.comparisonsCalculated(),
            result.eventsRead(),
            result.ignoredEvents(),
            result.marketsRead(),
            result.ignoredMarkets(),
            result.signals().size(),
            result.signalHistoryEntries().size(),
            result.failures().size()
        ));
    }

    private void scheduleNextCycles(ConfigPath configPath, StartupStatus startup) {
        if (scheduledRun != null) {
            scheduledRun.cancel(false);
        }
        long intervalSeconds = Math.max(startup.pollIntervalSeconds(), 1);
        scheduledRun = executor.scheduleWithFixedDelay(
            () -> safeScheduledCycle(configPath, startup),
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );
    }

    private void safeScheduledCycle(ConfigPath configPath, StartupStatus startup) {
        if (state.get().status() != InterfaceStatus.ACTIVE) {
            return;
        }
        try {
            runCycle(configPath, startup);
        } catch (RuntimeException exc) {
            update(InterfaceStatus.NEEDS_ATTENTION, "BetX necesita atencion antes de continuar.");
        }
    }

    private String safeMessage(RuntimeException exc) {
        String message = exc.getMessage();
        if (message != null && message.contains("Configuration file not found")) {
            return "No se encontro la configuracion de BetX.";
        }
        return "BetX necesita atencion antes de continuar.";
    }

    private static ScheduledExecutorService daemonExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "betx-interface-runtime");
            thread.setDaemon(true);
            return thread;
        });
    }

    public record RuntimeState(InterfaceStatus status, String message, Instant updatedAt, Instant lastCycleAt) {
    }
}
