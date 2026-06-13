package com.betx.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.BacktestResult;
import com.betx.application.BacktestTrade;
import com.betx.application.RunBacktestService;
import com.betx.application.port.out.BetxConfigRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.signal.BetSide;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BacktestCommandTest {
    @Test
    void printsCompactBacktestSummary() {
        RecordingRunBacktestService service = new RecordingRunBacktestService(result());
        BacktestCommand command = new BacktestCommand(service);
        command.configPath = Path.of("betx.yml");
        command.inputPath = Path.of("history.csv");

        String output = captureOutput(command::run);

        assertThat(service.configPaths()).containsExactly(new ConfigPath(Path.of("betx.yml")));
        assertThat(service.inputPaths()).containsExactly(Path.of("history.csv"));
        assertThat(output)
            .contains("Backtest complete | rows=4 | runnersAnalyzed=4 | trades=2 | wins=1 | losses=1")
            .contains("Performance | staked=10 | pnl=2.50 | roi=25.00% | strikeRate=50.00% | maxDrawdown=5.00")
            .contains("Top trades")
            .contains("TRADE | WIN | observedAt=2026-06-01T10:01:00Z | event=Team A v Team B | runner=Team A | odds=2.5 | stake=5 | pnl=7.50")
            .contains("Bottom trades")
            .contains("TRADE | LOSE | observedAt=2026-06-01T10:03:00Z | event=Team C v Team D | runner=Team C | odds=3 | stake=5 | pnl=-5");
    }

    private static BacktestResult result() {
        List<BacktestTrade> trades = List.of(
            new BacktestTrade(
                Instant.parse("2026-06-01T10:01:00Z"),
                "betfair",
                "1.1",
                "Team A v Team B",
                "Match Odds",
                42L,
                "Team A",
                BetSide.BACK,
                new BigDecimal("2.50"),
                new BigDecimal("5"),
                com.betx.application.BacktestOutcome.WIN,
                new BigDecimal("7.50")
            ),
            new BacktestTrade(
                Instant.parse("2026-06-01T10:03:00Z"),
                "betfair",
                "1.2",
                "Team C v Team D",
                "Match Odds",
                43L,
                "Team C",
                BetSide.BACK,
                new BigDecimal("3.00"),
                new BigDecimal("5"),
                com.betx.application.BacktestOutcome.LOSE,
                new BigDecimal("-5")
            )
        );
        return BacktestResult.from(4, 4, trades);
    }

    private static String captureOutput(Runnable runnable) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            runnable.run();
            return output.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(originalOut);
        }
    }

    private static final class RecordingRunBacktestService extends RunBacktestService {
        private final BacktestResult result;
        private final List<ConfigPath> configPaths = new java.util.ArrayList<>();
        private final List<Path> inputPaths = new java.util.ArrayList<>();

        private RecordingRunBacktestService(BacktestResult result) {
            super(new StaticConfigRepository(), ignored -> List.of());
            this.result = result;
        }

        @Override
        public BacktestResult run(ConfigPath configPath, Path inputPath) {
            configPaths.add(configPath);
            inputPaths.add(inputPath);
            return result;
        }

        private List<ConfigPath> configPaths() {
            return configPaths;
        }

        private List<Path> inputPaths() {
            return inputPaths;
        }
    }

    private record StaticConfigRepository() implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            return BetxConfig.defaults();
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
