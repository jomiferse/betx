import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { HomePage, tooltipPositionWithinStage } from "./HomePage";

const hookState = vi.hoisted(() => ({
  value: {
    range: "30D",
    setRange: vi.fn(),
    tradeFilters: {
      page: 0,
      size: 25,
      status: "ALL",
      result: "ALL",
      strategy: "ALL",
      search: "",
      sort: "timestamp" as const,
      order: "desc" as const
    },
    setTradeFilters: vi.fn(),
    isLoading: false,
    isRefreshing: false,
    error: null as string | null,
    lastUpdatedAt: "2026-06-25T10:56:38Z",
    refresh: vi.fn(),
    data: {
      summary: {
        totalPnl: 9.95,
        roi: 12.28,
        totalTrades: 97,
        wonTrades: 38,
        lostTrades: 43,
        winRate: 46.91,
        totalStaked: 81,
        maxDrawdown: 5,
        openExposure: 15,
        lastUpdatedAt: "2026-06-25T10:56:38Z"
      },
      equity: [
        { timestamp: "2026-06-23T10:00:00Z", cumulativePnl: 3, equity: 3, drawdown: 0, pnl: 3, dailyPnl: 3, trades: 1, sequenceNumber: 1, cumulativeRoi: 300 },
        { timestamp: "2026-06-24T10:00:00Z", cumulativePnl: -2, equity: -2, drawdown: 5, pnl: -5, dailyPnl: -5, trades: 1, sequenceNumber: 2, cumulativeRoi: -100 },
        { timestamp: "2026-06-25T10:00:00Z", cumulativePnl: 9.95, equity: 9.95, drawdown: 0, pnl: 11.95, dailyPnl: 11.95, trades: 1, sequenceNumber: 3, cumulativeRoi: 331.67 }
      ],
      dailyPnl: [
        { day: "2026-06-24", trades: 5, wonTrades: 2, lostTrades: 3, totalStake: 5, pnl: -0.94, roi: -18.8 },
        { day: "2026-06-25", trades: 6, wonTrades: 3, lostTrades: 3, totalStake: 6, pnl: 1.28, roi: 21.33 }
      ],
      strategyBreakdown: [
        { name: "value-football", trades: 81, wonTrades: 38, lostTrades: 43, pnl: 9.95, roi: 12.28, winRate: 46.91 }
      ],
      trades: {
        items: [{
          timestamp: "2026-06-25T10:56:38Z",
          marketName: "Cuiaba v Londrina",
          selection: "Cuiaba",
          strategy: "value-football",
          odds: 1.76,
          stake: 1,
          status: "EXECUTED",
          result: null,
          pnl: null
        }],
        page: 0,
        size: 25,
        totalItems: 1,
        totalPages: 1
      }
    }
  }
}));

vi.mock("../hooks/useDashboardData", () => ({
  useDashboardData: () => hookState.value
}));

function render() {
  return renderToStaticMarkup(<HomePage />);
}

describe("HomePage", () => {
  beforeEach(() => {
    hookState.value.isLoading = false;
    hookState.value.isRefreshing = false;
    hookState.value.error = null;
    hookState.value.range = "30D";
  });

  test("renders analytics dashboard instead of bot status console", () => {
    const html = render();

    expect(html).toContain("BetX Dashboard");
    expect(html).toContain("Performance, trades and risk analytics");
    expect(html).toContain("30D");
    expect(html).toContain("aria-label=\"Actualizar datos\"");
    expect(html).toContain("Total PnL");
    expect(html).toContain("ROI");
    expect(html).toContain("Trades");
    expect(html).toContain("Win rate");
    expect(html).toContain("Max drawdown");
    expect(html).toContain("Open exposure");
    expect(html).toContain("Equity curve");
    expect(html).toContain("Cumulative settled PnL over selected range");
    expect(html).toContain("Initial / final");
    expect(html).toContain("Best point");
    expect(html).toContain("Worst point");
    expect(html).toContain("Max drawdown");
    expect(html).toContain("Current drawdown");
    expect(html).toContain("Risk insights");
    expect(html).toContain("Worst daily loss");
    expect(html).toContain("Recovery from low");
    expect(html).not.toContain("Drawdown mini chart");
    expect(html).not.toContain("Distance from previous equity peak");
    expect(html).toContain("Daily PnL");
    expect(html).toContain("Net PnL grouped by settlement date");
    expect(html).toContain("ROI del dia");
    expect(html).toContain("data-bar-tone=\"positive\"");
    expect(html).toContain("data-bar-tone=\"negative\"");
    expect(html).toContain("Win/Loss");
    expect(html).toContain("ROI by strategy");
    expect(html).toContain("Trades");
    expect(html).toContain("1 resultado");
    expect(html).toContain("Estado");
    expect(html).toContain("Resultado");
    expect(html).toContain("Buscar");
    expect(html).toContain("Cuiaba v Londrina");
    expect(html).toContain("Cumulative PnL");
    expect(html).toContain("Drawdown");
    expect(html).toContain("--tooltip-x");
    expect(html).toContain("--tooltip-y");
    expect(html).not.toContain("tooltip-left");
    expect(html).not.toContain("tooltip-right");
    expect(html).not.toContain("Supervision operativa");
    expect(html).not.toContain("Panel de seguridad operativo");
    expect(html).not.toContain("java -jar");
    expect(html).not.toContain("Activar BetX");
    expect(html).not.toContain("Pausar BetX");
    expect(html).not.toContain("Reintentar");
  });

  test("renders useful empty states", () => {
    hookState.value.data = {
      ...hookState.value.data,
      equity: [],
      dailyPnl: [],
      strategyBreakdown: [],
      trades: { items: [], page: 0, size: 25, totalItems: 0, totalPages: 0 }
    };

    const html = render();

    expect(html).toContain("No hay puntos de equity para este rango.");
    expect(html).toContain("No hay PnL diario para este rango.");
    expect(html).toContain("No hay desglose por estrategia.");
    expect(html).toContain("No hay trades para los filtros seleccionados.");
  });

  test("shows passive error state without header retry or operational controls", () => {
    hookState.value.isRefreshing = true;
    hookState.value.error = "No se han podido cargar las metricas.";

    const html = render();

    expect(html).toContain("Actualizando...");
    expect(html).toContain("No se han podido cargar las metricas.");
    expect(html).not.toContain("Reintentar");
    expect(html).not.toContain("Reanudar BetX");
  });

  test("keeps cursor tooltip inside the chart stage near the right edge", () => {
    expect(tooltipPositionWithinStage(1160, 140, 1182)).toEqual({ x: 858, y: 140 });
    expect(tooltipPositionWithinStage(240, 80, 1182)).toEqual({ x: 252, y: 80 });
  });
});
