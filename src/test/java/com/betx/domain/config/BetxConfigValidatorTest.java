package com.betx.domain.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betx.common.ConfigException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BetxConfigValidatorTest {
    private final BetxConfigValidator validator = new BetxConfigValidator();

    @Test
    void acceptsDefaultDryRunConfiguration() {
        assertThatCode(() -> validator.validate(BetxConfig.defaults())).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedAppMode() {
        BetxConfig config = BetxConfig.defaults().withMode("paper");

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("app.mode must be dry-run or live.");
    }

    @Test
    void rejectsLiveModeWhenLiveBettingIsDisabled() {
        BetxConfig config = BetxConfig.defaults().withMode("live");

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("Refusing live mode because risk.live_betting_enabled is false.");
    }

    @Test
    void rejectsNonPositiveRiskLimits() {
        BetxConfig config = configWithRisk(new RiskConfig(BigDecimal.ZERO, BigDecimal.valueOf(25), 3, false));

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("risk.max_stake must be greater than zero.");
    }

    @Test
    void rejectsNonPositiveOpenPositions() {
        BetxConfig config = configWithRisk(new RiskConfig(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 0, false));

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("risk.max_open_positions must be greater than zero.");
    }

    @Test
    void rejectsUnsupportedStorageType() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            new StorageConfig("postgres", "./data/betx.db"),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("storage.type must be sqlite.");
    }

    @Test
    void rejectsNonPositivePollInterval() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            new MarketDataConfig(0, 5, java.util.List.of("1"), java.util.List.of("MATCH_ODDS"), true, 50),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("market_data.poll_interval_seconds must be greater than zero.");
    }

    @Test
    void acceptsZeroMaxMarketsAsUnlimited() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            new MarketDataConfig(60, 0, java.util.List.of("1"), java.util.List.of("MATCH_ODDS"), true, 50),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );

        assertThatCode(() -> validator.validate(config)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNegativeMaxMarkets() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            new MarketDataConfig(60, -1, java.util.List.of("1"), java.util.List.of("MATCH_ODDS"), false, 50),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("market_data.max_markets must be zero or greater.");
    }

    @Test
    void rejectsNonPositiveBetfairEventBatchSize() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            new MarketDataConfig(60, 0, java.util.List.of("1"), java.util.List.of("MATCH_ODDS"), true, 0),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("market_data.betfair_event_batch_size must be greater than zero.");
    }

    private BetxConfig configWithRisk(RiskConfig risk) {
        BetxConfig defaults = BetxConfig.defaults();
        return new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            defaults.storage(),
            risk,
            defaults.strategies(),
            defaults.ml()
        );
    }
}
