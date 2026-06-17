# BetX Interface Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal Spring Boot web interface vertical slice for BetX with one home page, real status/activity endpoints, and activate/pause controls.

**Architecture:** Keep the existing CLI as the internal operation surface. Add a Picocli `interface` command that launches a separate servlet web context guarded by `betx.interface.enabled=true`. Put runtime/status/activity orchestration in application services, expose only product-facing DTOs through web adapters, and keep the frontend as static HTML/CSS/JS that calls the minimal endpoints.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Picocli, Spring MVC via `spring-boot-starter-web`, JUnit 5, AssertJ.

---

## File Structure

- Modify `pom.xml`: replace raw `spring-web` dependency with `spring-boot-starter-web` so the interface can serve HTTP while preserving `RestClient`.
- Modify `src/main/java/com/betx/cli/BetxRootCommand.java`: register `InterfaceCommand`.
- Create `src/main/java/com/betx/cli/InterfaceCommand.java`: Picocli command for `interface --config ... --port ... --no-browser`.
- Create `src/main/java/com/betx/application/BetxInterfaceLauncher.java`: launches the servlet web context and opens the browser when enabled.
- Create `src/main/java/com/betx/application/BetxInterfaceProperties.java`: reads `betx.interface.config`.
- Create `src/main/java/com/betx/application/InterfaceStatus.java`: enum `ACTIVE`, `PAUSED`, `NEEDS_ATTENTION`.
- Create `src/main/java/com/betx/application/BetxInterfaceStatusView.java`: status response record.
- Create `src/main/java/com/betx/application/BetxInterfaceRuntimeService.java`: owns active/paused state and schedules cycles using `RunDryRunSignalsService`.
- Create `src/main/java/com/betx/application/BetxInterfaceStatusService.java`: maps config/runtime/account checks to user-facing status.
- Create `src/main/java/com/betx/application/BetxInterfaceActivityService.java`: maps recent `BetIntent` rows to user-facing activity.
- Create `src/main/java/com/betx/application/BetxInterfaceActivityItem.java`: activity response record.
- Create `src/main/java/com/betx/adapter/web/BetxInterfaceController.java`: minimal JSON endpoints.
- Create `src/main/java/com/betx/adapter/web/BetxInterfacePageController.java`: redirects `/` to `/interface/` only in interface mode.
- Create `src/main/resources/static/interface/index.html`: single page app.
- Create `src/main/resources/static/interface/styles.css`: restrained user-facing styles.
- Create `src/main/resources/static/interface/app.js`: polling and activate/pause actions.
- Create tests under `src/test/java/com/betx/application/` and `src/test/java/com/betx/adapter/web/`.

## Task 1: Web Dependency And CLI Command

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/betx/cli/BetxRootCommand.java`
- Create: `src/main/java/com/betx/cli/InterfaceCommand.java`
- Create: `src/main/java/com/betx/application/BetxInterfaceLauncher.java`
- Test: `src/test/java/com/betx/cli/InterfaceCommandTest.java`

- [ ] **Step 1: Write the failing CLI command tests**

Create `src/test/java/com/betx/cli/InterfaceCommandTest.java`:

```java
package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BetxInterfaceLauncher;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InterfaceCommandTest {
    @Test
    void launchesInterfaceWithDefaults() {
        RecordingLauncher launcher = new RecordingLauncher(0);
        InterfaceCommand command = new InterfaceCommand(launcher);

        int exitCode = command.call();

        assertThat(exitCode).isZero();
        assertThat(launcher.request.configPath()).isEqualTo(Path.of("betx.yml"));
        assertThat(launcher.request.port()).isEqualTo(8080);
        assertThat(launcher.request.openBrowser()).isTrue();
    }

    @Test
    void launchesInterfaceWithCustomOptions() {
        RecordingLauncher launcher = new RecordingLauncher(0);
        InterfaceCommand command = new InterfaceCommand(launcher);
        command.configPath = Path.of("custom.yml");
        command.port = 9090;
        command.noBrowser = true;

        int exitCode = command.call();

        assertThat(exitCode).isZero();
        assertThat(launcher.request.configPath()).isEqualTo(Path.of("custom.yml"));
        assertThat(launcher.request.port()).isEqualTo(9090);
        assertThat(launcher.request.openBrowser()).isFalse();
    }

    private static final class RecordingLauncher extends BetxInterfaceLauncher {
        private final int exitCode;
        private LaunchRequest request;

        private RecordingLauncher(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public int launch(LaunchRequest request) {
            this.request = request;
            return exitCode;
        }
    }
}
```

- [ ] **Step 2: Run the failing test**

Run: `mvn -q test -Dtest=InterfaceCommandTest`

Expected: compilation fails because `InterfaceCommand` and `BetxInterfaceLauncher` do not exist.

- [ ] **Step 3: Add Spring MVC dependency**

In `pom.xml`, replace:

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>
```

with:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

- [ ] **Step 4: Add the launcher**

Create `src/main/java/com/betx/application/BetxInterfaceLauncher.java`:

```java
package com.betx.application;

import com.betx.BetxApplication;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class BetxInterfaceLauncher {
    public int launch(LaunchRequest request) {
        String url = "http://localhost:" + request.port() + "/interface/";
        SpringApplicationBuilder builder = new SpringApplicationBuilder(BetxApplication.class)
            .web(WebApplicationType.SERVLET)
            .bannerMode(org.springframework.boot.Banner.Mode.OFF)
            .properties(Map.of(
                "betx.interface.enabled", "true",
                "betx.interface.config", request.configPath().toString(),
                "server.port", String.valueOf(request.port()),
                "spring.main.log-startup-info", "false"
            ));
        ConfigurableApplicationContext context = builder.run();
        System.out.println("BetX interface available at " + url);
        openBrowser(url, request.openBrowser());
        context.registerShutdownHook();
        try {
            Thread.currentThread().join();
            return 0;
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            context.close();
            return 130;
        }
    }

    private void openBrowser(String url, boolean enabled) {
        if (!enabled || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (RuntimeException | java.io.IOException exc) {
            System.out.println("Could not open browser automatically. Open " + url);
        }
    }

    public record LaunchRequest(Path configPath, int port, boolean openBrowser) {
        public LaunchRequest {
            configPath = configPath == null ? Path.of("betx.yml") : configPath;
            port = port <= 0 ? 8080 : port;
        }
    }
}
```

- [ ] **Step 5: Add the Picocli command**

Create `src/main/java/com/betx/cli/InterfaceCommand.java`:

```java
package com.betx.cli;

import com.betx.application.BetxInterfaceLauncher;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "interface", description = "Start the BetX user interface.")
public class InterfaceCommand implements Callable<Integer> {
    private final BetxInterfaceLauncher launcher;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--port", defaultValue = "8080", description = "Local web port.")
    int port;

    @Option(names = "--no-browser", description = "Do not open the browser automatically.")
    boolean noBrowser;

    public InterfaceCommand(BetxInterfaceLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public Integer call() {
        return launcher.launch(new BetxInterfaceLauncher.LaunchRequest(configPath, port, !noBrowser));
    }
}
```

- [ ] **Step 6: Register the command**

In `src/main/java/com/betx/cli/BetxRootCommand.java`, add `InterfaceCommand.class` to the `subcommands` list after `StartCommand.class`.

- [ ] **Step 7: Run the test**

Run: `mvn -q test -Dtest=InterfaceCommandTest`

Expected: PASS.

- [ ] **Step 8: Commit**

Run:

```bash
git add pom.xml src/main/java/com/betx/cli/BetxRootCommand.java src/main/java/com/betx/cli/InterfaceCommand.java src/main/java/com/betx/application/BetxInterfaceLauncher.java src/test/java/com/betx/cli/InterfaceCommandTest.java
git commit -m "Add BetX interface command"
```

## Task 2: Runtime State And Scheduling

**Files:**
- Create: `src/main/java/com/betx/application/InterfaceStatus.java`
- Create: `src/main/java/com/betx/application/BetxInterfaceRuntimeService.java`
- Test: `src/test/java/com/betx/application/BetxInterfaceRuntimeServiceTest.java`

- [ ] **Step 1: Write runtime tests**

Create `src/test/java/com/betx/application/BetxInterfaceRuntimeServiceTest.java`:

```java
package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeMarketDataGateway;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
            })
        );
    }

    private BetxConfig configWithEnabledExchange() {
        return BetxConfig.defaults().withExchanges(List.of(new com.betx.domain.config.ExchangeConfig(
            "betfair",
            true,
            new com.betx.domain.betfair.BetfairConfig("user", "password", "app-key")
        )));
    }

    private static final class RecordingDryRunService extends RunDryRunSignalsService {
        private int runs;

        private RecordingDryRunService() {
            super(
                new StaticConfigRepository(BetxConfig.defaults()),
                List.<ExchangeMarketDataGateway>of(),
                null,
                new NoopBetExecutionGateway()
            );
        }

        @Override
        public DryRunSignalsResult run(ConfigPath configPath, boolean sendTelegramAlerts, boolean logSuppressedTelegramAlerts) {
            runs++;
            return new DryRunSignalsResult(List.of(), List.of(), false);
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
```

Add this import to the test:

```java
import com.betx.application.NoopBetExecutionGateway;
```

- [ ] **Step 2: Run the failing test**

Run: `mvn -q test -Dtest=BetxInterfaceRuntimeServiceTest`

Expected: compilation fails because the runtime classes do not exist.

- [ ] **Step 3: Add the status enum**

Create `src/main/java/com/betx/application/InterfaceStatus.java`:

```java
package com.betx.application;

public enum InterfaceStatus {
    ACTIVE,
    PAUSED,
    NEEDS_ATTENTION
}
```

- [ ] **Step 4: Add runtime service**

Create `src/main/java/com/betx/application/BetxInterfaceRuntimeService.java`:

```java
package com.betx.application;

import com.betx.common.BetxException;
import com.betx.common.ConfigException;
import com.betx.application.port.out.BetxConfigRepository;
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
import org.springframework.stereotype.Service;

@Service
public class BetxInterfaceRuntimeService {
    private final StartBetxService startBetxService;
    private final BetxConfigRepository configRepository;
    private final RunDryRunSignalsService dryRunSignalsService;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final AtomicReference<RuntimeState> state;
    private ScheduledFuture<?> scheduledRun;

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
        this.startBetxService = startBetxService;
        this.configRepository = configRepository;
        this.dryRunSignalsService = dryRunSignalsService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.executor = executor == null ? daemonExecutor() : executor;
        this.state = new AtomicReference<>(new RuntimeState(
            InterfaceStatus.PAUSED,
            "BetX esta pausado.",
            Instant.now(this.clock)
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
        } catch (ConfigException | BetxException | IllegalStateException exc) {
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
        RuntimeState updated = new RuntimeState(status, message, Instant.now(clock));
        state.set(updated);
        return updated;
    }

    private void runCycle(ConfigPath configPath, StartupStatus startup) {
        dryRunSignalsService.run(configPath, !startup.requestConfirmation(), !startup.requestConfirmation());
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
        if (message == null || message.isBlank()) {
            return "BetX necesita atencion antes de continuar.";
        }
        return "BetX necesita atencion antes de continuar.";
    }

    public record RuntimeState(InterfaceStatus status, String message, Instant updatedAt) {
    }

    private static ScheduledExecutorService daemonExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "betx-interface-runtime");
            thread.setDaemon(true);
            return thread;
        });
    }
}
```

- [ ] **Step 5: Run and adjust**

Run: `mvn -q test -Dtest=BetxInterfaceRuntimeServiceTest`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/java/com/betx/application/InterfaceStatus.java src/main/java/com/betx/application/BetxInterfaceRuntimeService.java src/test/java/com/betx/application/BetxInterfaceRuntimeServiceTest.java
git commit -m "Add interface runtime state"
```

## Task 3: Status And Activity Application Services

**Files:**
- Create: `src/main/java/com/betx/application/BetxInterfaceProperties.java`
- Create: `src/main/java/com/betx/application/BetxInterfaceStatusView.java`
- Create: `src/main/java/com/betx/application/BetxInterfaceStatusService.java`
- Create: `src/main/java/com/betx/application/BetxInterfaceActivityItem.java`
- Create: `src/main/java/com/betx/application/BetxInterfaceActivityService.java`
- Test: `src/test/java/com/betx/application/BetxInterfaceStatusServiceTest.java`
- Test: `src/test/java/com/betx/application/BetxInterfaceActivityServiceTest.java`

- [ ] **Step 1: Write status service tests**

Create `src/test/java/com/betx/application/BetxInterfaceStatusServiceTest.java` with tests for paused and active state:

```java
package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeAccountGateway;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BetxInterfaceStatusServiceTest {
    @Test
    void returnsPausedStatusWithBalanceWhenAvailable() {
        BetxInterfaceRuntimeService.RuntimeState state = new BetxInterfaceRuntimeService.RuntimeState(
            InterfaceStatus.PAUSED,
            "BetX esta pausado.",
            Instant.parse("2026-06-18T10:00:00Z")
        );
        BetxInterfaceStatusService service = new BetxInterfaceStatusService(
            () -> state,
            new StaticConfigRepository(BetxConfig.defaults()),
            (config, exchange) -> Optional.of(BigDecimal.valueOf(100)),
            new BetxInterfaceProperties(Path.of("betx.yml"))
        );

        BetxInterfaceStatusView view = service.status();

        assertThat(view.status()).isEqualTo(InterfaceStatus.PAUSED);
        assertThat(view.message()).isEqualTo("BetX esta pausado.");
        assertThat(view.availableBalance()).isEqualByComparingTo("100");
        assertThat(view.manualConfirmationEnabled()).isFalse();
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
```

- [ ] **Step 2: Write activity service tests**

Create `src/test/java/com/betx/application/BetxInterfaceActivityServiceTest.java`:

```java
package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetIntentRepository;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentStage;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BetxInterfaceActivityServiceTest {
    @Test
    void mapsRecentOperationsToUserFacingRows() {
        RecordingBetIntentRepository repository = new RecordingBetIntentRepository(List.of(new BetIntent(
            "intent-1",
            "betfair",
            "market-1",
            42L,
            "Real Madrid vs Barcelona",
            "Match Odds",
            "Empate",
            "Movimiento favorable",
            BigDecimal.valueOf(3.2),
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(5),
            "accepted",
            BetIntentStage.EXECUTED,
            Instant.parse("2026-06-18T09:00:00Z"),
            Instant.parse("2026-06-18T09:01:00Z")
        )));
        BetxInterfaceActivityService service = new BetxInterfaceActivityService(
            repository,
            new BetxInterfaceProperties(Path.of("betx.yml"))
        );

        List<BetxInterfaceActivityItem> items = service.recent();

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("intent-1");
            assertThat(item.event()).isEqualTo("Real Madrid vs Barcelona");
            assertThat(item.selection()).isEqualTo("Empate");
            assertThat(item.odds()).isEqualByComparingTo("3.2");
            assertThat(item.amount()).isEqualByComparingTo("5");
            assertThat(item.statusLabel()).isEqualTo("Realizada");
        });
    }

    private record RecordingBetIntentRepository(List<BetIntent> intents) implements BetIntentRepository {
        @Override public java.util.Optional<BetIntent> findActiveByKey(String databasePath, String exchange, String marketId, long selectionId) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<BetIntent> findLatestByKeySince(String databasePath, String exchange, String marketId, long selectionId, Instant since) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<BetIntent> findById(String databasePath, String id) { return java.util.Optional.empty(); }
        @Override public List<BetIntent> listRecent(String databasePath, int limit) { return intents; }
        @Override public List<BetIntent> listByStages(String databasePath, List<BetIntentStage> stages, int limit) { return List.of(); }
        @Override public long countByStages(String databasePath, List<BetIntentStage> stages) { return 0; }
        @Override public BigDecimal sumSelectedStakeByStageSince(String databasePath, BetIntentStage stage, Instant since) { return BigDecimal.ZERO; }
        @Override public void save(String databasePath, BetIntent intent) { }
        @Override public void update(String databasePath, BetIntent intent) { }
    }
}
```

- [ ] **Step 3: Run failing tests**

Run: `mvn -q test -Dtest=BetxInterfaceStatusServiceTest,BetxInterfaceActivityServiceTest`

Expected: compilation fails because the service and DTO classes do not exist.

- [ ] **Step 4: Implement properties and DTO records**

Create the records:

```java
package com.betx.application;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record BetxInterfaceProperties(Path configPath) {
    public BetxInterfaceProperties(@Value("${betx.interface.config:betx.yml}") Path configPath) {
        this.configPath = configPath == null ? Path.of("betx.yml") : configPath;
    }
}
```

```java
package com.betx.application;

import java.math.BigDecimal;
import java.time.Instant;

public record BetxInterfaceStatusView(
    InterfaceStatus status,
    String message,
    BigDecimal availableBalance,
    Instant lastUpdatedAt,
    boolean manualConfirmationEnabled
) {
}
```

```java
package com.betx.application;

import java.math.BigDecimal;
import java.time.Instant;

public record BetxInterfaceActivityItem(
    String id,
    String event,
    String selection,
    BigDecimal odds,
    BigDecimal amount,
    String statusLabel,
    BigDecimal profitLoss,
    Instant updatedAt
) {
}
```

- [ ] **Step 5: Implement status service**

Create `src/main/java/com/betx/application/BetxInterfaceStatusService.java`:

```java
package com.betx.application;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.ExchangeAccountGateway;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class BetxInterfaceStatusService {
    private final Supplier<BetxInterfaceRuntimeService.RuntimeState> runtimeState;
    private final BetxConfigRepository configRepository;
    private final ExchangeAccountGateway accountGateway;
    private final BetxInterfaceProperties properties;

    public BetxInterfaceStatusService(
        BetxInterfaceRuntimeService runtimeService,
        BetxConfigRepository configRepository,
        ExchangeAccountGateway accountGateway,
        BetxInterfaceProperties properties
    ) {
        this(runtimeService::state, configRepository, accountGateway, properties);
    }

    BetxInterfaceStatusService(
        Supplier<BetxInterfaceRuntimeService.RuntimeState> runtimeState,
        BetxConfigRepository configRepository,
        ExchangeAccountGateway accountGateway,
        BetxInterfaceProperties properties
    ) {
        this.runtimeState = runtimeState;
        this.configRepository = configRepository;
        this.accountGateway = accountGateway;
        this.properties = properties;
    }

    public BetxInterfaceStatusView status() {
        BetxInterfaceRuntimeService.RuntimeState state = runtimeState.get();
        BetxConfig config;
        try {
            config = configRepository.load(new ConfigPath(properties.configPath()));
        } catch (RuntimeException exc) {
            return new BetxInterfaceStatusView(
                InterfaceStatus.NEEDS_ATTENTION,
                "BetX necesita atencion antes de continuar.",
                null,
                state.updatedAt(),
                false
            );
        }
        BigDecimal availableBalance = config.enabledExchanges().stream()
            .findFirst()
            .flatMap(exchange -> safeBalance(config, exchange.name()))
            .orElse(null);
        boolean manualConfirmationEnabled = config.enabledExchanges().stream()
            .filter(exchange -> "betfair".equals(exchange.name()))
            .anyMatch(exchange -> exchange.betfair().autoBetting().enabled()
                && exchange.betfair().autoBetting().requestConfirmation());
        return new BetxInterfaceStatusView(
            state.status(),
            state.message(),
            availableBalance,
            state.updatedAt(),
            manualConfirmationEnabled
        );
    }

    private Optional<BigDecimal> safeBalance(BetxConfig config, String exchange) {
        try {
            return accountGateway.availableBalance(config, exchange);
        } catch (RuntimeException exc) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 6: Implement activity service**

Create `src/main/java/com/betx/application/BetxInterfaceActivityService.java`:

```java
package com.betx.application;

import com.betx.application.port.out.BetIntentRepository;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetIntent;
import com.betx.domain.order.BetIntentStage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BetxInterfaceActivityService {
    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final BetIntentRepository intentRepository;
    private final BetxInterfaceProperties properties;

    public BetxInterfaceActivityService(BetIntentRepository intentRepository, BetxInterfaceProperties properties) {
        this.intentRepository = intentRepository;
        this.properties = properties;
    }

    public List<BetxInterfaceActivityItem> recent() {
        return intentRepository.listRecent(new ConfigPath(properties.configPath()).value().toString(), RECENT_ACTIVITY_LIMIT).stream()
            .map(this::item)
            .toList();
    }

    private BetxInterfaceActivityItem item(BetIntent intent) {
        return new BetxInterfaceActivityItem(
            intent.id(),
            blankToDash(intent.eventName()),
            blankToDash(intent.runnerName()),
            intent.odds(),
            intent.selectedStake(),
            label(intent.stage()),
            intent.realizedProfitLoss(),
            intent.updatedAt()
        );
    }

    private String label(BetIntentStage stage) {
        return switch (stage) {
            case AWAITING_CONFIRMATION, AWAITING_STAKE -> "Pendiente";
            case EXECUTED -> "Realizada";
            case SETTLED -> "Finalizada";
            case CANCELLED -> "Descartada";
            case FAILED -> "Necesita atencion";
        };
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
```

- [ ] **Step 7: Run tests**

Run: `mvn -q test -Dtest=BetxInterfaceStatusServiceTest,BetxInterfaceActivityServiceTest`

Expected: PASS.

- [ ] **Step 8: Commit**

Run:

```bash
git add src/main/java/com/betx/application/BetxInterfaceProperties.java src/main/java/com/betx/application/BetxInterfaceStatusView.java src/main/java/com/betx/application/BetxInterfaceStatusService.java src/main/java/com/betx/application/BetxInterfaceActivityItem.java src/main/java/com/betx/application/BetxInterfaceActivityService.java src/test/java/com/betx/application/BetxInterfaceStatusServiceTest.java src/test/java/com/betx/application/BetxInterfaceActivityServiceTest.java
git commit -m "Add interface status and activity services"
```

## Task 4: HTTP Endpoints

**Files:**
- Create: `src/main/java/com/betx/adapter/web/BetxInterfaceController.java`
- Create: `src/main/java/com/betx/adapter/web/BetxInterfacePageController.java`
- Test: `src/test/java/com/betx/adapter/web/BetxInterfaceControllerTest.java`

- [ ] **Step 1: Write controller tests**

Create `src/test/java/com/betx/adapter/web/BetxInterfaceControllerTest.java` as a direct unit test with Mockito mocks:

```java
package com.betx.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BetxInterfaceActivityItem;
import com.betx.application.BetxInterfaceActivityService;
import com.betx.application.BetxInterfaceProperties;
import com.betx.application.BetxInterfaceRuntimeService;
import com.betx.application.BetxInterfaceStatusService;
import com.betx.application.BetxInterfaceStatusView;
import com.betx.application.InterfaceStatus;
import com.betx.domain.config.ConfigPath;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BetxInterfaceControllerTest {
    @Test
    void exposesStatusAndActivity() {
        BetxInterfaceStatusService statusService = Mockito.mock(BetxInterfaceStatusService.class);
        BetxInterfaceRuntimeService runtimeService = Mockito.mock(BetxInterfaceRuntimeService.class);
        BetxInterfaceActivityService activityService = Mockito.mock(BetxInterfaceActivityService.class);
        BetxInterfaceProperties properties = new BetxInterfaceProperties(Path.of("betx.yml"));
        Mockito.when(statusService.status()).thenReturn(new BetxInterfaceStatusView(
            InterfaceStatus.PAUSED,
            "BetX esta pausado.",
            null,
            Instant.parse("2026-06-18T10:00:00Z"),
            false
        ));
        Mockito.when(activityService.recent()).thenReturn(List.of(new BetxInterfaceActivityItem(
            "intent-1",
            "Real Madrid vs Barcelona",
            "Empate",
            BigDecimal.valueOf(3.2),
            BigDecimal.valueOf(5),
            "Realizada",
            null,
            Instant.parse("2026-06-18T09:01:00Z")
        )));
        BetxInterfaceController controller = new BetxInterfaceController(
            statusService,
            runtimeService,
            activityService,
            properties
        );

        assertThat(controller.status().status()).isEqualTo(InterfaceStatus.PAUSED);
        assertThat(controller.activity()).singleElement()
            .satisfies(item -> assertThat(item.event()).isEqualTo("Real Madrid vs Barcelona"));
    }

    @Test
    void activatesAndPausesThroughRuntimeService() {
        BetxInterfaceStatusService statusService = Mockito.mock(BetxInterfaceStatusService.class);
        BetxInterfaceRuntimeService runtimeService = Mockito.mock(BetxInterfaceRuntimeService.class);
        BetxInterfaceActivityService activityService = Mockito.mock(BetxInterfaceActivityService.class);
        BetxInterfaceProperties properties = new BetxInterfaceProperties(Path.of("betx.yml"));
        Mockito.when(statusService.status()).thenReturn(new BetxInterfaceStatusView(
            InterfaceStatus.ACTIVE,
            "BetX esta activo.",
            null,
            Instant.parse("2026-06-18T10:01:00Z"),
            false
        ));
        BetxInterfaceController controller = new BetxInterfaceController(
            statusService,
            runtimeService,
            activityService,
            properties
        );

        controller.activate();
        controller.pause();

        Mockito.verify(runtimeService).activate(new ConfigPath(Path.of("betx.yml")));
        Mockito.verify(runtimeService).pause();
    }
}
```

- [ ] **Step 2: Run failing test**

Run: `mvn -q test -Dtest=BetxInterfaceControllerTest`

Expected: compilation fails because `BetxInterfaceController` does not exist.

- [ ] **Step 3: Implement controller**

Create `src/main/java/com/betx/adapter/web/BetxInterfaceController.java`:

```java
package com.betx.adapter.web;

import com.betx.application.BetxInterfaceActivityItem;
import com.betx.application.BetxInterfaceActivityService;
import com.betx.application.BetxInterfaceProperties;
import com.betx.application.BetxInterfaceRuntimeService;
import com.betx.application.BetxInterfaceStatusService;
import com.betx.application.BetxInterfaceStatusView;
import com.betx.domain.config.ConfigPath;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interface")
@ConditionalOnProperty(name = "betx.interface.enabled", havingValue = "true")
public class BetxInterfaceController {
    private final BetxInterfaceStatusService statusService;
    private final BetxInterfaceRuntimeService runtimeService;
    private final BetxInterfaceActivityService activityService;
    private final BetxInterfaceProperties properties;

    public BetxInterfaceController(
        BetxInterfaceStatusService statusService,
        BetxInterfaceRuntimeService runtimeService,
        BetxInterfaceActivityService activityService,
        BetxInterfaceProperties properties
    ) {
        this.statusService = statusService;
        this.runtimeService = runtimeService;
        this.activityService = activityService;
        this.properties = properties;
    }

    @GetMapping("/status")
    public BetxInterfaceStatusView status() {
        return statusService.status();
    }

    @PostMapping("/activate")
    public BetxInterfaceStatusView activate() {
        runtimeService.activate(new ConfigPath(properties.configPath()));
        return statusService.status();
    }

    @PostMapping("/pause")
    public BetxInterfaceStatusView pause() {
        runtimeService.pause();
        return statusService.status();
    }

    @GetMapping("/activity")
    public List<BetxInterfaceActivityItem> activity() {
        return activityService.recent();
    }
}
```

Create `src/main/java/com/betx/adapter/web/BetxInterfacePageController.java`:

```java
package com.betx.adapter.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(name = "betx.interface.enabled", havingValue = "true")
public class BetxInterfacePageController {
    @GetMapping("/")
    public String home() {
        return "redirect:/interface/";
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=BetxInterfaceControllerTest`

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/betx/adapter/web/BetxInterfaceController.java src/main/java/com/betx/adapter/web/BetxInterfacePageController.java src/test/java/com/betx/adapter/web/BetxInterfaceControllerTest.java
git commit -m "Expose interface endpoints"
```

## Task 5: Minimal Home Page

**Files:**
- Create: `src/main/resources/static/interface/index.html`
- Create: `src/main/resources/static/interface/styles.css`
- Create: `src/main/resources/static/interface/app.js`
- Test: `src/test/java/com/betx/adapter/web/BetxInterfaceStaticPageTest.java`

- [ ] **Step 1: Write static asset test**

Create `src/test/java/com/betx/adapter/web/BetxInterfaceStaticPageTest.java`:

```java
package com.betx.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BetxInterfaceStaticPageTest {
    @Test
    void pageUsesOnlyCommercialInterfaceLanguage() throws IOException {
        String html = resource("/static/interface/index.html");
        String js = resource("/static/interface/app.js");

        assertThat(html)
            .contains("BetX")
            .contains("Activar BetX")
            .contains("Pausar BetX")
            .doesNotContain("paper")
            .doesNotContain("backtest")
            .doesNotContain("runner")
            .doesNotContain("snapshot")
            .doesNotContain("gateway");
        assertThat(js)
            .contains("/api/interface/status")
            .contains("/api/interface/activity")
            .doesNotContain("paper")
            .doesNotContain("backtest");
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 2: Run failing test**

Run: `mvn -q test -Dtest=BetxInterfaceStaticPageTest`

Expected: FAIL because static assets do not exist.

- [ ] **Step 3: Add HTML**

Create `src/main/resources/static/interface/index.html`:

```html
<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>BetX</title>
  <link rel="stylesheet" href="/interface/styles.css">
</head>
<body>
  <main class="shell">
    <header class="topbar">
      <div>
        <p class="eyebrow">BetX</p>
        <h1>Inicio</h1>
      </div>
      <div id="statusBadge" class="status status-paused">Pausado</div>
    </header>

    <section class="summary">
      <div>
        <p class="label">Estado</p>
        <p id="statusMessage" class="message">Consultando estado...</p>
      </div>
      <div>
        <p class="label">Balance disponible</p>
        <p id="availableBalance" class="metric">-</p>
      </div>
      <div>
        <p class="label">Confirmacion manual</p>
        <p id="manualConfirmation" class="metric">-</p>
      </div>
    </section>

    <section class="actions" aria-label="Controles de BetX">
      <button id="activateButton" type="button">Activar BetX</button>
      <button id="pauseButton" type="button" class="secondary">Pausar BetX</button>
    </section>

    <section class="activity">
      <div class="section-title">
        <h2>Actividad reciente</h2>
        <span id="activityCount">0 operaciones</span>
      </div>
      <div id="activityList" class="activity-list">
        <p class="empty">No hay actividad reciente.</p>
      </div>
    </section>
  </main>
  <script src="/interface/app.js"></script>
</body>
</html>
```

- [ ] **Step 4: Add CSS**

Create `src/main/resources/static/interface/styles.css` with restrained dashboard styling:

```css
:root {
  color-scheme: light;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  background: #f5f7f8;
  color: #17201c;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
}

.shell {
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
  padding: 28px 0 40px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 24px;
}

.eyebrow,
.label {
  margin: 0 0 6px;
  color: #60706a;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0;
}

h1,
h2,
p {
  margin-top: 0;
}

h1 {
  margin-bottom: 0;
  font-size: 2rem;
}

.status {
  min-width: 132px;
  border-radius: 6px;
  padding: 10px 14px;
  text-align: center;
  font-weight: 700;
}

.status-active {
  background: #dff5ea;
  color: #0f6b42;
}

.status-paused {
  background: #eef1f3;
  color: #43505a;
}

.status-attention {
  background: #fff0d8;
  color: #8a4d00;
}

.summary {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.summary > div,
.activity {
  background: #ffffff;
  border: 1px solid #dfe5e2;
  border-radius: 8px;
  padding: 18px;
}

.message,
.metric {
  margin-bottom: 0;
  font-size: 1.05rem;
  font-weight: 650;
}

.actions {
  display: flex;
  gap: 10px;
  margin-bottom: 24px;
}

button {
  border: 0;
  border-radius: 6px;
  padding: 11px 16px;
  background: #146c43;
  color: #ffffff;
  font-weight: 700;
  cursor: pointer;
}

button.secondary {
  background: #34423b;
}

button:disabled {
  opacity: 0.55;
  cursor: wait;
}

.section-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
  margin-bottom: 14px;
}

.section-title h2 {
  margin-bottom: 0;
  font-size: 1.2rem;
}

.activity-list {
  display: grid;
  gap: 10px;
}

.activity-row {
  display: grid;
  grid-template-columns: 1.5fr 1fr 0.5fr 0.5fr 0.8fr;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid #e5ebe8;
  border-radius: 6px;
}

.activity-row strong,
.activity-row span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.empty {
  margin-bottom: 0;
  color: #60706a;
}

@media (max-width: 760px) {
  .topbar,
  .actions,
  .section-title {
    align-items: stretch;
    flex-direction: column;
  }

  .summary,
  .activity-row {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 5: Add JavaScript**

Create `src/main/resources/static/interface/app.js`:

```javascript
const statusBadge = document.querySelector("#statusBadge");
const statusMessage = document.querySelector("#statusMessage");
const availableBalance = document.querySelector("#availableBalance");
const manualConfirmation = document.querySelector("#manualConfirmation");
const activateButton = document.querySelector("#activateButton");
const pauseButton = document.querySelector("#pauseButton");
const activityList = document.querySelector("#activityList");
const activityCount = document.querySelector("#activityCount");

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error("request failed");
  }
  return response.json();
}

function statusLabel(status) {
  if (status === "ACTIVE") return "Activo";
  if (status === "NEEDS_ATTENTION") return "Necesita atencion";
  return "Pausado";
}

function statusClass(status) {
  if (status === "ACTIVE") return "status status-active";
  if (status === "NEEDS_ATTENTION") return "status status-attention";
  return "status status-paused";
}

function money(value) {
  if (value === null || value === undefined) return "-";
  return new Intl.NumberFormat("es-ES", { style: "currency", currency: "EUR" }).format(Number(value));
}

function decimal(value) {
  if (value === null || value === undefined) return "-";
  return new Intl.NumberFormat("es-ES", { maximumFractionDigits: 2 }).format(Number(value));
}

function renderStatus(data) {
  statusBadge.textContent = statusLabel(data.status);
  statusBadge.className = statusClass(data.status);
  statusMessage.textContent = data.message || "Estado no disponible.";
  availableBalance.textContent = money(data.availableBalance);
  manualConfirmation.textContent = data.manualConfirmationEnabled ? "Activada" : "Desactivada";
}

function renderActivity(items) {
  activityCount.textContent = `${items.length} ${items.length === 1 ? "operacion" : "operaciones"}`;
  if (items.length === 0) {
    activityList.innerHTML = '<p class="empty">No hay actividad reciente.</p>';
    return;
  }
  activityList.innerHTML = items.map(item => `
    <div class="activity-row">
      <strong>${escapeHtml(item.event || "-")}</strong>
      <span>${escapeHtml(item.selection || "-")}</span>
      <span>${decimal(item.odds)}</span>
      <span>${money(item.amount)}</span>
      <span>${escapeHtml(item.statusLabel || "-")}</span>
    </div>
  `).join("");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function refresh() {
  try {
    const [status, activity] = await Promise.all([
      fetchJson("/api/interface/status"),
      fetchJson("/api/interface/activity")
    ]);
    renderStatus(status);
    renderActivity(activity);
  } catch (error) {
    renderStatus({ status: "NEEDS_ATTENTION", message: "No se pudo actualizar BetX.", availableBalance: null, manualConfirmationEnabled: false });
  }
}

async function postAction(url) {
  activateButton.disabled = true;
  pauseButton.disabled = true;
  try {
    const status = await fetchJson(url, { method: "POST" });
    renderStatus(status);
    await refresh();
  } finally {
    activateButton.disabled = false;
    pauseButton.disabled = false;
  }
}

activateButton.addEventListener("click", () => postAction("/api/interface/activate"));
pauseButton.addEventListener("click", () => postAction("/api/interface/pause"));

refresh();
setInterval(refresh, 5000);
```

- [ ] **Step 6: Run tests**

Run: `mvn -q test -Dtest=BetxInterfaceStaticPageTest`

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add src/main/resources/static/interface/index.html src/main/resources/static/interface/styles.css src/main/resources/static/interface/app.js src/test/java/com/betx/adapter/web/BetxInterfaceStaticPageTest.java
git commit -m "Add minimal BetX interface page"
```

## Task 6: Integration Verification And Documentation

**Files:**
- Modify: `README.md`
- Test: full test suite

- [ ] **Step 1: Add README section**

Add a short section near Common Commands:

```markdown
## User Interface Preview

BetX includes a minimal local web interface for the end-user vertical slice:

```bash
java -jar target/betx.jar interface --config betx.yml
```

The interface shows BetX status, activate/pause controls, and recent activity. Internal tools such as paper trading, backtesting, CLV diagnostics, walk-forward validation, ML research, and technical logs remain CLI-only and are not exposed in the commercial interface.
```
```

- [ ] **Step 2: Run targeted checks**

Run:

```bash
mvn -q test -Dtest=InterfaceCommandTest,BetxInterfaceRuntimeServiceTest,BetxInterfaceStatusServiceTest,BetxInterfaceActivityServiceTest,BetxInterfaceControllerTest,BetxInterfaceStaticPageTest
```

Expected: PASS.

- [ ] **Step 3: Run full suite**

Run: `mvn test`

Expected: PASS.

- [ ] **Step 4: Build package**

Run: `mvn package`

Expected: PASS and `target/betx.jar` exists.

- [ ] **Step 5: Manual smoke test without auto-opening browser**

Run:

```bash
java -jar target/betx.jar interface --config betx.yml --port 8080 --no-browser
```

Expected terminal output includes:

```text
BetX interface available at http://localhost:8080/interface/
```

Open `http://localhost:8080/interface/` manually. The page should show BetX, status, activate/pause buttons, and recent activity without mentioning paper trading, backtesting, ML, CLV, runners, gateways, snapshots, or logs.

- [ ] **Step 6: Commit**

Run:

```bash
git add README.md
git commit -m "Document BetX interface preview"
```

## Self-Review

Spec coverage:

- Start BetX in interface mode: Task 1 and Task 6.
- Open browser automatically: Task 1.
- Show home page: Task 5.
- Real status endpoint: Tasks 3 and 4.
- Active, paused, needs attention: Task 2 and Task 3.
- Activate and pause controls: Tasks 2, 4, and 5.
- Activity recent: Tasks 3, 4, and 5.
- Keep CLI and internal tools separate: Tasks 1, 5, and 6.
- Avoid duplicated domain logic: Tasks 2 and 3 route through existing services and repositories.

Placeholder scan:

- No `TBD`, `TODO`, `implement later`, or intentionally vague future API steps remain.

Type consistency:

- `InterfaceStatus`, `BetxInterfaceStatusView`, `BetxInterfaceActivityItem`, `BetxInterfaceProperties`, and service names are consistent across tasks.
