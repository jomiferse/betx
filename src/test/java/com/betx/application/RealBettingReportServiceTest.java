package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.application.port.out.BetxConfigRepository;
import com.betx.application.port.out.RealBettingReportRepository;
import com.betx.domain.config.BetxConfig;
import com.betx.domain.config.ConfigPath;
import com.betx.domain.config.StorageConfig;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealBettingReportServiceTest {
    @Test
    void calculatesRoiFromRealizedProfitLossAndDifferentStakes() {
        RealBettingReport report = service(List.of(
            settled("win-1", "Team A", "La Liga", "2.50", "2.00", "3.00", BetSettlementResult.WIN, "2026-06-01T10:00:00Z"),
            settled("lose-1", "Draw", "La Liga", "3.20", "8.00", "-8.00", BetSettlementResult.LOSE, "2026-06-02T10:00:00Z")
        )).generate(new ConfigPath(Path.of("betx.yml")));

        assertThat(report.settledBets()).isEqualTo(2);
        assertThat(report.wins()).isEqualTo(1);
        assertThat(report.losses()).isEqualTo(1);
        assertThat(report.totalStaked()).isEqualByComparingTo("10.00");
        assertThat(report.netRealizedPnl()).isEqualByComparingTo("-5.00");
        assertThat(report.roiPercent()).isEqualByComparingTo("-50.00");
        assertThat(report.winRatePercent()).isEqualByComparingTo("50.00");
        assertThat(report.averageExecutedOdds()).isEqualByComparingTo("2.85");
    }

    @Test
    void handlesAllLosingAndAllWinningSamples() {
        RealBettingReport losing = service(List.of(
            settled("lose-1", "Team A", "N/A", "2.00", "5.00", "-5.00", BetSettlementResult.LOSE, "2026-06-01T10:00:00Z"),
            settled("lose-2", "Team B", "N/A", "3.00", "5.00", "-5.00", BetSettlementResult.LOSE, "2026-06-02T10:00:00Z")
        )).generate(new ConfigPath(Path.of("betx.yml")));
        RealBettingReport winning = service(List.of(
            settled("win-1", "Team A", "N/A", "2.00", "5.00", "5.00", BetSettlementResult.WIN, "2026-06-01T10:00:00Z"),
            settled("win-2", "Team B", "N/A", "3.00", "5.00", "10.00", BetSettlementResult.WIN, "2026-06-02T10:00:00Z")
        )).generate(new ConfigPath(Path.of("betx.yml")));

        assertThat(losing.winRatePercent()).isEqualByComparingTo("0.00");
        assertThat(losing.maxLosingStreak()).isEqualTo(2);
        assertThat(winning.winRatePercent()).isEqualByComparingTo("100.00");
        assertThat(winning.maxWinningStreak()).isEqualTo(2);
    }

    @Test
    void separatesOpenExposureFromSettledPerformance() {
        RealBettingReport report = service(List.of(
            settled("win-1", "Team A", "La Liga", "2.50", "5.00", "7.50", BetSettlementResult.WIN, "2026-06-01T10:00:00Z"),
            open("open-1", "Draw", "3.10", "4.00", "2026-06-02T09:00:00Z"),
            row("null-pnl", BetIntentStage.SETTLED, BetSettlementResult.WIN, "Away", "La Liga", "2.20", "6.00", null, null, null, null, "2026-06-03T10:00:00Z")
        )).generate(new ConfigPath(Path.of("betx.yml")));

        assertThat(report.settledBets()).isEqualTo(1);
        assertThat(report.openBets()).isEqualTo(1);
        assertThat(report.openExposure()).isEqualByComparingTo("4.00");
        assertThat(report.netRealizedPnl()).isEqualByComparingTo("7.50");
    }

    @Test
    void handlesEmptyDatabaseAndDivisionByZero() {
        RealBettingReport report = service(List.of()).generate(new ConfigPath(Path.of("betx.yml")));

        assertThat(report.settledBets()).isZero();
        assertThat(report.openBets()).isZero();
        assertThat(report.totalStaked()).isEqualByComparingTo("0.00");
        assertThat(report.roiPercent()).isEqualByComparingTo("0.00");
        assertThat(report.winRatePercent()).isEqualByComparingTo("0.00");
        assertThat(report.periodLabel()).isEqualTo("N/A");
    }

    @Test
    void ignoresOpenBetsWithoutStakeWhenCalculatingExposure() {
        RealBettingReport report = service(List.of(new RealBettingReportRow(
            "open-without-stake",
            "betfair",
            "market-open",
            42L,
            "Team A v Team B",
            "Match Odds",
            "HOME",
            SelectionSide.UNKNOWN,
            "N/A",
            "N/A",
            new BigDecimal("2.50"),
            null,
            null,
            null,
            null,
            null,
            null,
            BetIntentStage.EXECUTED,
            Instant.parse("2026-06-01T10:00:00Z"),
            null,
            Instant.parse("2026-06-01T10:01:00Z")
        ))).generate(new ConfigPath(Path.of("betx.yml")));

        assertThat(report.openBets()).isEqualTo(1);
        assertThat(report.openExposure()).isEqualByComparingTo("0.00");
    }

    @Test
    void ignoresNullEffectiveBalancesAndKeepsLatestAvailableBalances() {
        RealBettingReport report = service(List.of(
            row("first", BetIntentStage.EXECUTED, null, "Team A", "N/A", "2.00", "5.00", null, "100.00", "95.00", "2026-06-01T09:00:00Z", "2026-06-01T09:00:00Z"),
            row("second", BetIntentStage.EXECUTED, null, "Team B", "N/A", "2.10", "5.00", null, "110.00", null, "2026-06-02T09:00:00Z", "2026-06-02T09:00:00Z"),
            row("third", BetIntentStage.EXECUTED, null, "Team C", "N/A", "2.20", "5.00", null, null, "101.00", "2026-06-03T09:00:00Z", "2026-06-03T09:00:00Z")
        )).generate(new ConfigPath(Path.of("betx.yml")));

        assertThat(report.exchangeAvailableBalance()).isEqualByComparingTo("110.00");
        assertThat(report.operationalAvailableBalance()).isEqualByComparingTo("101.00");
    }

    @Test
    void calculatesRealizedEquityPeakDrawdownAndCurrentDrawdownFromSettledChronology() {
        RealBettingReport report = service(List.of(
            settled("third-input-first", "Team C", "N/A", "2.00", "5.00", "4.00", BetSettlementResult.WIN, "2026-06-03T10:00:00Z"),
            settled("first-input-second", "Team A", "N/A", "2.00", "5.00", "10.00", BetSettlementResult.WIN, "2026-06-01T10:00:00Z"),
            settled("second-input-third", "Team B", "N/A", "2.00", "5.00", "-7.00", BetSettlementResult.LOSE, "2026-06-02T10:00:00Z"),
            settled("fourth", "Team D", "N/A", "2.00", "5.00", "-3.00", BetSettlementResult.LOSE, "2026-06-04T10:00:00Z")
        )).generate(new ConfigPath(Path.of("betx.yml")));

        assertThat(report.realizedEquity()).isEqualByComparingTo("4.00");
        assertThat(report.peakRealizedEquity()).isEqualByComparingTo("10.00");
        assertThat(report.maximumDrawdown()).isEqualByComparingTo("7.00");
        assertThat(report.currentDrawdown()).isEqualByComparingTo("6.00");
        assertThat(report.realizedEquityUsesInitialReference()).isFalse();
        assertThat(report.limitations())
            .contains("No reliable initial balance was found; the performance curve starts from cumulative realized PnL at 0.00 €.");
    }

    @Test
    void usesInitialReferenceBalanceWhenEarliestSettledRowHasReliableBalance() {
        RealBettingReport report = service(List.of(
            row("win-1", BetIntentStage.SETTLED, BetSettlementResult.WIN, "Team A", "N/A", "2.00", "5.00", "5.00", "105.00", "105.00", "2026-06-01T09:00:00Z", "2026-06-01T10:00:00Z"),
            settled("lose-1", "Team B", "N/A", "2.00", "5.00", "-5.00", BetSettlementResult.LOSE, "2026-06-02T10:00:00Z")
        )).generate(new ConfigPath(Path.of("betx.yml")));

        assertThat(report.realizedEquityUsesInitialReference()).isTrue();
        assertThat(report.initialReferenceBalance()).isEqualByComparingTo("100.00");
        assertThat(report.realizedEquity()).isEqualByComparingTo("100.00");
        assertThat(report.peakRealizedEquity()).isEqualByComparingTo("105.00");
        assertThat(report.currentDrawdown()).isEqualByComparingTo("5.00");
    }

    @Test
    void groupsSettledPerformanceBySelectionSideRunnerCompetitionStrategyOddsBandAndDay() {
        RealBettingReport report = service(List.of(
            settled("home", "HOME", SelectionSide.HOME, "La Liga", "value-football", "1.80", "5.00", "4.00", BetSettlementResult.WIN, "2026-06-01T10:00:00Z"),
            settled("draw", "DRAW", SelectionSide.DRAW, "N/A", "value-football", "2.50", "5.00", "-5.00", BetSettlementResult.LOSE, "2026-06-01T11:00:00Z"),
            settled("away", "AWAY", SelectionSide.AWAY, "Premier League", "value-football", "3.50", "5.00", "0.00", BetSettlementResult.VOID, "2026-06-02T11:00:00Z"),
            settled("five", "AWAY", SelectionSide.AWAY, "Premier League", "value-football", "5.20", "5.00", "21.00", BetSettlementResult.WIN, "2026-06-02T12:00:00Z"),
            settled("four", "HOME", SelectionSide.HOME, "La Liga", "value-football", "4.30", "5.00", "-5.00", BetSettlementResult.LOSE, "2026-06-03T12:00:00Z")
        )).generate(new ConfigPath(Path.of("betx.yml")));

        assertThat(report.voidsCancelled()).isEqualTo(1);
        assertThat(report.selectionSideSegments()).extracting(RealBettingReportSegment::name)
            .containsExactly("AWAY", "HOME", "DRAW");
        assertThat(report.runnerSegments()).extracting(RealBettingReportSegment::name)
            .containsExactly("AWAY", "HOME", "DRAW");
        assertThat(report.competitionSegments()).extracting(RealBettingReportSegment::name)
            .contains("La Liga", "Premier League", "N/A");
        assertThat(report.strategySegments()).extracting(RealBettingReportSegment::name)
            .containsExactly("value-football");
        assertThat(report.oddsBandSegments()).extracting(RealBettingReportSegment::name)
            .containsExactly("1.00-1.99", "2.00-2.99", "3.00-3.99", "4.00-4.99", "5.00+");
        assertThat(report.dailyPnl()).extracting(RealBettingReportDailyPnl::day)
            .containsExactly(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-02"), LocalDate.parse("2026-06-03"));
        assertThat(report.dailyPnl().get(1).pnl()).isEqualByComparingTo("21.00");
    }

    @Test
    void calculatesRollingWindowsFromMostRecentSettledBets() {
        List<RealBettingReportRow> rows = new java.util.ArrayList<>();
        for (int index = 1; index <= 60; index++) {
            rows.add(settled(
                "bet-" + index,
                "Team " + index,
                index % 3 == 0 ? SelectionSide.AWAY : SelectionSide.HOME,
                "La Liga",
                "value-football",
                "2.00",
                "10.00",
                index > 35 ? "5.00" : "-10.00",
                index > 35 ? BetSettlementResult.WIN : BetSettlementResult.LOSE,
                Instant.parse("2026-06-01T10:00:00Z").plusSeconds(index * 60L).toString()
            ));
        }

        RealBettingReport report = service(rows).generate(new ConfigPath(Path.of("betx.yml")));

        assertThat(report.rollingWindows()).extracting(RealBettingReportRollingWindow::requestedSize)
            .containsExactly(25, 50, 100);
        assertThat(report.rollingWindows().get(0)).satisfies(window -> {
            assertThat(window.availableSettledBets()).isEqualTo(25);
            assertThat(window.wins()).isEqualTo(25);
            assertThat(window.losses()).isZero();
            assertThat(window.totalStaked()).isEqualByComparingTo("250.00");
            assertThat(window.netRealizedPnl()).isEqualByComparingTo("125.00");
            assertThat(window.roiPercent()).isEqualByComparingTo("50.00");
            assertThat(window.maximumDrawdown()).isEqualByComparingTo("0.00");
            assertThat(window.maxWinningStreak()).isEqualTo(25);
        });
        assertThat(report.rollingWindows().get(2).availableSettledBets()).isEqualTo(60);
    }

    private static RealBettingReportService service(List<RealBettingReportRow> rows) {
        return new RealBettingReportService(new StaticConfigRepository(), new StaticReportRepository(rows));
    }

    private static RealBettingReportRow settled(
        String id,
        String runner,
        String competition,
        String odds,
        String stake,
        String pnl,
        BetSettlementResult settlement,
        String settledAt
    ) {
        return row(id, BetIntentStage.SETTLED, settlement, runner, competition, odds, stake, pnl, null, null, null, settledAt);
    }

    private static RealBettingReportRow open(String id, String runner, String odds, String stake, String updatedAt) {
        return row(id, BetIntentStage.EXECUTED, null, runner, "N/A", odds, stake, null, null, null, null, updatedAt);
    }

    private static RealBettingReportRow row(
        String id,
        BetIntentStage stage,
        BetSettlementResult settlement,
        String runner,
        String competition,
        String odds,
        String stake,
        String pnl,
        String availableBalance,
        String effectiveAvailableBalance,
        String balanceSnapshotAt,
        String settledAt
    ) {
        Instant timestamp = Instant.parse(settledAt == null ? "2026-06-01T08:00:00Z" : settledAt);
        return new RealBettingReportRow(
            id,
            "betfair",
            "market-" + id,
            42L,
            "Team A v Team B",
            "Match Odds",
            runner,
            SelectionSide.UNKNOWN,
            competition,
            "N/A",
            new BigDecimal(odds),
            new BigDecimal(stake),
            decimal(availableBalance),
            decimal(effectiveAvailableBalance),
            balanceSnapshotAt == null ? null : Instant.parse(balanceSnapshotAt),
            settlement,
            decimal(pnl),
            stage,
            timestamp.minusSeconds(60),
            settledAt == null ? null : Instant.parse(settledAt),
            timestamp
        );
    }

    private static RealBettingReportRow settled(
        String id,
        String runner,
        SelectionSide selectionSide,
        String competition,
        String strategy,
        String odds,
        String stake,
        String pnl,
        BetSettlementResult settlement,
        String settledAt
    ) {
        Instant timestamp = Instant.parse(settledAt);
        return new RealBettingReportRow(
            id,
            "betfair",
            "market-" + id,
            42L,
            "Team A v Team B",
            "Match Odds",
            runner,
            selectionSide,
            competition,
            strategy,
            new BigDecimal(odds),
            new BigDecimal(stake),
            null,
            null,
            null,
            settlement,
            decimal(pnl),
            BetIntentStage.SETTLED,
            timestamp.minusSeconds(60),
            timestamp,
            timestamp
        );
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static final class StaticConfigRepository implements BetxConfigRepository {
        @Override
        public BetxConfig load(ConfigPath path) {
            BetxConfig defaults = BetxConfig.defaults();
            return new BetxConfig(
                defaults.app(),
                defaults.telegram(),
                defaults.betfair(),
                defaults.exchanges(),
                defaults.marketData(),
                new StorageConfig(defaults.storage().type(), "data.db"),
                defaults.risk(),
                defaults.strategies(),
                defaults.ml()
            );
        }

        @Override
        public boolean writeDefault(ConfigPath path, boolean force) {
            return false;
        }

        @Override
        public void saveTelegramFields(ConfigPath path, Map<String, Object> fields) {
        }
    }

    private record StaticReportRepository(List<RealBettingReportRow> rows) implements RealBettingReportRepository {
        @Override
        public List<RealBettingReportRow> listReportRows(String databasePath) {
            return rows;
        }
    }
}
