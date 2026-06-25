import type {
  DashboardBreakdownItem,
  DashboardDailyPnlPoint,
  DashboardData,
  DashboardEquityPoint,
  DashboardRange,
  DashboardSummary,
  DashboardTradeFilters,
  DashboardTradePage
} from "../types/dashboard";

const API_ROOT = "/api/v1/dashboard";

const defaultTradeFilters: DashboardTradeFilters = {
  page: 0,
  size: 25,
  status: "ALL",
  result: "ALL",
  strategy: "ALL",
  search: "",
  sort: "timestamp",
  order: "desc"
};

async function readJson<T>(path: string, params: URLSearchParams): Promise<T> {
  const response = await fetch(`${API_ROOT}${path}?${params.toString()}`, undefined);
  if (!response.ok) {
    throw new Error("No se han podido cargar las metricas.");
  }
  return response.json() as Promise<T>;
}

export async function getDashboardData(
  range: DashboardRange,
  filters: Partial<DashboardTradeFilters> = {}
): Promise<DashboardData> {
  const tradeFilters = { ...defaultTradeFilters, ...filters };
  const rangeParams = new URLSearchParams({ range });
  const tradeParams = new URLSearchParams({
    range,
    page: tradeFilters.page.toString(),
    size: tradeFilters.size.toString(),
    status: tradeFilters.status,
    result: tradeFilters.result,
    strategy: tradeFilters.strategy,
    search: tradeFilters.search,
    sort: tradeFilters.sort,
    order: tradeFilters.order
  });
  const [summary, equity, dailyPnl, strategyBreakdown, trades] = await Promise.all([
    readJson<DashboardSummary>("/summary", rangeParams),
    readJson<DashboardEquityPoint[]>("/equity", rangeParams),
    readJson<DashboardDailyPnlPoint[]>("/daily-pnl", rangeParams),
    readJson<DashboardBreakdownItem[]>("/breakdown/strategy", rangeParams),
    readJson<DashboardTradePage>("/trades", tradeParams)
  ]);
  return { summary, equity, dailyPnl, strategyBreakdown, trades };
}
