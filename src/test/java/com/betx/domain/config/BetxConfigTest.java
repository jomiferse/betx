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

    @Test
    void providesDefaultPaperTradingConfiguration() {
        BetxConfig config = BetxConfig.defaults();

        assertThat(config.paper().continuous()).isFalse();
        assertThat(config.paper().pollInterval()).isEqualTo(java.time.Duration.ofSeconds(60));
        assertThat(config.paper().closingCaptureMinutesBeforeStart()).isEqualTo(2);
        assertThat(config.paper().settlementPollInterval()).isEqualTo(java.time.Duration.ofMinutes(5));
        assertThat(config.paper().readinessGate().enabled()).isFalse();
        assertThat(config.paper().readinessGate().minimumSettledTrades()).isEqualTo(100);
        assertThat(config.paper().readinessGate().requiredEvidenceStatus()).isEqualTo("CANDIDATE_EDGE");
        assertThat(config.paper().readinessGate().minimumExecutableRoi()).isEqualByComparingTo("0.01");
        assertThat(config.paper().readinessGate().minimumMedianClv()).isEqualByComparingTo("0.00");
        assertThat(config.paper().readinessGate().rollingWindowSize()).isEqualTo(100);
        assertThat(config.paper().readinessGate().minimumRollingRoi()).isEqualByComparingTo("0.00");
        assertThat(config.paper().readinessGate().blockOnExecutionFailure()).isTrue();
    }

    @Test
    void providesDefaultOrderExecutionQueueConfiguration() {
        BetxConfig config = BetxConfig.defaults();

        assertThat(config.execution().queue().enabled()).isTrue();
        assertThat(config.execution().queue().maxPendingPerExchange()).isEqualTo(20);
        assertThat(config.execution().queue().orderTtl()).isEqualTo(java.time.Duration.ofSeconds(10));
        assertThat(config.execution().queue().staleBalanceTtl()).isEqualTo(java.time.Duration.ofSeconds(5));
        assertThat(config.execution().queue().revalidateOddsAfter()).isEqualTo(java.time.Duration.ofSeconds(3));
        assertThat(config.execution().queue().minEffectiveBalance()).isEqualByComparingTo("0.01");
    }

    @Test
    void providesDefaultStakeSizingShadowConfiguration() {
        BetxConfig config = BetxConfig.defaults();

        assertThat(config.staking().enabled()).isFalse();
        assertThat(config.staking().shadowEnabled()).isTrue();
        assertThat(config.staking().baseStake()).isEqualByComparingTo("1.00");
        assertThat(config.staking().minStake()).isEqualByComparingTo("1.00");
        assertThat(config.staking().maxStake()).isEqualByComparingTo("10.00");
        assertThat(config.staking().bankroll()).isEqualByComparingTo("500.00");
        assertThat(config.staking().shadow().enabled()).isTrue();
        assertThat(config.staking().shadow().policies()).contains(
            com.betx.domain.staking.StakeSizingMode.FLAT,
            com.betx.domain.staking.StakeSizingMode.RISK_ADJUSTED,
            com.betx.domain.staking.StakeSizingMode.TIERED_CONFIDENCE,
            com.betx.domain.staking.StakeSizingMode.FRACTIONAL_KELLY_SHADOW
        );
    }

    @Test
    void providesDefaultStructuredLoggingConfiguration() {
        BetxConfig config = BetxConfig.defaults();

        assertThat(config.app().logLevel()).isEqualTo("info");
        assertThat(config.app().structuredLogs().enabled()).isTrue();
        assertThat(config.app().structuredLogs().directory()).isEqualTo("./logs/events");
        assertThat(config.app().structuredLogs().retentionDays()).isEqualTo(30);
    }
}
