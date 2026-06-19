package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RealBettingReportJsonExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesStructuredJsonWithNumericRatiosAndBreakdowns() throws Exception {
        Path exportPath = tempDir.resolve("reports/report.json");
        RealBettingReport report = new RealBettingReport(
            Instant.parse("2026-06-01T10:00:00Z"),
            Instant.parse("2026-06-01T12:00:00Z"),
            "2026-06-01T10:00:00Z to 2026-06-01T12:00:00Z",
            1,
            0,
            1,
            0,
            0,
            new BigDecimal("5.00"),
            BigDecimal.ZERO,
            new BigDecimal("7.50"),
            new BigDecimal("150.00"),
            new BigDecimal("100.00"),
            new BigDecimal("2.50"),
            new BigDecimal("105.00"),
            new BigDecimal("110.00"),
            new BigDecimal("107.50"),
            new BigDecimal("107.50"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            true,
            1,
            0,
            List.of(new RealBettingReportSegment("HOME", 1, 1, 0, 0, new BigDecimal("5.00"), new BigDecimal("7.50"), new BigDecimal("150.00"), new BigDecimal("100.00"))),
            List.of(),
            List.of(),
            List.of(new RealBettingReportSegment("value-football", 1, 1, 0, 0, new BigDecimal("5.00"), new BigDecimal("7.50"), new BigDecimal("150.00"), new BigDecimal("100.00"))),
            List.of(),
            List.of(),
            List.of(new RealBettingReportRollingWindow(25, 1, 1, 0, new BigDecimal("100.00"), new BigDecimal("5.00"), new BigDecimal("7.50"), new BigDecimal("150.00"), BigDecimal.ZERO, 1, 0)),
            List.of(),
            List.of(),
            List.of()
        ).withRows(List.of(new RealBettingReportRow(
            "intent-1", "betfair", "1.1", 42L, "Event", "Match Odds", "Team A", SelectionSide.HOME,
            "La Liga", "value-football", new BigDecimal("2.50"), new BigDecimal("5.00"), null, null, null,
            null, null, null, null, null, null
        )));

        new RealBettingReportJsonExporter(Clock.fixed(Instant.parse("2026-06-02T00:00:00Z"), ZoneOffset.UTC))
            .export(report, exportPath);

        String json = java.nio.file.Files.readString(exportPath);
        assertThat(json)
            .contains("\"generatedAt\" : \"2026-06-02T00:00:00Z\"")
            .contains("\"roi\" : 1.5000")
            .contains("\"netRealizedPnl\" : 7.50")
            .contains("\"selectionSide\" : \"HOME\"")
            .contains("\"bySelectionSide\"")
            .contains("\"byRunner\"")
            .contains("\"byCompetition\"")
            .contains("\"byStrategy\"")
            .contains("\"byOdds\"")
            .contains("\"byDay\"")
            .contains("\"rollingWindows\"")
            .doesNotContain("token")
            .doesNotContain("apiKey")
            .doesNotContain("password")
            .doesNotContain("sessionToken");
    }
}
