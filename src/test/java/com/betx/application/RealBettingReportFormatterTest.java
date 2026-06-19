package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealBettingReportFormatterTest {
    @Test
    void formatsReadableTerminalReportWithWarningsAndUnavailableDimensions() {
        RealBettingReport report = new RealBettingReport(
            Instant.parse("2026-06-01T10:00:00Z"),
            Instant.parse("2026-06-03T10:00:00Z"),
            "2026-06-01T10:00:00Z to 2026-06-03T10:00:00Z",
            2,
            1,
            1,
            1,
            0,
            new BigDecimal("10.00"),
            new BigDecimal("4.00"),
            new BigDecimal("2.50"),
            new BigDecimal("25.00"),
            new BigDecimal("50.00"),
            new BigDecimal("2.85"),
            new BigDecimal("101.00"),
            new BigDecimal("110.00"),
            new BigDecimal("102.50"),
            new BigDecimal("105.00"),
            new BigDecimal("7.00"),
            new BigDecimal("2.50"),
            new BigDecimal("100.00"),
            true,
            1,
            1,
            List.of(segment("HOME", "5.00")),
            List.of(segment("Team A", "5.00")),
            List.of(segment("N/A", "2.50")),
            List.of(segment("N/A", "2.50")),
            List.of(segment("2.00-2.99", "2.50")),
            List.of(new RealBettingReportDailyPnl(LocalDate.parse("2026-06-01"), 2, new BigDecimal("2.50"))),
            List.of(new RealBettingReportRollingWindow(
                25,
                2,
                1,
                1,
                new BigDecimal("50.00"),
                new BigDecimal("10.00"),
                new BigDecimal("2.50"),
                new BigDecimal("25.00"),
                new BigDecimal("7.00"),
                1,
                1
            )),
            List.of(),
            List.of(new RealBettingReportWarning("Small sample: results are not statistically reliable yet.")),
            List.of("Strategy is shown as N/A for historical bets created before strategy persistence.")
        );

        String output = String.join("\n", new RealBettingReportFormatter().format(report));

        assertThat(output)
            .contains("REAL BETTING REPORT")
            .contains("Period: 2026-06-01T10:00:00Z to 2026-06-03T10:00:00Z")
            .contains("Settled bets")
            .contains("+2.50 €")
            .contains("-")
            .contains("25.00 %")
            .contains("Operational available balance")
            .contains("Exchange available balance")
            .contains("Realized equity")
            .contains("Total staked / turnover:")
            .contains("10.00 €")
            .contains("Open exposure:")
            .contains("4.00 €")
            .contains("Maximum drawdown:")
            .contains("7.00 €")
            .contains("Current drawdown from peak:")
            .contains("2.50 €")
            .contains("Small sample: results are not statistically reliable yet.")
            .contains("By selection side")
            .contains("By selection / runner")
            .contains("By competition")
            .contains("By strategy")
            .contains("Rolling windows")
            .contains("Last 25 settled bets")
            .contains("Turnover          PnL        ROI")
            .contains("+2.50 €")
            .contains("N/A")
            .contains("Strategy is shown as N/A for historical bets created before strategy persistence.");
    }

    @Test
    void formatsRollingWindowNegativePnlWithSign() {
        RealBettingReport report = RealBettingReport.empty().withRows(List.of());
        report = new RealBettingReport(
            report.periodStart(),
            report.periodEnd(),
            report.periodLabel(),
            2,
            0,
            0,
            2,
            0,
            new BigDecimal("20.00"),
            BigDecimal.ZERO,
            new BigDecimal("-8.00"),
            new BigDecimal("-40.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            null,
            new BigDecimal("-8.00"),
            BigDecimal.ZERO,
            new BigDecimal("8.00"),
            new BigDecimal("8.00"),
            null,
            false,
            0,
            2,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new RealBettingReportRollingWindow(
                25,
                2,
                0,
                2,
                BigDecimal.ZERO,
                new BigDecimal("20.00"),
                new BigDecimal("-8.00"),
                new BigDecimal("-40.00"),
                new BigDecimal("8.00"),
                0,
                2
            )),
            List.of(),
            List.of(),
            List.of()
        );

        String output = String.join("\n", new RealBettingReportFormatter().format(report));

        assertThat(output)
            .contains("Turnover          PnL        ROI")
            .contains("-8.00 €")
            .contains("20.00 €")
            .contains("8.00 €");
    }

    @Test
    void usesCumulativePnlLabelsWhenNoInitialBalanceExists() {
        RealBettingReport report = new RealBettingReport(
            null,
            null,
            "N/A",
            1,
            0,
            1,
            0,
            0,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            new BigDecimal("2.50"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            null,
            null,
            new BigDecimal("2.50"),
            new BigDecimal("2.50"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            false,
            1,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("No reliable initial balance was found; the performance curve starts from cumulative realized PnL at 0.00 €.")
        );

        String output = String.join("\n", new RealBettingReportFormatter().format(report));

        assertThat(output)
            .contains("Cumulative realized PnL")
            .contains("Peak cumulative realized PnL")
            .doesNotContain("Realized equity")
            .doesNotContain("Peak realized equity");
    }

    private static RealBettingReportSegment segment(String name, String pnl) {
        return new RealBettingReportSegment(
            name,
            2,
            1,
            1,
            0,
            new BigDecimal("10.00"),
            new BigDecimal(pnl),
            new BigDecimal("25.00"),
            new BigDecimal("50.00")
        );
    }
}
