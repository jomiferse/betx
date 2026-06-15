package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.betx.application.BacktestOutcome;
import com.betx.application.BacktestPaperTrade;
import com.betx.application.BacktestSlippageModel;
import com.betx.application.PaperTradeHistoryDiagnostics;
import com.betx.application.PaperTradeRunnerClassificationDiagnostic;
import com.betx.application.PaperTradingResult;
import com.betx.application.RunPaperTradingService;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.signal.RunnerType;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

class PaperTradeCommandTest {
    @Test
    void printsPaperModeDiagnosticsAndProspectiveClvMetrics() {
        RunPaperTradingService service = org.mockito.Mockito.mock(RunPaperTradingService.class);
        when(service.run(any(ConfigPath.class), any(BigDecimal.class), any(BacktestSlippageModel.class), any(BigDecimal.class)))
            .thenReturn(new PaperTradingResult(
                List.of(settledTrade()),
                List.of(),
                3,
                3,
                1,
                1,
                2,
                0,
                0,
                0,
                1
            ));
        PaperTradeCommand command = new PaperTradeCommand(service);
        command.configPath = Path.of("betx.yml");

        String output = captureOutput(command::run);

        assertThat(output)
            .contains("PAPER MODE | strategy=value-football-draw-only | realOrders=false | telegram=false | intelligence=false")
            .contains("marketsScanned=1")
            .contains("recommendationsGenerated=1")
            .contains("duplicatesSkipped=2")
            .contains("settledTrades=1")
            .contains("commissionRate=0.05")
            .contains("CLV | strategy=value-football-draw-only | status=VALID_PROSPECTIVE | validClvTrades=1")
            .contains("PAPER_VALIDATION | strategy=value-football-draw-only | status=INSUFFICIENT_SAMPLE | clvStatus=VALID_PROSPECTIVE")
            .contains("PAPER_LEAGUE | league=SP1 | settledTrades=1 | executableRoi=270.00% | medianClv=0.05714286");
    }

    @Test
    void printsInsufficientClvDataWhenNoSettledTradeHasIndependentClosingPrice() {
        RunPaperTradingService service = org.mockito.Mockito.mock(RunPaperTradingService.class);
        when(service.run(any(ConfigPath.class), any(BigDecimal.class), any(BacktestSlippageModel.class), any(BigDecimal.class)))
            .thenReturn(new PaperTradingResult(
                List.of(),
                List.of(),
                267,
                267,
                89,
                0,
                0,
                0,
                0,
                0,
                0
            ));
        PaperTradeCommand command = new PaperTradeCommand(service);
        command.configPath = Path.of("betx.yml");

        String output = captureOutput(command::run);

        assertThat(output)
            .contains("PAPER_HISTORY | previousSnapshotsLoaded=0")
            .contains("runnersWithoutPreviousSnapshot=0")
            .contains("PAPER_ANALYZER | reason=INSUFFICIENT_HISTORY | count=0")
            .contains("PAPER_ANALYZER | reason=ACCEPTED | count=0")
            .contains("CLV | strategy=value-football-draw-only | status=INSUFFICIENT_DATA | validClvTrades=0")
            .contains("averageClv=n/a")
            .contains("medianClv=n/a")
            .contains("positiveClv=n/a")
            .contains("PAPER_VALIDATION | strategy=value-football-draw-only | status=INSUFFICIENT_DATA | clvStatus=INSUFFICIENT_DATA");
    }

    @Test
    void printsDrawClassificationDiagnosticsAndWarnings() {
        RunPaperTradingService service = org.mockito.Mockito.mock(RunPaperTradingService.class);
        PaperTradeHistoryDiagnostics diagnostics = new PaperTradeHistoryDiagnostics(
            3,
            0,
            3,
            3,
            1,
            2,
            Instant.parse("2026-06-15T09:59:00Z"),
            Instant.parse("2026-06-15T09:59:00Z"),
            1,
            3,
            List.of(new PaperTradeRunnerClassificationDiagnostic(
                "1.234",
                "Match Odds",
                170940L,
                "The Draw",
                "the draw",
                RunnerType.DRAW,
                true
            )),
            List.of("PAPER_WARNING | complete Match Odds market has zero DRAW runners | marketId=1.999 | runnerNames=A, B, C"),
            Map.of()
        );
        when(service.run(any(ConfigPath.class), any(BigDecimal.class), any(BacktestSlippageModel.class), any(BigDecimal.class)))
            .thenReturn(new PaperTradingResult(
                List.of(),
                List.of(),
                3,
                3,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                diagnostics
            ));
        PaperTradeCommand command = new PaperTradeCommand(service);
        command.configPath = Path.of("betx.yml");

        String output = captureOutput(command::run);

        assertThat(output)
            .contains("PAPER_DRAW_CLASSIFICATION | marketId=1.234 | marketName=Match Odds | selectionId=170940"
                + " | runnerName=The Draw | normalizedRunnerName=the draw | inferredRunnerType=DRAW | isDraw=true")
            .contains("PAPER_WARNING | complete Match Odds market has zero DRAW runners | marketId=1.999 | runnerNames=A, B, C");
    }

    @Test
    void rejectsInvalidCommissionRate() {
        PaperTradeCommand command = new PaperTradeCommand(org.mockito.Mockito.mock(RunPaperTradingService.class));
        command.configPath = Path.of("betx.yml");
        command.commissionRate = new BigDecimal("1.01");

        assertThatThrownBy(command::run)
            .isInstanceOf(picocli.CommandLine.ParameterException.class)
            .hasMessageContaining("--commission-rate must be between 0.0 and 1.0");
    }

    @Test
    void continuousModeUsesConfiguredPollIntervalAndPrintsCycleDiagnostics() {
        RunPaperTradingService service = org.mockito.Mockito.mock(RunPaperTradingService.class);
        when(service.paperConfig(any(ConfigPath.class))).thenReturn(com.betx.domain.config.PaperConfig.defaults());
        PaperTradingResult result = new PaperTradingResult(
            List.of(settledTrade()),
            List.of(),
            3,
            3,
            1,
            1,
            0,
            0,
            0,
            0,
            1
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            BiConsumer<Integer, PaperTradingResult> reporter = invocation.getArgument(6);
            reporter.accept(1, result);
            return List.of(result);
        }).when(service).runContinuous(
            any(ConfigPath.class),
            any(BigDecimal.class),
            any(BacktestSlippageModel.class),
            any(BigDecimal.class),
            any(Duration.class),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any()
        );
        PaperTradeCommand command = new PaperTradeCommand(service);
        command.configPath = Path.of("betx.yml");
        command.continuous = true;
        command.pollInterval = "60s";

        String output = captureOutput(command::run);

        org.mockito.Mockito.verify(service).runContinuous(
            any(ConfigPath.class),
            any(BigDecimal.class),
            any(BacktestSlippageModel.class),
            any(BigDecimal.class),
            org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(60)),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any()
        );
        assertThat(output)
            .contains("Paper trading continuous mode started | pollInterval=60s")
            .contains("Paper trading cycle complete | cycle=1")
            .contains("settledTrades=1");
    }

    @Test
    void yamlPaperContinuousModeRunsContinuouslyWhenFlagIsNotProvided() {
        RunPaperTradingService service = org.mockito.Mockito.mock(RunPaperTradingService.class);
        when(service.paperConfig(any(ConfigPath.class))).thenReturn(new com.betx.domain.config.PaperConfig(true, "5m", 2, "5m"));
        PaperTradingResult result = new PaperTradingResult(
            List.of(),
            List.of(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            BiConsumer<Integer, PaperTradingResult> reporter = invocation.getArgument(6);
            reporter.accept(1, result);
            return List.of(result);
        }).when(service).runContinuous(
            any(ConfigPath.class),
            any(BigDecimal.class),
            any(BacktestSlippageModel.class),
            any(BigDecimal.class),
            any(Duration.class),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any()
        );
        PaperTradeCommand command = new PaperTradeCommand(service);
        command.configPath = Path.of("betx.yml");

        String output = captureOutput(command::run);

        org.mockito.Mockito.verify(service).runContinuous(
            any(ConfigPath.class),
            any(BigDecimal.class),
            any(BacktestSlippageModel.class),
            any(BigDecimal.class),
            org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any()
        );
        assertThat(output).contains("Paper trading continuous mode started | pollInterval=5m");
    }

    private static BacktestPaperTrade settledTrade() {
        return new BacktestPaperTrade(
            "1.234",
            "1.234",
            "SP1",
            "prospective",
            "Team A v Team B",
            "Draw",
            Instant.parse("2026-06-15T10:00:00Z"),
            Instant.parse("2026-06-15T10:00:01Z"),
            Instant.parse("2026-06-15T17:50:00Z"),
            new BigDecimal("3.70"),
            new BigDecimal("3.70"),
            new BigDecimal("3.70"),
            new BigDecimal("3.50"),
            BacktestOutcome.WIN,
            new BigDecimal("13.50"),
            BigDecimal.ZERO,
            new BigDecimal("13.50"),
            new BigDecimal("0.05714286"),
            new BigDecimal("-0.01544402"),
            "settled"
        );
    }

    private static String captureOutput(Runnable runnable) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runnable.run();
            return output.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(originalOut);
        }
    }
}
