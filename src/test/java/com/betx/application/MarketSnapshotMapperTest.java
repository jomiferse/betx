package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairRunnerPrice;
import com.betx.domain.signal.RunnerType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
            assertThat(snapshot.runnerType()).isEqualTo(RunnerType.HOME);
            assertThat(snapshot.bestBackPrice()).isEqualByComparingTo("2.50");
            assertThat(snapshot.bestLayPrice()).isEqualByComparingTo("2.60");
            assertThat(snapshot.spread()).isEqualByComparingTo("0.04");
            assertThat(snapshot.liquidity()).isEqualByComparingTo("1500");
        });
    }

    @Test
    void mapsCommonDrawRunnerNamesToDraw() {
        assertThat(snapshotForRunner("Draw").runnerType()).isEqualTo(RunnerType.DRAW);
        assertThat(snapshotForRunner("The Draw").runnerType()).isEqualTo(RunnerType.DRAW);
        assertThat(snapshotForRunner("  tHe DrAw  ").runnerType()).isEqualTo(RunnerType.DRAW);
    }

    @Test
    void doesNotMapTeamNamesToDraw() {
        assertThat(snapshotForRunner("Drawbridge FC").runnerType()).isNotEqualTo(RunnerType.DRAW);
        assertThat(snapshotForRunner("Team Draw United").runnerType()).isNotEqualTo(RunnerType.DRAW);
        assertThat(snapshotForRunner("Team A").runnerType()).isNotEqualTo(RunnerType.DRAW);
    }

    @Test
    void mapsOneDrawRunnerInThreeRunnerMatchOddsMarket() {
        BetfairMarketCatalogue catalogue = catalogue(Map.of(
            111L, "Team A",
            222L, "The Draw",
            333L, "Team B"
        ));
        BetfairMarketBook book = new BetfairMarketBook(
            "1.234",
            "OPEN",
            false,
            BigDecimal.valueOf(1_500),
            List.of(
                price(111L, "2.10"),
                price(222L, "3.50"),
                price(333L, "3.90")
            )
        );

        var snapshots = mapper.toSnapshots(List.of(catalogue), List.of(book));

        assertThat(snapshots).hasSize(3);
        assertThat(snapshots)
            .filteredOn(snapshot -> snapshot.runnerType() == RunnerType.DRAW)
            .singleElement()
            .satisfies(snapshot -> assertThat(snapshot.selectionId()).isEqualTo(222L));
    }

    private com.betx.domain.signal.MarketSnapshot snapshotForRunner(String runnerName) {
        BetfairMarketCatalogue catalogue = catalogue(Map.of(42L, runnerName));
        BetfairMarketBook book = new BetfairMarketBook(
            "1.234",
            "OPEN",
            false,
            BigDecimal.valueOf(1_500),
            List.of(price(42L, "2.50"))
        );
        return mapper.toSnapshots(List.of(catalogue), List.of(book)).getFirst();
    }

    private BetfairMarketCatalogue catalogue(Map<Long, String> runnerNames) {
        return new BetfairMarketCatalogue(
            "1.234",
            "Match Odds",
            "Team A v Team B",
            "La Liga",
            Instant.parse("2026-06-01T18:00:00Z"),
            runnerNames
        );
    }

    private BetfairRunnerPrice price(long selectionId, String odds) {
        BigDecimal back = new BigDecimal(odds);
        return new BetfairRunnerPrice(
            selectionId,
            null,
            back,
            back.add(new BigDecimal("0.10")),
            BigDecimal.valueOf(300)
        );
    }
}
