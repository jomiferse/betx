package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.GenerateRealBettingReportUseCase;
import com.betx.application.RealBettingReport;
import com.betx.application.RealBettingReportFormatter;
import com.betx.domain.config.ConfigPath;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class ReportCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void printsRealBettingReportForConfigPath() {
        RecordingUseCase useCase = new RecordingUseCase(RealBettingReport.empty());
        ReportCommand command = new ReportCommand(useCase, new RealBettingReportFormatter());
        command.configPath = Path.of("custom.yml");

        String output = captureOutput(command::run);

        assertThat(useCase.configPath).isEqualTo(new ConfigPath(Path.of("custom.yml")));
        assertThat(output)
            .contains("REAL BETTING REPORT")
            .contains("Settled bets")
            .contains("0");
    }

    @Test
    void exportsJsonAndCsvWhenRequested() {
        RecordingUseCase useCase = new RecordingUseCase(RealBettingReport.empty());
        ReportCommand command = new ReportCommand(
            useCase,
            new RealBettingReportFormatter(),
            new com.betx.application.RealBettingReportJsonExporter(java.time.Clock.fixed(
                java.time.Instant.parse("2026-06-02T00:00:00Z"),
                java.time.ZoneOffset.UTC
            )),
            new com.betx.application.RealBettingReportCsvExporter()
        );
        command.configPath = Path.of("custom.yml");
        command.exportJsonPath = tempDir.resolve("reports/report.json");
        command.exportCsvPath = tempDir.resolve("reports/report.csv");

        captureOutput(command::run);

        assertThat(command.exportJsonPath).exists();
        assertThat(command.exportCsvPath).exists();
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

    private static final class RecordingUseCase implements GenerateRealBettingReportUseCase {
        private final RealBettingReport report;
        private ConfigPath configPath;

        private RecordingUseCase(RealBettingReport report) {
            this.report = report;
        }

        @Override
        public RealBettingReport generate(ConfigPath configPath) {
            this.configPath = configPath;
            return report;
        }
    }
}
