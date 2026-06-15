package com.betx.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.betx.domain.signal.BetSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestComparisonCsvExporterTest {
    @Test
    void exportsRankedStrategiesAndLeagueBreakdowns() {
        BacktestComparisonReport report = new BacktestComparisonReport(
            42L,
            new BigDecimal("0.05"),
            List.of(
                new BacktestStrategyReport("favorite", 1, BacktestResult.from(3, 9, List.of(
                    trade("SP1", BacktestOutcome.WIN, "2.00"),
                    trade("E0", BacktestOutcome.LOSE, "1.80")
                ))),
                new BacktestStrategyReport("draw", 2, BacktestResult.from(3, 9, List.of(
                    trade("SP1", BacktestOutcome.LOSE, "3.20")
                )))
            ),
            List.of(
                new BacktestStrategyLeagueReport("favorite", "E0", BacktestResult.from(3, 9, List.of(
                    trade("E0", BacktestOutcome.LOSE, "1.80")
                ))),
                new BacktestStrategyLeagueReport("favorite", "SP1", BacktestResult.from(3, 9, List.of(
                    trade("SP1", BacktestOutcome.WIN, "2.00")
                )))
            )
        );

        List<String> lines = new BacktestComparisonCsvExporter().lines(report);

        assertThat(lines).containsExactly(
            "section,rank,strategy,league,season,period,odds_source,pricing_mode,dataset_capability,markets,trades,wins,losses,gross_roi,net_roi,gross_pnl,commission,net_pnl,gross_drawdown,net_drawdown,strike_rate,random_seed,commission_rate,odds_slippage_rate,slippage_model",
            "strategy,1,favorite,,,,unknown,exchange,EXCHANGE_SNAPSHOTS,0,2,1,1,0.00,0.00,0.00,0.00,0.00,5.00,5.00,50.00,42,0.05,0,PROFIT_HAIRCUT",
            "strategy,2,draw,,,,unknown,exchange,EXCHANGE_SNAPSHOTS,0,1,0,1,-100.00,-100.00,-5.00,0.00,-5.00,5.00,5.00,0.00,42,0.05,0,PROFIT_HAIRCUT",
            "league,,favorite,E0,,,unknown,exchange,EXCHANGE_SNAPSHOTS,0,1,0,1,-100.00,-100.00,-5.00,0.00,-5.00,5.00,5.00,0.00,42,0.05,0,PROFIT_HAIRCUT",
            "league,,favorite,SP1,,,unknown,exchange,EXCHANGE_SNAPSHOTS,0,1,1,0,100.00,100.00,5.00,0.00,5.00,0.00,0.00,100.00,42,0.05,0,PROFIT_HAIRCUT"
        );
    }

    private static BacktestTrade trade(String competition, BacktestOutcome outcome, String odds) {
        BigDecimal price = new BigDecimal(odds);
        BigDecimal stake = new BigDecimal("5");
        BigDecimal profitLoss = outcome == BacktestOutcome.WIN
            ? stake.multiply(price.subtract(BigDecimal.ONE))
            : stake.negate();
        return new BacktestTrade(
            Instant.parse("2026-06-01T10:00:00Z"),
            "football-data",
            "1.1",
            "Team A v Team B",
            "Match Odds",
            1L,
            "Team A",
            BetSide.BACK,
            price,
            stake,
            outcome,
            profitLoss,
            competition,
            "Benchmark",
            null,
            BacktestRunnerType.HOME
        );
    }
}
