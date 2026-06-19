package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.DiagnosticFinding;
import com.betx.application.DiagnosticsCoverage;
import com.betx.application.DiagnosticsCsvExporter;
import com.betx.application.DiagnosticsDecisionFunnel;
import com.betx.application.DiagnosticsExecutionMetrics;
import com.betx.application.DiagnosticsFormatter;
import com.betx.application.DiagnosticsJsonExporter;
import com.betx.application.DiagnosticsModel.DiagnosticFindingSeverity;
import com.betx.application.DiagnosticsModel.DiagnosticsDataProvenance;
import com.betx.application.DiagnosticsModel.DiagnosticsRequest;
import com.betx.application.DiagnosticsPaperVsRealMetrics;
import com.betx.application.DiagnosticsPeriod;
import com.betx.application.DiagnosticsReport;
import com.betx.application.GenerateDiagnosticsUseCase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticsCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void printsDiagnosticsAndExportsFiles() {
        RecordingUseCase useCase = new RecordingUseCase(report());
        DiagnosticsCommand command = new DiagnosticsCommand(
            useCase,
            new DiagnosticsFormatter(),
            new DiagnosticsJsonExporter(),
            new DiagnosticsCsvExporter()
        );
        command.configPath = Path.of("custom.yml");
        command.from = "2026-06-01T00:00:00Z";
        command.to = "2026-06-02T00:00:00Z";
        command.logsDir = Path.of("custom-logs");
        command.matchWindow = "PT2H";
        command.exportJsonPath = tempDir.resolve("diagnostics.json");
        command.exportCsvPath = tempDir.resolve("diagnostics.csv");

        String output = captureOutput(command::run);

        assertThat(output).contains("BETX DIAGNOSTICS").contains("JSON export written").contains("CSV export written");
        assertThat(command.exportJsonPath).exists();
        assertThat(command.exportCsvPath).exists();
        assertThat(useCase.request.configPath().value()).isEqualTo(Path.of("custom.yml"));
        assertThat(useCase.request.from()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(useCase.request.matchWindow().toHours()).isEqualTo(2);
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

    private static DiagnosticsReport report() {
        return new DiagnosticsReport(
            Instant.parse("2026-06-02T00:00:00Z"),
            new DiagnosticsPeriod(Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-02T00:00:00Z")),
            new DiagnosticsCoverage(1, 1, 0, 1, 1, 0),
            new DiagnosticsDecisionFunnel(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, Map.of()),
            new DiagnosticsExecutionMetrics(0, 0, 0, 0, 0, 0, null, null, null, DiagnosticsDataProvenance.UNAVAILABLE, null, DiagnosticsDataProvenance.UNAVAILABLE, 0, 0),
            new DiagnosticsPaperVsRealMetrics(0, 0, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, null, null, 0, DiagnosticsDataProvenance.UNAVAILABLE, DiagnosticsDataProvenance.UNAVAILABLE),
            List.of(new DiagnosticFinding(DiagnosticFindingSeverity.WARNING, "TEST", "Example warning.", 1)),
            List.of("limitation"),
            List.of("finding"),
            List.of()
        );
    }

    private static final class RecordingUseCase implements GenerateDiagnosticsUseCase {
        private final DiagnosticsReport report;
        private DiagnosticsRequest request;

        private RecordingUseCase(DiagnosticsReport report) {
            this.report = report;
        }

        @Override
        public DiagnosticsReport generate(DiagnosticsRequest request) {
            this.request = request;
            return report;
        }
    }
}
