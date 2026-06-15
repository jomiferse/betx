package com.betx.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BacktestOutcome;
import com.betx.application.PaperTrade;
import com.betx.application.PaperTradeStatus;
import com.betx.domain.signal.MarketSnapshot;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcPaperTradeRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void upsertsPaperTradesByMarketSelectionAcrossRepositoryInstances() {
        String databasePath = tempDir.resolve("paper.db").toString();
        JdbcPaperTradeRepository writer = new JdbcPaperTradeRepository(databasePath);
        PaperTrade recommended = PaperTrade.recommended(snapshot(), Instant.parse("2026-06-15T10:00:00Z"), BigDecimal.valueOf(5));
        PaperTrade settled = recommended
            .withExecuted(Instant.parse("2026-06-15T10:01:00Z"), new BigDecimal("3.70"), true)
            .withClosed(Instant.parse("2026-06-15T17:50:00Z"), new BigDecimal("3.50"))
            .withSettled(Instant.parse("2026-06-15T20:00:00Z"), BacktestOutcome.WIN, new BigDecimal("0.05"));

        writer.upsert(databasePath, recommended);
        writer.upsert(databasePath, settled);

        JdbcPaperTradeRepository reader = new JdbcPaperTradeRepository(databasePath);
        assertThat(reader.listAll(databasePath)).singleElement().satisfies(trade -> {
            assertThat(trade.status()).isEqualTo(PaperTradeStatus.SETTLED);
            assertThat(trade.paperMode()).isTrue();
            assertThat(trade.exchange()).isEqualTo("betfair");
            assertThat(trade.marketId()).isEqualTo("1.234");
            assertThat(trade.selectionId()).isEqualTo(2L);
            assertThat(trade.executionOdds()).isEqualByComparingTo("3.70");
            assertThat(trade.closingOdds()).isEqualByComparingTo("3.50");
            assertThat(trade.decimalClvRatio()).isEqualByComparingTo("0.05714286");
            assertThat(trade.grossPnl()).isEqualByComparingTo("13.50");
            assertThat(trade.commission()).isEqualByComparingTo("0.68");
            assertThat(trade.netPnl()).isEqualByComparingTo("12.82");
        });
        assertThat(reader.findByMarketSelection(databasePath, "betfair", "1.234", 2L))
            .get()
            .extracting(PaperTrade::status)
            .isEqualTo(PaperTradeStatus.SETTLED);
    }

    private static MarketSnapshot snapshot() {
        return new MarketSnapshot(
            "betfair",
            "1.234",
            "Match Odds",
            "Team A v Team B",
            "SP1",
            Instant.parse("2026-06-15T18:00:00Z"),
            2L,
            "Draw",
            new BigDecimal("3.70"),
            new BigDecimal("3.80"),
            new BigDecimal("0.03"),
            new BigDecimal("1200")
        );
    }
}
