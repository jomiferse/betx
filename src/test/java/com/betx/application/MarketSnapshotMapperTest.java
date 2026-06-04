package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairRunnerPrice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketSnapshotMapperTest {
    private final MarketSnapshotMapper mapper = new MarketSnapshotMapper();

    @Test
    void combinesCatalogueAndMarketBookRunnerPricesIntoSnapshots() {
        BetfairMarketCatalogue catalogue = new BetfairMarketCatalogue(
            "1.234",
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            java.util.Map.of(42L, "Team A")
        );
        BetfairMarketBook book = new BetfairMarketBook(
            "1.234",
            "OPEN",
            false,
            BigDecimal.valueOf(1_500),
            List.of(new BetfairRunnerPrice(42L, null, BigDecimal.valueOf(2.50), BigDecimal.valueOf(2.60), BigDecimal.valueOf(300)))
        );

        var snapshots = mapper.toSnapshots(List.of(catalogue), List.of(book));

        assertThat(snapshots).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.exchange()).isEqualTo("betfair");
            assertThat(snapshot.marketId()).isEqualTo("1.234");
            assertThat(snapshot.selectionId()).isEqualTo(42L);
            assertThat(snapshot.runnerName()).isEqualTo("Team A");
            assertThat(snapshot.bestBackPrice()).isEqualByComparingTo("2.50");
            assertThat(snapshot.bestLayPrice()).isEqualByComparingTo("2.60");
            assertThat(snapshot.spread()).isEqualByComparingTo("0.04");
            assertThat(snapshot.liquidity()).isEqualByComparingTo("1500");
        });
    }
}
