import { useCallback, useEffect, useState } from "react";
import { getDashboardData } from "../api/dashboardClient";
import type { DashboardData, DashboardRange, DashboardTradeFilters } from "../types/dashboard";

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

interface DashboardState {
  data: DashboardData | null;
  range: DashboardRange;
  setRange: (range: DashboardRange) => void;
  tradeFilters: DashboardTradeFilters;
  setTradeFilters: (filters: Partial<DashboardTradeFilters>) => void;
  isLoading: boolean;
  isRefreshing: boolean;
  error: string | null;
  lastUpdatedAt: string | null;
  refresh: () => Promise<void>;
}

export function useDashboardData(): DashboardState {
  const [data, setData] = useState<DashboardData | null>(null);
  const [range, setRangeValue] = useState<DashboardRange>(initialRange);
  const [tradeFilters, setTradeFiltersValue] = useState<DashboardTradeFilters>(defaultTradeFilters);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setIsRefreshing(true);
    try {
      const nextData = await getDashboardData(range, tradeFilters);
      setData(nextData);
      setError(null);
      setLastUpdatedAt(nextData.summary.lastUpdatedAt ?? new Date().toISOString());
    } catch (exc) {
      setError("No se han podido cargar las metricas.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [range, tradeFilters]);

  const setRange = useCallback((nextRange: DashboardRange) => {
    setRangeValue(nextRange);
    setTradeFiltersValue((current) => ({ ...current, page: 0 }));
    if (typeof window !== "undefined") {
      const url = new URL(window.location.href);
      url.searchParams.set("range", nextRange);
      window.history.replaceState(null, "", url);
    }
  }, []);

  const setTradeFilters = useCallback((filters: Partial<DashboardTradeFilters>) => {
    setTradeFiltersValue((current) => ({ ...current, ...filters }));
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    data,
    range,
    setRange,
    tradeFilters,
    setTradeFilters,
    isLoading,
    isRefreshing,
    error,
    lastUpdatedAt,
    refresh
  };
}

function initialRange(): DashboardRange {
  if (typeof window === "undefined") {
    return "30D";
  }
  const value = new URLSearchParams(window.location.search).get("range");
  return value === "7D" || value === "30D" || value === "90D" || value === "ALL" ? value : "30D";
}
