package com.betx.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.betfair.BetfairConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class BetxConfigTest {
    @Test
    void normalizesExchangeNamesAndFiltersDisabledExchanges() {
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(
            new ExchangeConfig(" BetFair ", true, new BetfairConfig("user", "password", "app-key")),
            new ExchangeConfig("smarkets", false, null)
        ));

        assertThat(config.enabledExchanges()).singleElement().satisfies(exchange -> {
            assertThat(exchange.name()).isEqualTo("betfair");
            assertThat(exchange.betfair().isConfigured()).isTrue();
        });
    }

    @Test
    void createsLegacyBetfairExchangeOnlyWhenCredentialsAreConfigured() {
        BetfairConfig betfair = new BetfairConfig("user", "password", "app-key");
        BetxConfig defaults = BetxConfig.defaults();

        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            betfair,
            null,
            defaults.marketData(),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );

        assertThat(config.enabledExchanges()).singleElement().satisfies(exchange -> {
            assertThat(exchange.name()).isEqualTo("betfair");
            assertThat(exchange.betfair()).isEqualTo(betfair);
        });
    }

    @Test
    void doesNotCreateLegacyExchangeWhenBetfairCredentialsAreBlank() {
        assertThat(BetxConfig.defaults().enabledExchanges()).isEmpty();
    }

    @Test
    void providesDefaultMarketDataConfiguration() {
        BetxConfig config = BetxConfig.defaults();

        assertThat(config.marketData().pollIntervalSeconds()).isEqualTo(60);
        assertThat(config.marketData().maxMarkets()).isZero();
        assertThat(config.marketData().scanAllMarkets()).isTrue();
        assertThat(config.marketData().betfairEventBatchSize()).isEqualTo(50);
        assertThat(config.marketData().eventTypeIds()).containsExactly("1");
        assertThat(config.marketData().marketTypeCodes()).containsExactly("MATCH_ODDS");
    }

    @Test
    void providesDefaultMarketSnapshotCleanupPolicy() {
        BetxConfig config = BetxConfig.defaults();

        assertThat(config.storage().cleanupMarketSnapshotsEnabled()).isTrue();
        assertThat(config.storage().marketSnapshotRetentionHours()).isEqualTo(48);
    }
}
