package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.betx.application.EvaluatePaperReadinessUseCase;
import com.betx.application.PaperReadinessResult;
import com.betx.application.PaperReadinessStatus;
import com.betx.domain.config.ConfigPath;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaperReadinessCommandTest {
    @Test
    void printsReadinessDiagnostics() {
        EvaluatePaperReadinessUseCase useCase = org.mockito.Mockito.mock(EvaluatePaperReadinessUseCase.class);
        when(useCase.evaluate(any(ConfigPath.class), eq("value-football-draw-only")))
            .thenReturn(new PaperReadinessResult(
                PaperReadinessStatus.NOT_READY,
                "value-football-draw-only",
                84,
                100,
                new BigDecimal("0.62"),
                new BigDecimal("1.00"),
                new BigDecimal("0.008"),
                BigDecimal.ZERO,
                new BigDecimal("-0.31"),
                BigDecimal.ZERO,
                "INSUFFICIENT_SAMPLE",
                List.of("Minimum settled trades not reached.", "Rolling ROI is below the configured threshold.")
            ));
        PaperReadinessCommand command = new PaperReadinessCommand(useCase);
        command.configPath = Path.of("betx.yml");

        String output = captureOutput(command::run);

        assertThat(output)
            .contains("Paper readiness")
            .contains("Strategy: value-football-draw-only")
            .contains("Status: NOT_READY")
            .contains("Settled trades: 84 / 100")
            .contains("Executable ROI: +0.62% / +1.00%")
            .contains("Median CLV: +0.008 / >= 0.00")
            .contains("Rolling ROI: -0.31% / >= 0.00%")
            .contains("Auto-betting: BLOCKED")
            .contains("- Minimum settled trades not reached.")
            .contains("- Rolling ROI is below the configured threshold.");
    }

    private static String captureOutput(Runnable runnable) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runnable.run();
        } finally {
            System.setOut(originalOut);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
