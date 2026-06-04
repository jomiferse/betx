package com.betx.adapter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betx.common.ConfigException;
import com.betx.domain.betfair.BetfairCountry;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlBetxConfigRepositoryTest {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    private final YamlBetxConfigRepository repository = new YamlBetxConfigRepository();

    @TempDir
    Path tempDir;

    @Test
    void readsBetfairCountryFromYaml() throws Exception {
        BetxConfig config = mapper.readValue("""
            betfair:
              username: user
              password: password
              app_key: app-key
              country: romania
            """, BetxConfig.class);

        assertThat(config.betfair().country()).isEqualTo(BetfairCountry.ROMANIA);
    }

    @Test
    void readsNewExchangeConfigFromYaml() throws Exception {
        BetxConfig config = mapper.readValue("""
            exchanges:
              - name: betfair
                enabled: true
                betfair:
                  username: user
                  password: password
                  app_key: app-key
                  country: spain
              - name: smarkets
                enabled: false
            """, BetxConfig.class);

        assertThat(config.exchanges()).hasSize(2);
        assertThat(config.enabledExchanges()).singleElement().satisfies(exchange -> {
            assertThat(exchange.name()).isEqualTo("betfair");
            assertThat(exchange.betfair().appKey()).isEqualTo("app-key");
        });
    }

    @Test
    void readsMarketDataOverridesFromYaml() throws Exception {
        BetxConfig config = mapper.readValue("""
            market_data:
              poll_interval_seconds: 15
              max_markets: 9
              scan_all_markets: false
              betfair_event_batch_size: 25
              event_type_ids: ["1", "2"]
              market_type_codes: ["MATCH_ODDS", "OVER_UNDER_25"]
            """, BetxConfig.class);

        assertThat(config.marketData().pollIntervalSeconds()).isEqualTo(15);
        assertThat(config.marketData().maxMarkets()).isEqualTo(9);
        assertThat(config.marketData().scanAllMarkets()).isFalse();
        assertThat(config.marketData().betfairEventBatchSize()).isEqualTo(25);
        assertThat(config.marketData().eventTypeIds()).containsExactly("1", "2");
        assertThat(config.marketData().marketTypeCodes()).containsExactly("MATCH_ODDS", "OVER_UNDER_25");
    }

    @Test
    void convertsLegacyBetfairConfigToEnabledBetfairExchange() throws Exception {
        BetxConfig config = mapper.readValue("""
            betfair:
              username: user
              password: password
              app_key: app-key
              country: romania
            """, BetxConfig.class);

        assertThat(config.enabledExchanges()).singleElement().satisfies(exchange -> {
            assertThat(exchange.name()).isEqualTo("betfair");
            assertThat(exchange.betfair().country()).isEqualTo(BetfairCountry.ROMANIA);
        });
    }

    @Test
    void loadFailsWhenConfigFileDoesNotExist() {
        ConfigPath path = new ConfigPath(tempDir.resolve("missing.yml"));

        assertThatThrownBy(() -> repository.load(path))
            .isInstanceOf(ConfigException.class)
            .hasMessage("Configuration file not found: " + path.value());
    }

    @Test
    void loadFailsWhenConfigFileIsInvalidYaml() throws Exception {
        Path file = tempDir.resolve("betx.yml");
        Files.writeString(file, "app: [");

        assertThatThrownBy(() -> repository.load(new ConfigPath(file)))
            .isInstanceOf(ConfigException.class)
            .hasMessage("Configuration file is invalid: " + file);
    }

    @Test
    void writeDefaultDoesNotOverwriteExistingConfigWithoutForce() throws Exception {
        Path file = tempDir.resolve("betx.yml");
        Files.writeString(file, "existing: true\n");

        boolean written = repository.writeDefault(new ConfigPath(file), false);

        assertThat(written).isFalse();
        assertThat(Files.readString(file)).isEqualTo("existing: true\n");
    }

    @Test
    void writeDefaultOverwritesExistingConfigWithForce() throws Exception {
        Path file = tempDir.resolve("betx.yml");
        Files.writeString(file, "existing: true\n");

        boolean written = repository.writeDefault(new ConfigPath(file), true);

        assertThat(written).isTrue();
        assertThat(Files.readString(file)).contains("exchanges:");
        assertThat(Files.readString(file)).contains("market_data:");
    }

    @Test
    void saveTelegramFieldsPreservesExistingTelegramFields() throws Exception {
        Path file = tempDir.resolve("betx.yml");
        Files.writeString(file, """
            telegram:
              enabled: true
              bot_username: existing_bot
            app:
              mode: dry-run
            """);

        repository.saveTelegramFields(new ConfigPath(file), java.util.Map.of("chat_id", "12345"));

        BetxConfig config = repository.load(new ConfigPath(file));
        assertThat(config.telegram().enabled()).isTrue();
        assertThat(config.telegram().botUsername()).isEqualTo("existing_bot");
        assertThat(config.telegram().chatId()).isEqualTo("12345");
    }
}
