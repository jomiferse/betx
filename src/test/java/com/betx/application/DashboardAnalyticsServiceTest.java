package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.config.ConfigPath;
import com.betx.domain.order.BetIntentStage;
import com.betx.domain.order.BetSettlementResult;
import com.betx.domain.order.SelectionSide;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardAnalyticsServiceTest {
    @Test
    void mapsReportSummaryForDashboardKpis() {
        DashboardAnalyticsService service = service(report());

        DashboardSummaryView summary = service.summary();

        assertThat(summary.totalPnl()).isEqualByComparingTo("9.95");
        assertThat(summary.roi()).isEqualByComparingTo("331.67");
        assertThat(summary.totalTrades()).isEqualTo(4);
        assertThat(summary.wonTrades()).isEqualTo(2);
        assertThat(summary.lostTrades()).isEqualTo(1);
        assertThat(summary.winRate()).isEqualByComparingTo("66.67");
        assertThat(summary.totalStaked()).isEqualByComparingTo("3.00");
        assertThat(summary.maxDrawdown()).isEqualByComparingTo("5.00");
        assertThat(summary.openExposure()).isEqualByComparingTo("1.00");
        assertThat(summary.lastUpdatedAt()).isEqualTo(Instant.parse("2026-06-25T10:56:38Z"));
    }

    @Test
    void buildsEquityDailyBreakdownAndRecentTradeViews() {
        DashboardAnalyticsService service = service(report());

        assertThat(service.equity("ALL")).extracting(DashboardEquityPoint::cumulativePnl)
            .containsExactly(new BigDecimal("3.00"), new BigDecimal("-2.00"), new BigDecimal("9.95"));
        assertThat(service.equity("ALL")).extracting(DashboardEquityPoint::pnl)
            .containsExactly(new BigDecimal("3.00"), new BigDecimal("-5.00"), new BigDecimal("11.95"));
        assertThat(service.equity("ALL")).extracting(DashboardEquityPoint::sequenceNumber)
            .containsExactly(1L, 2L, 3L);
        assertThat(service.equity("ALL").get(1).drawdown()).isEqualByComparingTo("5.00");
        assertThat(service.dailyPnl("ALL")).extracting(DashboardDailyPnlPoint::day)
            .containsExactly(LocalDate.parse("2026-06-23"), LocalDate.parse("2026-06-24"));
        assertThat(service.dailyPnl("ALL")).extracting(DashboardDailyPnlPoint::roi)
            .containsExactly(new BigDecimal("-100.00"), new BigDecimal("1195.00"));
        assertThat(service.strategyBreakdown("ALL")).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("value-football");
            assertThat(item.pnl()).isEqualByComparingTo("9.95");
            assertThat(item.roi()).isEqualByComparingTo("331.67");
        });
        assertThat(service.trades("ALL", 0, 25, null, null, null, null, "timestamp", "desc").items())
            .extracting(DashboardTradeView::marketName)
            .containsExactly("Open Market", "Late Market", "Middle Market", "Early Market");
        assertThat(service.trades("ALL", 0, 25, null, null, null, null, "timestamp", "desc").items().getFirst()).satisfies(trade -> {
            assertThat(trade.selection()).isEqualTo("Open Selection");
            assertThat(trade.strategy()).isEqualTo("value-football");
            assertThat(trade.status()).isEqualTo("EXECUTED");
            assertThat(trade.pnl()).isNull();
        });
    }

    @Test
    void recalculatesDashboardMetricsAfterFilteringByRange() {
        DashboardAnalyticsService service = service(report());

        DashboardSummaryView summary = service.summary("1D");

        assertThat(summary.totalTrades()).isEqualTo(2);
        assertThat(summary.wonTrades()).isEqualTo(1);
        assertThat(summary.lostTrades()).isZero();
        assertThat(summary.totalPnl()).isEqualByComparingTo("11.95");
        assertThat(summary.totalStaked()).isEqualByComparingTo("1.00");
        assertThat(summary.openExposure()).isEqualByComparingTo("1.00");
        assertThat(service.equity("1D")).extracting(DashboardEquityPoint::cumulativePnl)
            .containsExactly(new BigDecimal("11.95"));
        assertThat(service.dailyPnl("1D")).singleElement().satisfies(day -> {
            assertThat(day.day()).isEqualTo(LocalDate.parse("2026-06-24"));
            assertThat(day.trades()).isEqualTo(1);
            assertThat(day.wonTrades()).isEqualTo(1);
            assertThat(day.lostTrades()).isZero();
            assertThat(day.totalStake()).isEqualByComparingTo("1.00");
            assertThat(day.roi()).isEqualByComparingTo("1195.00");
        });
    }

    @Test
    void paginatesAndFiltersTrades() {
        DashboardAnalyticsService service = service(report());

        DashboardTradePage page = service.trades("ALL", 0, 1, "SETTLED", "WIN", "value-football", "Late", "pnl", "desc");

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(trade -> {
            assertThat(trade.marketName()).isEqualTo("Late Market");
            assertThat(trade.result()).isEqualTo("WIN");
            assertThat(trade.pnl()).isEqualByComparingTo("11.95");
        });
    }

    private static DashboardAnalyticsService service(RealBettingReport report) {
        return new DashboardAnalyticsService(new StaticReportUseCase(report), new BetxInterfaceProperties(Path.of("betx.yml")));
    }

    private static RealBettingReport report() {
        List<RealBettingReportRow> rows = List.of(
            settled("early", "Early Market", "Early Selection", "3.00", "2026-06-23T10:00:00Z"),
            settled("middle", "Middle Market", "Middle Selection", "-5.00", "2026-06-23T11:00:00Z"),
            settled("late", "Late Market", "Late Selection", "11.95", "2026-06-24T12:00:00Z"),
            row("open", "Open Market", "Open Selection", BetIntentStage.EXECUTED, null, null, "2026-06-25T10:56:38Z")
        );
        return new RealBettingReport(
            Instant.parse("2026-06-23T10:00:00Z"),
            Instant.parse("2026-06-25T10:56:38Z"),
            "sample",
            81,
            15,
            38,
            43,
            1,
            new BigDecimal("81.00"),
            new BigDecimal("15.00"),
            new BigDecimal("9.95"),
            new BigDecimal("12.28"),
            new BigDecimal("46.91"),
            new BigDecimal("2.10"),
            null,
            null,
            new BigDecimal("9.95"),
            new BigDecimal("12.95"),
            new BigDecimal("5.00"),
            new BigDecimal("3.00"),
            null,
            false,
            3,
            4,
            List.of(),
            List.of(),
            List.of(),
            List.of(new RealBettingReportSegment("value-football", 81, 38, 43, 0, new BigDecimal("81.00"), new BigDecimal("9.95"), new BigDecimal("12.28"), new BigDecimal("46.91"))),
            List.of(),
            List.of(
                new RealBettingReportDailyPnl(LocalDate.parse("2026-06-23"), 2, new BigDecimal("-2.00")),
                new RealBettingReportDailyPnl(LocalDate.parse("2026-06-24"), 1, new BigDecimal("11.95"))
            ),
            List.of(),
            rows,
            List.of(),
            List.of()
        );
    }

    private static RealBettingReportRow settled(String id, String market, String selection, String pnl, String timestamp) {
        BigDecimal profitLoss = new BigDecimal(pnl);
        BetSettlementResult result = profitLoss.signum() >= 0 ? BetSettlementResult.WIN : BetSettlementResult.LOSE;
        return row(id, market, selection, BetIntentStage.SETTLED, result, profitLoss, timestamp);
    }

    private static RealBettingReportRow row(
        String id,
        String market,
        String selection,
        BetIntentStage stage,
        BetSettlementResult result,
        BigDecimal pnl,
        String timestamp
    ) {
        return new RealBettingReportRow(
            id,
            "betfair",
            "market-" + id,
            10L,
            market + " Event",
            market,
            selection,
            SelectionSide.UNKNOWN,
            "La Liga",
            "value-football",
            new BigDecimal("2.00"),
            BigDecimal.ONE,
            null,
            null,
            null,
            result,
            pnl,
            stage,
            Instant.parse(timestamp),
            result == null ? null : Instant.parse(timestamp),
            Instant.parse(timestamp)
        );
    }

    private record StaticReportUseCase(RealBettingReport report) implements GenerateRealBettingReportUseCase {
        @Override
        public RealBettingReport generate(ConfigPath configPath) {
            return report;
        }
    }
}
