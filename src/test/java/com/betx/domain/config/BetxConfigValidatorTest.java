package com.betx.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betx.common.ConfigException;
import com.betx.domain.betfair.BetfairAutoBettingConfig;
import com.betx.domain.betfair.BetfairConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BetxConfigValidatorTest {
    private final BetxConfigValidator validator = new BetxConfigValidator();

    @Test
    void acceptsDefaultConfiguration() {
        assertThatCode(() -> validator.validate(BetxConfig.defaults())).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidStructuredLogRetention() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            new AppConfig("info", new StructuredLogsConfig(true, "./logs/events", 0)),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            defaults.storage(),
            defaults.paper(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml(),
            defaults.intelligence(),
            defaults.resilience(),
            defaults.execution()
        );

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("app.structured_logs.retention_days must be greater than zero.");
    }

    @Test
    void acceptsConfigurationWithoutAppMode() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            null,
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );

        assertThatCode(() -> validator.validate(config)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonPositiveRiskLimits() {
        BetxConfig config = configWithRisk(new RiskConfig(BigDecimal.ZERO, BigDecimal.valueOf(25), 3));

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("risk.max_stake must be greater than zero.");
    }

    @Test
    void rejectsNonPositiveOpenPositions() {
        BetxConfig config = configWithRisk(new RiskConfig(BigDecimal.valueOf(5), BigDecimal.valueOf(25), 0));

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("risk.max_open_positions must be greater than zero.");
    }

    @Test
    void rejectsInvalidStakeSizingLimits() {
        BetxConfig config = configWithStaking(new StakingConfig(
            false,
            true,
            null,
            BigDecimal.ONE,
            BigDecimal.valueOf(10),
            BigDecimal.ONE,
            BigDecimal.valueOf(500),
            null,
            null,
            null
        ));

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("staking.min_stake must not be greater than staking.max_stake.");
    }

    @Test
    void rejectsEnabledBetfairAutoBettingWithNonPositiveStake() {
        BetxConfig config = configWithBetfairAutoBetting(new BetfairAutoBettingConfig(
            true,
            true,
            BigDecimal.ZERO,
            BigDecimal.valueOf(25),
            3
        ));

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("exchanges.betfair.auto_betting.max_stake must be greater than zero.");
    }

    @Test
    void rejectsEnabledBetfairAutoBettingWithNonPositiveOpenPositions() {
        BetxConfig config = configWithBetfairAutoBetting(new BetfairAutoBettingConfig(
            true,
            true,
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(25),
            0
        ));

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("exchanges.betfair.auto_betting.max_open_positions must be greater than zero.");
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
    void rejectsInvalidExecutionQueueConfiguration() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            defaults.storage(),
            defaults.paper(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml(),
            defaults.intelligence(),
            defaults.resilience(),
            new ExecutionConfig(new ExecutionQueueConfig(true, 0, "10s", "5s", "3s", new BigDecimal("0.01")))
        );

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("execution.queue.max_pending_per_exchange must be greater than zero.");
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

    @Test
    void rejectsInlineOpenRouterApiKeyInApiKeyEnv() {
        BetxConfig config = BetxConfig.defaults()
            .withIntelligence(new IntelligenceConfig(true, "openrouter", "x-ai/grok-4.3", "sk-or-v1-secret", null, 20, 70));

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("intelligence.api_key_env must be an environment variable name, not an API key.");
    }

    @Test
    void acceptsInlineOpenRouterApiKeyInApiKey() {
        BetxConfig config = BetxConfig.defaults()
            .withIntelligence(new IntelligenceConfig(true, "openrouter", "x-ai/grok-4.3", "OPENROUTER_API_KEY", "sk-or-v1-secret", 20, 70));

        assertThatCode(() -> validator.validate(config)).doesNotThrowAnyException();
    }

    @Test
    void defaultsMissingIntelligenceAutoBettingPolicyToStrictApprove() {
        IntelligenceConfig config = new IntelligenceConfig(true, "openrouter", "x-ai/grok-4.3", "OPENROUTER_API_KEY", null, 20, 70);

        assertThat(config.autoBettingPolicy()).isEqualTo(IntelligenceAutoBettingPolicy.STRICT_APPROVE);
    }

    @Test
    void acceptsBlockOnlyOnRejectIntelligenceAutoBettingPolicy() {
        BetxConfig config = BetxConfig.defaults()
            .withIntelligence(new IntelligenceConfig(
                true,
                "openrouter",
                "x-ai/grok-4.3",
                "OPENROUTER_API_KEY",
                null,
                20,
                70,
                IntelligenceAutoBettingPolicy.BLOCK_ONLY_ON_REJECT
            ));

        assertThatCode(() -> validator.validate(config)).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidPaperReadinessGateConfiguration() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            defaults.storage(),
            new PaperConfig(false, null, null, null, new PaperReadinessGateConfig(
                true,
                0,
                "CANDIDATE_EDGE",
                new BigDecimal("0.01"),
                BigDecimal.ZERO,
                100,
                BigDecimal.ZERO,
                true
            )),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml(),
            defaults.intelligence(),
            defaults.resilience(),
            defaults.execution()
        );

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("paper.readiness_gate.minimum_settled_trades must be greater than zero.");
    }

    @Test
    void rejectsUnknownPaperReadinessEvidenceStatus() {
        BetxConfig defaults = BetxConfig.defaults();
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            defaults.storage(),
            new PaperConfig(false, null, null, null, new PaperReadinessGateConfig(
                true,
                100,
                "MAYBE",
                new BigDecimal("0.01"),
                BigDecimal.ZERO,
                100,
                BigDecimal.ZERO,
                true
            )),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml(),
            defaults.intelligence(),
            defaults.resilience(),
            defaults.execution()
        );

        assertThatThrownBy(() -> validator.validate(config))
            .isInstanceOf(ConfigException.class)
            .hasMessage("paper.readiness_gate.required_evidence_status must be one of: INSUFFICIENT_DATA, INSUFFICIENT_SAMPLE, NEGATIVE_EXECUTABLE_ROI, FRAGILE_EDGE, HISTORICAL_CANDIDATE, WEAK_EVIDENCE, EXECUTION_FAILURE, CANDIDATE_EDGE.");
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

    private BetxConfig configWithBetfairAutoBetting(BetfairAutoBettingConfig autoBetting) {
        BetxConfig defaults = BetxConfig.defaults();
        return new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            List.of(new ExchangeConfig("betfair", true, new BetfairConfig("user", "password", "app-key", null, autoBetting))),
            defaults.marketData(),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );
    }

    private BetxConfig configWithStaking(StakingConfig staking) {
        BetxConfig defaults = BetxConfig.defaults();
        return new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            defaults.storage(),
            defaults.paper(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml(),
            defaults.intelligence(),
            defaults.resilience(),
            defaults.execution(),
            staking
        );
    }
}
