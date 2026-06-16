package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestPaperTradeCsvExporterTest {
    @Test
    void exportsPaperTradeSideAfterRunner() {
        BacktestPaperTrade trade = new BacktestPaperTrade(
            "1.234",
            "1.234",
            "SP1",
            "prospective",
            "Team A v Team B",
            "Draw",
            BetSide.BACK,
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
        BacktestComparisonReport report = new BacktestComparisonReport(
            42L,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BacktestSlippageModel.PROFIT_HAIRCUT,
            "exchange",
            "unknown",
            BacktestDatasetCapability.EXCHANGE_SNAPSHOTS,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new BacktestLeakageDiagnostics(0, 0),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(trade),
            BacktestClvSummary.unavailable(List.of(trade)),
            List.of(),
            List.of()
        );

        List<String> lines = new BacktestPaperTradeCsvExporter().lines(report);

        assertThat(lines.getFirst()).startsWith("event_id,market_id,league,season,event,runner,side,");
        assertThat(lines.get(1)).startsWith("1.234,1.234,SP1,prospective,Team A v Team B,Draw,BACK,");
    }
}
