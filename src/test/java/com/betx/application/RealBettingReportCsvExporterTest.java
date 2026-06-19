package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RealBettingReportCsvExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesOneRowPerBetAndCreatesParentDirectories() throws Exception {
        Path exportPath = tempDir.resolve("nested/report.csv");
        RealBettingReport report = RealBettingReport.empty().withRows(List.of(
            new RealBettingReportRow(
                "intent-1",
                "betfair",
                "1.1",
                42L,
                "Team A v Team B",
                "Match Odds",
                "Team A",
                SelectionSide.HOME,
                "La Liga",
                "value-football",
                new BigDecimal("2.50"),
                new BigDecimal("5.00"),
                new BigDecimal("110.00"),
                new BigDecimal("105.00"),
                Instant.parse("2026-06-01T09:59:00Z"),
                BetSettlementResult.WIN,
                new BigDecimal("7.50"),
                BetIntentStage.SETTLED,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T12:00:00Z"),
                Instant.parse("2026-06-01T12:00:00Z")
            ),
            new RealBettingReportRow(
                "intent-2",
                "betfair",
                "1.2",
                43L,
                "Team C v Team D",
                "Match Odds",
                "The Draw",
                SelectionSide.UNKNOWN,
                "N/A",
                "N/A",
                new BigDecimal("3.20"),
                new BigDecimal("4.00"),
                null,
                null,
                null,
                null,
                null,
                BetIntentStage.EXECUTED,
                Instant.parse("2026-06-02T10:00:00Z"),
                null,
                Instant.parse("2026-06-02T10:01:00Z")
            )
        ));

        new RealBettingReportCsvExporter().export(report, exportPath);

        assertThat(exportPath).exists();
        String csv = java.nio.file.Files.readString(exportPath);
        assertThat(csv)
            .contains("bet_intent_id,event_name,market_name,competition_name,strategy_name,runner_name,selection_side,stage,settlement_result,selected_stake,executed_odds,realized_profit_loss,available_balance,effective_available_balance,created_at,executed_at,settled_at")
            .contains("intent-1,Team A v Team B,Match Odds,La Liga,value-football,Team A,HOME,SETTLED,WIN,5.00,2.50,7.50,110.00,105.00,2026-06-01T10:00:00Z,,2026-06-01T12:00:00Z")
            .contains("intent-2,Team C v Team D,Match Odds,N/A,N/A,The Draw,UNKNOWN,EXECUTED,,4.00,3.20,,,,2026-06-02T10:00:00Z,2026-06-02T10:01:00Z,")
            .doesNotContain("token")
            .doesNotContain("apiKey")
            .doesNotContain("password")
            .doesNotContain("sessionToken");
    }
}
