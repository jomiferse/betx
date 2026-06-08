package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.common.ConfigException;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StartBetxServiceTest {
    private static final ConfigPath CONFIG_PATH = new ConfigPath(Path.of("betx.yml"));

    @Test
    void returnsStartupStatusFromValidatedConfig() {
        StartBetxService service = new StartBetxService(new StaticConfigRepository(BetxConfig.defaults()));

        var status = service.start(CONFIG_PATH);

        assertThat(status.telegramEnabled()).isTrue();
        assertThat(status.mlEnabled()).isFalse();
        assertThat(status.storagePath()).isEqualTo("./data/betx.db");
        assertThat(status.pollIntervalSeconds()).isEqualTo(60);
    }

    @Test
    void propagatesValidationErrors() {
        BetxConfig defaults = BetxConfig.defaults();
        StartBetxService service = new StartBetxService(new StaticConfigRepository(new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            new com.betx.domain.config.MarketDataConfig(0, 0, java.util.List.of("1"), java.util.List.of("MATCH_ODDS"), true, 50),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        )));

        assertThatThrownBy(() -> service.start(CONFIG_PATH))
            .isInstanceOf(ConfigException.class)
            .hasMessage("market_data.poll_interval_seconds must be greater than zero.");
    }

    private record StaticConfigRepository(BetxConfig config) implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return config;
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }
}
