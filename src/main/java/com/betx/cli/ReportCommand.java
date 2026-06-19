package com.betx.cli;

import com.betx.application.GenerateRealBettingReportUseCase;
import com.betx.application.RealBettingReport;
import com.betx.application.RealBettingReportCsvExporter;
import com.betx.application.RealBettingReportFormatter;
import com.betx.application.RealBettingReportJsonExporter;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "report", description = "Print a read-only real betting performance report.")
public class ReportCommand implements Runnable {
    private final GenerateRealBettingReportUseCase useCase;
    private final RealBettingReportFormatter formatter;
    private final RealBettingReportJsonExporter jsonExporter;
    private final RealBettingReportCsvExporter csvExporter;

    @Option(names = {"--config", "-c"}, defaultValue = "betx.yml", description = "Path to betx.yml.")
    Path configPath;

    @Option(names = "--export-json", description = "Optional path to write the report as JSON.")
    Path exportJsonPath;

    @Option(names = "--export-csv", description = "Optional path to write one row per bet as CSV.")
    Path exportCsvPath;

    @Autowired
    public ReportCommand(GenerateRealBettingReportUseCase useCase) {
        this(useCase, new RealBettingReportFormatter(), new RealBettingReportJsonExporter(), new RealBettingReportCsvExporter());
    }

    ReportCommand(GenerateRealBettingReportUseCase useCase, RealBettingReportFormatter formatter) {
        this(useCase, formatter, new RealBettingReportJsonExporter(), new RealBettingReportCsvExporter());
    }

    ReportCommand(
        GenerateRealBettingReportUseCase useCase,
        RealBettingReportFormatter formatter,
        RealBettingReportJsonExporter jsonExporter,
        RealBettingReportCsvExporter csvExporter
    ) {
        this.useCase = useCase;
        this.formatter = formatter;
        this.jsonExporter = jsonExporter;
        this.csvExporter = csvExporter;
    }

    @Override
    public void run() {
        RealBettingReport report = useCase.generate(new ConfigPath(configPath));
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
}
