export type DashboardRange = "7D" | "30D" | "90D" | "ALL";

export interface DashboardSummary {
  totalPnl: number;
  roi: number;
  totalTrades: number;
  wonTrades: number;
  lostTrades: number;
  winRate: number;
  totalStaked: number;
  maxDrawdown: number;
  openExposure: number;
  lastUpdatedAt: string | null;
}

export interface DashboardEquityPoint {
  timestamp: string;
  cumulativePnl: number;
  equity: number;
  drawdown: number;
  pnl: number;
  dailyPnl: number;
  trades: number;
  sequenceNumber: number;
  cumulativeRoi: number;
}

export interface DashboardDailyPnlPoint {
  day: string;
  trades: number;
  wonTrades: number;
  lostTrades: number;
  totalStake: number;
  pnl: number;
  roi: number;
}

export interface DashboardBreakdownItem {
  name: string;
  trades: number;
  wonTrades: number;
  lostTrades: number;
  pnl: number;
  roi: number;
  winRate: number;
}

export type TradeSort = "timestamp" | "pnl" | "stake" | "odds";
export type SortOrder = "asc" | "desc";

export interface DashboardTrade {
  timestamp: string;
  marketName: string;
  selection: string;
  strategy: string;
  odds: number | null;
  stake: number | null;
  status: string;
  result: string | null;
  pnl: number | null;
}

export interface DashboardTradePage {
  items: DashboardTrade[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface DashboardTradeFilters {
  page: number;
  size: number;
  status: string;
  result: string;
  strategy: string;
  search: string;
  sort: TradeSort;
  order: SortOrder;
}

export interface DashboardData {
  summary: DashboardSummary;
  equity: DashboardEquityPoint[];
  dailyPnl: DashboardDailyPnlPoint[];
  strategyBreakdown: DashboardBreakdownItem[];
  trades: DashboardTradePage;
}
