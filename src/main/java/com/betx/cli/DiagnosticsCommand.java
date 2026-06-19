package com.betx.cli;

import com.betx.application.DiagnosticsCsvExporter;
import com.betx.application.DiagnosticsFormatter;
import com.betx.application.DiagnosticsJsonExporter;
import com.betx.application.DiagnosticsModel.DiagnosticsRequest;
import com.betx.application.DiagnosticsReport;
import com.betx.application.GenerateDiagnosticsUseCase;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "diagnostics", description = "Print a read-only technical and operational betting diagnostic.")
public class DiagnosticsCommand implements Runnable {
    private final GenerateDiagnosticsUseCase useCase;
    private final DiagnosticsFormatter formatter;
    private final DiagnosticsJsonExporter jsonExporter;
    private final DiagnosticsCsvExporter csvExporter;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--from", description = "Optional inclusive period start as ISO-8601 instant.")
    String from;

    @Option(names = "--to", description = "Optional inclusive period end as ISO-8601 instant.")
    String to;

    @Option(names = "--logs-dir", defaultValue = "logs/events", description = "Structured JSONL events directory.")
    Path logsDir;

    @Option(names = "--match-window", defaultValue = "PT24H", description = "Maximum paper-real matching distance as ISO-8601 duration.")
    String matchWindow;

    @Option(names = "--export-json", description = "Optional path to write diagnostics as JSON.")
    Path exportJsonPath;

    @Option(names = "--export-csv", description = "Optional path to write one row per paper-real comparison as CSV.")
    Path exportCsvPath;

    @Autowired
    public DiagnosticsCommand(GenerateDiagnosticsUseCase useCase) {
        this(useCase, new DiagnosticsFormatter(), new DiagnosticsJsonExporter(), new DiagnosticsCsvExporter());
    }

    DiagnosticsCommand(
        GenerateDiagnosticsUseCase useCase,
        DiagnosticsFormatter formatter,
        DiagnosticsJsonExporter jsonExporter,
        DiagnosticsCsvExporter csvExporter
    ) {
        this.useCase = useCase;
        this.formatter = formatter;
        this.jsonExporter = jsonExporter;
        this.csvExporter = csvExporter;
    }

    @Override
    public void run() {
        DiagnosticsReport report = useCase.generate(new DiagnosticsRequest(
            new ConfigPath(configPath),
            instant(from),
            instant(to),
            logsDir,
            Duration.parse(matchWindow)
        ));
        formatter.format(report).forEach(System.out::println);
        if (exportJsonPath != null) {
            jsonExporter.export(report, exportJsonPath);
            System.out.println("JSON export written: " + exportJsonPath);
        }
        if (exportCsvPath != null) {
            csvExporter.export(report, exportCsvPath);
            System.out.println("CSV export written: " + exportCsvPath);
        }
    }

    private static Instant instant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
