package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.PaperTradeRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.PaperConfig;
import com.betx.domain.config.PaperReadinessGateConfig;
import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaperReadinessServiceTest {
    private static final ConfigPath CONFIG_PATH = new ConfigPath(Path.of("betx.yml"));

    @Test
    void returnsDisabledWhenGateIsDisabled() {
        PaperReadinessResult result = service(config(false, 100, "CANDIDATE_EDGE", "0.01", "0.00", 100, "0.00", true), List.of())
            .evaluate(CONFIG_PATH, "value-football-draw-only");

        assertThat(result.status()).isEqualTo(PaperReadinessStatus.DISABLED);
        assertThat(result.reasons()).containsExactly("Paper readiness gate is disabled.");
    }

    @Test
    void reportsInsufficientDataBelowMinimumSettledTrades() {
        PaperReadinessResult result = service(config(true, 3, "CANDIDATE_EDGE", "0.01", "0.00", 3, "0.00", true), List.of(
                settled("1", BacktestOutcome.WIN, "3.00", "2.90", "9.50", "2026-06-01T10:00:00Z"),
                settled("2", BacktestOutcome.LOSE, "3.00", "2.90", "-5.00", "2026-06-02T10:00:00Z")
            ))
            .evaluate(CONFIG_PATH, "value-football-draw-only");

        assertThat(result.status()).isEqualTo(PaperReadinessStatus.INSUFFICIENT_DATA);
        assertThat(result.settledTrades()).isEqualTo(2);
        assertThat(result.reasons()).contains("Minimum settled trades not reached.");
    }

    @Test
    void reportsNotReadyWhenExecutableRoiIsBelowThreshold() {
        PaperReadinessResult result = service(config(true, 2, "HISTORICAL_CANDIDATE", "0.01", "0.00", 2, "0.00", true), List.of(
                settled("1", BacktestOutcome.WIN, "2.10", null, "5.50", "2026-06-01T10:00:00Z"),
                settled("2", BacktestOutcome.LOSE, "2.10", null, "-5.50", "2026-06-02T10:00:00Z")
            ))
            .evaluate(CONFIG_PATH, "value-football-draw-only");

        assertThat(result.status()).isEqualTo(PaperReadinessStatus.NOT_READY);
        assertThat(result.reasons()).contains("Executable ROI is below the configured threshold.");
    }

    @Test
    void reportsNotReadyWhenMedianClvIsBelowThreshold() {
        PaperReadinessResult result = service(config(true, 2, "CANDIDATE_EDGE", "0.00", "0.02", 2, "0.00", true), List.of(
                settled("1", BacktestOutcome.WIN, "3.00", "2.99", "10.00", "2026-06-01T10:00:00Z"),
                settled("2", BacktestOutcome.WIN, "3.00", "2.99", "10.00", "2026-06-02T10:00:00Z")
            ))
            .evaluate(CONFIG_PATH, "value-football-draw-only");

        assertThat(result.status()).isEqualTo(PaperReadinessStatus.NOT_READY);
        assertThat(result.reasons()).contains("Median CLV is below the configured threshold.");
    }

    @Test
    void reportsNotReadyWhenRollingRoiIsBelowThreshold() {
        PaperReadinessResult result = service(config(true, 3, "CANDIDATE_EDGE", "0.00", "0.00", 2, "0.00", true), List.of(
                settled("1", BacktestOutcome.WIN, "3.00", "2.90", "10.00", "2026-06-01T10:00:00Z"),
                settled("2", BacktestOutcome.LOSE, "3.00", "2.90", "-5.00", "2026-06-02T10:00:00Z"),
                settled("3", BacktestOutcome.LOSE, "3.00", "2.90", "-5.00", "2026-06-03T10:00:00Z")
            ))
            .evaluate(CONFIG_PATH, "value-football-draw-only");

        assertThat(result.status()).isEqualTo(PaperReadinessStatus.NOT_READY);
        assertThat(result.rollingRoi()).isEqualByComparingTo("-100.00");
        assertThat(result.reasons()).contains("Rolling ROI is below the configured threshold.");
    }

    @Test
    void blocksWhenEvidenceReportsExecutionFailure() {
        PaperReadinessResult result = service(config(true, 2, "CANDIDATE_EDGE", "0.00", "0.00", 2, "0.00", true), List.of(
                settled("1", BacktestOutcome.WIN, "3.00", "2.90", "1.00", "2026-06-01T10:00:00Z"),
                settled("2", BacktestOutcome.LOSE, "3.00", "2.90", "-5.00", "2026-06-02T10:00:00Z")
            ))
            .evaluate(CONFIG_PATH, "value-football-draw-only");

        assertThat(result.status()).isEqualTo(PaperReadinessStatus.BLOCKED);
        assertThat(result.evidenceStatus()).isEqualTo("EXECUTION_FAILURE");
        assertThat(result.reasons()).contains("Execution evidence reports an execution failure.");
    }

    @Test
    void blocksWhenPersistedPaperTradeExecutionFailed() {
        PaperReadinessResult result = service(config(true, 1, "CANDIDATE_EDGE", "0.00", "0.00", 1, "0.00", true), List.of(
                settled("1", BacktestOutcome.WIN, "3.00", "2.90", "10.00", "2026-06-01T10:00:00Z"),
                executionFailed("failed")
            ))
            .evaluate(CONFIG_PATH, "value-football-draw-only");

        assertThat(result.status()).isEqualTo(PaperReadinessStatus.BLOCKED);
        assertThat(result.reasons()).contains("At least one persisted paper trade has execution failed.");
    }

    @Test
    void reportsReadyWhenAllThresholdsPass() {
        PaperReadinessResult result = service(config(true, 2, "CANDIDATE_EDGE", "0.01", "0.00", 2, "0.00", true), List.of(
                settled("1", BacktestOutcome.WIN, "3.00", "2.90", "10.00", "2026-06-01T10:00:00Z"),
                settled("2", BacktestOutcome.WIN, "3.00", "2.90", "10.00", "2026-06-02T10:00:00Z")
            ))
            .evaluate(CONFIG_PATH, "value-football-draw-only");

        assertThat(result.status()).isEqualTo(PaperReadinessStatus.READY);
        assertThat(result.reasons()).containsExactly("Paper readiness gate passed.");
    }

    private static PaperReadinessService service(BetxConfig config, List<PaperTrade> trades) {
        return new PaperReadinessService(new StaticConfigRepository(config), new RecordingPaperTradeRepository(trades));
    }

    private static BetxConfig config(
        boolean enabled,
        int minimumSettledTrades,
        String requiredEvidenceStatus,
        String minimumExecutableRoi,
        String minimumMedianClv,
        int rollingWindowSize,
        String minimumRollingRoi,
        boolean blockOnExecutionFailure
    ) {
        BetxConfig defaults = BetxConfig.defaults();
        return new BetxConfig(
            defaults.app(),
            defaults.telegram(),
            defaults.betfair(),
            defaults.exchanges(),
            defaults.marketData(),
            defaults.storage(),
            new PaperConfig(false, null, null, null, new PaperReadinessGateConfig(
                enabled,
                minimumSettledTrades,
                requiredEvidenceStatus,
                new BigDecimal(minimumExecutableRoi),
                new BigDecimal(minimumMedianClv),
                rollingWindowSize,
                new BigDecimal(minimumRollingRoi),
                blockOnExecutionFailure
            )),
            defaults.risk(),
            defaults.strategies(),
            defaults.ml(),
            defaults.intelligence(),
            defaults.resilience(),
            defaults.execution()
        );
    }

    private static PaperTrade settled(String id, BacktestOutcome outcome, String executionOdds, String closingOdds, String netPnl, String recommendedAt) {
        return new PaperTrade(
            id,
            "betfair",
            id,
            Long.parseLong(id.replaceAll("\\D", "")) + 10L,
            "Team A v Team B",
            "Match Odds",
            "SP1",
            Instant.parse("2026-06-10T18:00:00Z"),
            "The Draw",
            BetSide.BACK,
            PaperTradeStatus.SETTLED,
            Instant.parse(recommendedAt),
            new BigDecimal(executionOdds),
            new BigDecimal(executionOdds),
            Instant.parse(recommendedAt).plusSeconds(60),
            new BigDecimal(executionOdds),
            true,
            closingOdds == null ? null : Instant.parse(recommendedAt).plusSeconds(120),
            closingOdds == null ? null : new BigDecimal(closingOdds),
            Instant.parse(recommendedAt).plusSeconds(3600),
            outcome,
            BigDecimal.valueOf(5),
            new BigDecimal(netPnl),
            BigDecimal.ZERO,
            new BigDecimal(netPnl),
            closingOdds == null ? null : BacktestPaperTrade.clvRatio(new BigDecimal(executionOdds), new BigDecimal(closingOdds)),
            null,
            true
        );
    }

    private static PaperTrade executionFailed(String id) {
        return new PaperTrade(
            id,
            "betfair",
            id,
            99L,
            "Team A v Team B",
            "Match Odds",
            "SP1",
            Instant.parse("2026-06-10T18:00:00Z"),
            "The Draw",
            BetSide.BACK,
            PaperTradeStatus.EXECUTION_FAILED,
            Instant.parse("2026-06-01T10:00:00Z"),
            new BigDecimal("3.00"),
            new BigDecimal("3.00"),
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            BigDecimal.valueOf(5),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            null,
            true
        );
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
        public void saveTelegramFields(ConfigPath path, java.util.Map<String, Object> fields) {
        }
    }

    private static final class RecordingPaperTradeRepository implements PaperTradeRepository {
        private final List<PaperTrade> trades;

        private RecordingPaperTradeRepository(List<PaperTrade> trades) {
            this.trades = new ArrayList<>(trades);
        }

        @Override
        public Optional<PaperTrade> findByMarketSelection(String databasePath, String exchange, String marketId, long selectionId) {
            return Optional.empty();
        }

        @Override
        public void upsert(String databasePath, PaperTrade trade) {
            trades.add(trade);
        }

        @Override
        public List<PaperTrade> listAll(String databasePath) {
            return List.copyOf(trades);
        }
    }
}
