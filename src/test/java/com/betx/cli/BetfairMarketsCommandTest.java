package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BetfairIntegrationService;
import com.betx.application.port.out.BetfairGateway;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.betfair.BetfairConfig;
import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairMarketQuery;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.ExchangeConfig;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BetfairMarketsCommandTest {
    @Test
    void printsNoMarketsMessageWhenServiceReturnsEmptyList() {
        BetfairMarketsCommand command = command(List.of());

        String output = captureOutput(command::run);

        assertThat(output).isEqualTo("No active Betfair markets found.\n");
    }

    @Test
    void printsMarketsReturnedByService() {
        BetfairMarketsCommand command = command(List.of(
            new BetfairMarketCatalogue("1.234", "Match Odds", "Team A v Team B", "La Liga", Instant.parse("2026-06-01T18:00:00Z"))
        ));

        String output = captureOutput(command::run);

        assertThat(output).isEqualTo("1.234 | Match Odds | Team A v Team B\n");
    }

    private BetfairMarketsCommand command(List<BetfairMarketCatalogue> markets) {
        BetfairMarketsCommand command = new BetfairMarketsCommand(new BetfairIntegrationService(new StaticConfigRepository(), new StaticBetfairGateway(markets)));
        command.configPath = Path.of("custom.yml");
        command.eventTypeId = "1";
        command.maxResults = 5;
        return command;
    }

    private String captureOutput(Runnable runnable) {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            runnable.run();
        } finally {
            System.setOut(original);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private record StaticConfigRepository() implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return BetxConfig.defaults().withExchanges(List.of(new ExchangeConfig("betfair", true, new BetfairConfig("user", "password", "app-key"))));
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }

    private record StaticBetfairGateway(List<BetfairMarketCatalogue> markets) implements BetfairGateway {
        @Override
        public BetfairSession login(BetfairCredentials credentials) {
            return new BetfairSession("session-token", credentials.appKey());
        }

        @Override
        public List<BetfairMarketCatalogue> listMarketCatalogue(BetfairSession session, BetfairMarketQuery query) {
            return markets;
        }

        @Override
        public List<BetfairMarketBook> listMarketBook(BetfairSession session, List<String> marketIds) {
            return List.of();
        }
    }
}
