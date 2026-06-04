package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betx.application.port.out.BetfairGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.betfair.BetfairCountry;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairMarketQuery;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BetfairIntegrationServiceTest {
    private static final ConfigPath CONFIG_PATH = new ConfigPath(Path.of("betx.yml"));

    @Test
    void authenticatesWithBetfairExchangeCredentialsWhenConfigured() {
        RecordingBetfairGateway gateway = new RecordingBetfairGateway();
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(
            new ExchangeConfig("betfair", true, new BetfairConfig("exchange-user", "exchange-password", "exchange-app", BetfairCountry.ITALY))
        ));
        BetfairIntegrationService service = new BetfairIntegrationService(new StaticConfigRepository(config), gateway);

        service.authenticate(CONFIG_PATH);

        assertThat(gateway.credentials()).isEqualTo(
            new BetfairCredentials("exchange-user", "exchange-password", "exchange-app", BetfairCountry.ITALY)
        );
    }

    @Test
    void authenticatesWithLegacyBetfairCredentialsWhenNoExchangeExists() {
        RecordingBetfairGateway gateway = new RecordingBetfairGateway();
        BetxConfig defaults = BetxConfig.defaults();
        BetfairConfig legacy = new BetfairConfig("legacy-user", "legacy-password", "legacy-app", BetfairCountry.ROMANIA);
        BetxConfig config = new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            legacy,
            List.of(),
            defaults.marketData(),
            defaults.storage(),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml()
        );
        BetfairIntegrationService service = new BetfairIntegrationService(new StaticConfigRepository(config), gateway);

        service.authenticate(CONFIG_PATH);

        assertThat(gateway.credentials()).isEqualTo(
            new BetfairCredentials("legacy-user", "legacy-password", "legacy-app", BetfairCountry.ROMANIA)
        );
    }

    @Test
    void failsWhenBetfairCredentialsAreMissing() {
        BetfairIntegrationService service = new BetfairIntegrationService(
            new StaticConfigRepository(BetxConfig.defaults()),
            new RecordingBetfairGateway()
        );

        assertThatThrownBy(() -> service.authenticate(CONFIG_PATH))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Betfair credentials are missing from betx.yml.");
    }

    @Test
    void listsMarketsWithFreshSessionAndQuery() {
        RecordingBetfairGateway gateway = new RecordingBetfairGateway();
        BetxConfig config = BetxConfig.defaults().withExchanges(List.of(
            new ExchangeConfig("betfair", true, new BetfairConfig("user", "password", "app-key"))
        ));
        BetfairIntegrationService service = new BetfairIntegrationService(new StaticConfigRepository(config), gateway);

        service.listMarkets(CONFIG_PATH, "1", 7);

        assertThat(gateway.query()).isEqualTo(new BetfairMarketQuery(List.of("1"), List.of(), 7));
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

    private static final class RecordingBetfairGateway implements BetfairGateway {
        private BetfairCredentials credentials;
        private BetfairMarketQuery query;

        @Override
        public BetfairSession login(BetfairCredentials credentials) {
            this.credentials = credentials;
            return new BetfairSession("session-token", credentials.appKey());
        }

        @Override
        public List<BetfairMarketCatalogue> listMarketCatalogue(BetfairSession session, BetfairMarketQuery query) {
            this.query = query;
            return List.of();
        }

        @Override
        public List<BetfairMarketBook> listMarketBook(BetfairSession session, List<String> marketIds) {
            return List.of();
        }

        private BetfairCredentials credentials() {
            return credentials;
        }

        private BetfairMarketQuery query() {
            return query;
        }
    }
}
