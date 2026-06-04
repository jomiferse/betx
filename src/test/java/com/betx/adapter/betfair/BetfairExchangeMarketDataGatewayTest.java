package com.betx.adapter.betfair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betx.application.MarketSnapshotMapper;
import com.betx.application.port.out.BetfairGateway;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairEvent;
import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairMarketQuery;
import com.betx.domain.betfair.BetfairRunnerPrice;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.config.ExchangeConfig;
import com.betx.domain.config.MarketDataConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BetfairExchangeMarketDataGatewayTest {
    @Test
    void readsBetfairMarketsAndReturnsNormalizedSnapshots() {
        RecordingBetfairGateway gateway = new RecordingBetfairGateway(List.of(catalogue()), List.of(book()));
        BetfairExchangeMarketDataGateway adapter = new BetfairExchangeMarketDataGateway(gateway, new MarketSnapshotMapper());

        var snapshots = adapter.listSnapshots(exchange());

        assertThat(gateway.credentials()).isEqualTo(new BetfairCredentials("user", "password", "app-key", exchange().betfair().country()));
        assertThat(gateway.query()).isEqualTo(new BetfairMarketQuery(List.of("1"), List.of(), List.of(), 1000));
        assertThat(gateway.marketIds()).containsExactly("1.234");
        assertThat(snapshots).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.exchange()).isEqualTo("betfair");
            assertThat(snapshot.marketId()).isEqualTo("1.234");
            assertThat(snapshot.selectionId()).isEqualTo(42L);
        });
    }

    @Test
    void fullScanUsesBroadCatalogueQueryAndFiltersMatchOddsLocally() {
        RecordingBetfairGateway gateway = new RecordingBetfairGateway(
            List.of(catalogue("1.234", "Team A v Team B"), catalogue("1.235", "Over/Under 2.5 Goals", "Team A v Team B")),
            List.of(book()),
            List.of(
                new BetfairEvent("e-1", "Team A v Team B", "ES", "GMT", Instant.parse("2026-06-01T18:00:00Z"), 11),
                new BetfairEvent("e-test", "Test C v Test V", "RO", "GMT", Instant.parse("2026-06-01T18:00:00Z"), 11)
            )
        );
        BetfairExchangeMarketDataGateway adapter = new BetfairExchangeMarketDataGateway(gateway, new MarketSnapshotMapper());

        var result = adapter.listMarketData(exchange(new MarketDataConfig(60, 0, List.of("1"), List.of("MATCH_ODDS"), true, 2)));

        assertThat(gateway.eventTypeIds()).isNull();
        assertThat(gateway.query()).isEqualTo(new BetfairMarketQuery(List.of("1"), List.of(), List.of(), 1000));
        assertThat(result.snapshots()).hasSize(1);
        assertThat(result.eventsRead()).isEqualTo(1);
        assertThat(result.ignoredEvents()).isZero();
    }

    @Test
    void limitedScanUsesConfiguredMaxMarketsWithoutEventDiscovery() {
        RecordingBetfairGateway gateway = new RecordingBetfairGateway(List.of(catalogue()), List.of(book()));
        BetfairExchangeMarketDataGateway adapter = new BetfairExchangeMarketDataGateway(gateway, new MarketSnapshotMapper());

        adapter.listSnapshots(exchange(new MarketDataConfig(60, 7, List.of("1"), List.of("MATCH_ODDS"), false, 50)));

        assertThat(gateway.eventTypeIds()).isNull();
        assertThat(gateway.query()).isEqualTo(new BetfairMarketQuery(List.of("1"), List.of(), List.of("MATCH_ODDS"), 7));
    }

    @Test
    void fullScanFallsBackToCatalogueQueryWhenEventDiscoveryReturnsTooMuchData() {
        RecordingBetfairGateway gateway = new RecordingBetfairGateway(List.of(catalogue()), List.of(book()));
        gateway.eventFailure = new IllegalStateException("Betfair request failed: TOO_MUCH_DATA");
        BetfairExchangeMarketDataGateway adapter = new BetfairExchangeMarketDataGateway(gateway, new MarketSnapshotMapper());

        var snapshots = adapter.listSnapshots(exchange(new MarketDataConfig(60, 0, List.of("1"), List.of("MATCH_ODDS"), true, 50)));

        assertThat(gateway.query().eventIds()).isEmpty();
        assertThat(gateway.query().marketTypeCodes()).isEmpty();
        assertThat(gateway.query().maxResults()).isEqualTo(1000);
        assertThat(gateway.query().marketStartTimeFrom()).isNull();
        assertThat(gateway.query().marketStartTimeTo()).isNull();
        assertThat(snapshots).hasSize(1);
    }

    @Test
    void listsMarketBooksInBatches() {
        List<BetfairMarketCatalogue> catalogues = java.util.stream.IntStream.range(0, 45)
            .mapToObj(index -> catalogue("1." + index, "Team A v Team B"))
            .toList();
        List<BetfairMarketBook> books = java.util.stream.IntStream.range(0, 45)
            .mapToObj(index -> book("1." + index))
            .toList();
        RecordingBetfairGateway gateway = new RecordingBetfairGateway(catalogues, books);
        BetfairExchangeMarketDataGateway adapter = new BetfairExchangeMarketDataGateway(gateway, new MarketSnapshotMapper());

        adapter.listSnapshots(exchange());

        assertThat(gateway.marketIdBatches()).hasSize(2);
        assertThat(gateway.marketIdBatches().get(0)).hasSize(40);
        assertThat(gateway.marketIdBatches().get(1)).hasSize(5);
    }

    @Test
    void doesNotListBooksWhenNoCataloguesAreReturned() {
        RecordingBetfairGateway gateway = new RecordingBetfairGateway(List.of(), List.of(book()));
        BetfairExchangeMarketDataGateway adapter = new BetfairExchangeMarketDataGateway(gateway, new MarketSnapshotMapper());

        assertThat(adapter.listSnapshots(exchange())).isEmpty();
        assertThat(gateway.marketIds()).isNull();
    }

    @Test
    void failsWhenBetfairCredentialsAreMissing() {
        BetfairExchangeMarketDataGateway adapter = new BetfairExchangeMarketDataGateway(
            new RecordingBetfairGateway(List.of(), List.of()),
            new MarketSnapshotMapper()
        );

        assertThatThrownBy(() -> adapter.listSnapshots(new ExchangeConfig("betfair", true, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Betfair credentials are missing from betx.yml.");
    }

    private ExchangeConfig exchange() {
        return new ExchangeConfig("betfair", true, new BetfairConfig("user", "password", "app-key"));
    }

    private ExchangeConfig exchange(MarketDataConfig marketData) {
        return new ExchangeConfig("betfair", true, new BetfairConfig("user", "password", "app-key"), marketData);
    }

    private BetfairMarketCatalogue catalogue() {
        return catalogue("1.234", "Team A v Team B");
    }

    private BetfairMarketCatalogue catalogue(String marketId, String eventName) {
        return new BetfairMarketCatalogue(marketId, "Match Odds", eventName, "La Liga", Instant.parse("2026-06-01T18:00:00Z"));
    }

    private BetfairMarketCatalogue catalogue(String marketId, String marketName, String eventName) {
        return new BetfairMarketCatalogue(marketId, marketName, eventName, "La Liga", Instant.parse("2026-06-01T18:00:00Z"));
    }

    private BetfairMarketBook book() {
        return book("1.234");
    }

    private BetfairMarketBook book(String marketId) {
        return new BetfairMarketBook(
            marketId,
            "OPEN",
            false,
            BigDecimal.valueOf(1_500),
            List.of(new BetfairRunnerPrice(42L, null, BigDecimal.valueOf(2.50), BigDecimal.valueOf(2.60), BigDecimal.valueOf(300)))
        );
    }

    private static final class RecordingBetfairGateway implements BetfairGateway {
        private final List<BetfairMarketCatalogue> catalogues;
        private final List<BetfairMarketBook> books;
        private final List<BetfairEvent> events;
        private BetfairCredentials credentials;
        private BetfairMarketQuery query;
        private List<String> marketIds;
        private final List<List<String>> marketIdBatches = new java.util.ArrayList<>();
        private List<String> eventTypeIds;
        private List<String> eventMarketTypeCodes;
        private RuntimeException eventFailure;

        private RecordingBetfairGateway(List<BetfairMarketCatalogue> catalogues, List<BetfairMarketBook> books) {
            this(catalogues, books, List.of(new BetfairEvent("e-1", "Team A v Team B", "ES", "GMT", Instant.parse("2026-06-01T18:00:00Z"), 1)));
        }

        private RecordingBetfairGateway(List<BetfairMarketCatalogue> catalogues, List<BetfairMarketBook> books, List<BetfairEvent> events) {
            this.catalogues = catalogues;
            this.books = books;
            this.events = events;
        }

        @Override
        public BetfairSession login(BetfairCredentials credentials) {
            this.credentials = credentials;
            return new BetfairSession("session-token", credentials.appKey());
        }

        @Override
        public List<BetfairMarketCatalogue> listMarketCatalogue(BetfairSession session, BetfairMarketQuery query) {
            this.query = query;
            return catalogues;
        }

        @Override
        public List<BetfairEvent> listEvents(BetfairSession session, List<String> eventTypeIds, List<String> marketTypeCodes) {
            if (eventFailure != null) {
                throw eventFailure;
            }
            this.eventTypeIds = eventTypeIds;
            this.eventMarketTypeCodes = marketTypeCodes;
            return events;
        }

        @Override
        public List<BetfairMarketBook> listMarketBook(BetfairSession session, List<String> marketIds) {
            this.marketIds = marketIds;
            this.marketIdBatches.add(marketIds);
            return books.stream()
                .filter(book -> marketIds.contains(book.marketId()))
                .toList();
        }

        private BetfairCredentials credentials() {
            return credentials;
        }

        private BetfairMarketQuery query() {
            return query;
        }

        private List<String> eventTypeIds() {
            return eventTypeIds;
        }

        private List<String> eventMarketTypeCodes() {
            return eventMarketTypeCodes;
        }

        private List<String> marketIds() {
            return marketIds;
        }

        private List<List<String>> marketIdBatches() {
            return marketIdBatches;
        }
    }
}
