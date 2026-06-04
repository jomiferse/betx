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

        assertThat(status.mode()).isEqualTo("dry-run");
        assertThat(status.telegramEnabled()).isTrue();
        assertThat(status.mlEnabled()).isFalse();
        assertThat(status.liveBettingEnabled()).isFalse();
        assertThat(status.storagePath()).isEqualTo("./data/betx.db");
        assertThat(status.pollIntervalSeconds()).isEqualTo(60);
    }

    @Test
    void propagatesValidationErrors() {
        StartBetxService service = new StartBetxService(new StaticConfigRepository(BetxConfig.defaults().withMode("paper")));

        assertThatThrownBy(() -> service.start(CONFIG_PATH))
            .isInstanceOf(ConfigException.class)
            .hasMessage("app.mode must be dry-run or live.");
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
