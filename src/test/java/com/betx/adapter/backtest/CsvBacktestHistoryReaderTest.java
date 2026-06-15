package com.betx.adapter.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betx.application.BacktestOutcome;
import com.betx.application.BacktestValidationException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvBacktestHistoryReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesNormalizedBacktestRows() throws Exception {
        Path input = tempDir.resolve("history.csv");
        Files.writeString(input, """
            observed_at,exchange,market_id,market_name,event_name,competition_name,season,odds_source,market_start_time,selection_id,runner_name,best_back_price,best_lay_price,spread,liquidity,result
            2026-06-01T10:00:00Z,betfair,1.1,Match Odds,Team A v Team B,La Liga,2025/26,closing-average,2026-06-01T18:00:00Z,42,Team A,2.60,2.70,0.04,1000,WIN
            """);

        var rows = new CsvBacktestHistoryReader().read(input);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.observedAt()).hasToString("2026-06-01T10:00:00Z");
            assertThat(row.exchange()).isEqualTo("betfair");
            assertThat(row.marketId()).isEqualTo("1.1");
            assertThat(row.season()).isEqualTo("2025/26");
            assertThat(row.oddsSource()).isEqualTo("closing-average");
            assertThat(row.selectionId()).isEqualTo(42L);
            assertThat(row.bestBackPrice()).isEqualByComparingTo("2.60");
            assertThat(row.bestLayPrice()).isEqualByComparingTo("2.70");
            assertThat(row.spread()).isEqualByComparingTo("0.04");
            assertThat(row.liquidity()).isEqualByComparingTo("1000");
            assertThat(row.outcome()).isEqualTo(BacktestOutcome.WIN);
        });
    }

    @Test
    void remainsCompatibleWithRowsWithoutSeasonAndOddsSource() throws Exception {
        Path input = tempDir.resolve("legacy-history.csv");
        Files.writeString(input, """
            observed_at,exchange,market_id,market_name,event_name,competition_name,market_start_time,selection_id,runner_name,best_back_price,best_lay_price,spread,liquidity,result
            2026-06-01T10:00:00Z,betfair,1.1,Match Odds,Team A v Team B,La Liga,2026-06-01T18:00:00Z,42,Team A,2.60,2.70,0.04,1000,WIN
            """);

        var rows = new CsvBacktestHistoryReader().read(input);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.season()).isEqualTo("2025/26");
            assertThat(row.oddsSource()).isEqualTo("unknown");
        });
    }

    @Test
    void rejectsRowsMissingRequiredFieldsWithActionableMessage() throws Exception {
        Path input = tempDir.resolve("history.csv");
        Files.writeString(input, """
            observed_at,exchange,market_id,market_name,event_name,competition_name,market_start_time,selection_id,runner_name,best_back_price,best_lay_price,spread,liquidity,result
            2026-06-01T10:00:00Z,betfair,,Match Odds,Team A v Team B,La Liga,2026-06-01T18:00:00Z,42,Team A,2.60,2.70,0.04,1000,WIN
            """);

        assertThatThrownBy(() -> new CsvBacktestHistoryReader().read(input))
            .isInstanceOf(BacktestValidationException.class)
            .hasMessageContaining("Backtest CSV row 2 is invalid")
            .hasMessageContaining("market_id is required");
    }

    @Test
    void rejectsUnsupportedOutcomeValues() throws Exception {
        Path input = tempDir.resolve("history.csv");
        Files.writeString(input, """
            observed_at,exchange,market_id,market_name,event_name,competition_name,market_start_time,selection_id,runner_name,best_back_price,best_lay_price,spread,liquidity,result
            2026-06-01T10:00:00Z,betfair,1.1,Match Odds,Team A v Team B,La Liga,2026-06-01T18:00:00Z,42,Team A,2.60,2.70,0.04,1000,DRAW
            """);

        assertThatThrownBy(() -> new CsvBacktestHistoryReader().read(input))
            .isInstanceOf(BacktestValidationException.class)
            .hasMessageContaining("result must be WIN or LOSE");
    }
}
