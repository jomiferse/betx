import type { CSSProperties, MouseEvent, ReactNode } from "react";
import { useMemo, useState } from "react";
import { useDashboardData } from "../hooks/useDashboardData";
import type {
  DashboardBreakdownItem,
  DashboardDailyPnlPoint,
  DashboardEquityPoint,
  DashboardRange,
  DashboardSummary,
  DashboardTrade,
  DashboardTradeFilters,
  SortOrder,
  TradeSort
} from "../types/dashboard";
import { formatActivityTime, formatDecimal, formatMoney, formatSignedMoney } from "../utils/format";
import { translateOperationStatus, translateResult, translateSelection } from "../utils/translations";

const ranges: DashboardRange[] = ["7D", "30D", "90D", "ALL"];

type TooltipPosition = {
  x: number;
  y: number;
};

const tooltipOffset = 12;
const tooltipMaxWidth = 310;
const tooltipEdgePadding = 14;

const emptySummary: DashboardSummary = {
  totalPnl: 0,
  roi: 0,
  totalTrades: 0,
  wonTrades: 0,
  lostTrades: 0,
  winRate: 0,
  totalStaked: 0,
  maxDrawdown: 0,
  openExposure: 0,
  lastUpdatedAt: null
};

export function HomePage() {
  const {
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
  } = useDashboardData();
  const summary = data?.summary ?? emptySummary;
  const strategyOptions = useMemo(() => {
    const names = (data?.strategyBreakdown ?? []).map((item) => item.name).filter(Boolean);
    return Array.from(new Set(names));
  }, [data?.strategyBreakdown]);

  return (
    <main className="dashboard-shell">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">BetX Analytics</p>
          <h1>BetX Dashboard</h1>
          <p className="hero-copy">Performance, trades and risk analytics</p>
        </div>
        <div className="dashboard-toolbar">
          <div className="range-filter" aria-label="Rango temporal">
            {ranges.map((item) => (
              <button
                key={item}
                type="button"
                className={item === range ? "range-active" : ""}
                onClick={() => setRange(item)}
              >
                {item}
              </button>
            ))}
            <button
              type="button"
              className="range-refresh"
              aria-label="Actualizar datos"
              title="Actualizar datos"
              onClick={() => void refresh()}
              disabled={isRefreshing}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path d="M20 11a8 8 0 0 0-14.5-4.7L4 8" />
                <path d="M4 4v4h4" />
                <path d="M4 13a8 8 0 0 0 14.5 4.7L20 16" />
                <path d="M20 20v-4h-4" />
              </svg>
            </button>
          </div>
          {isRefreshing ? <span className="refresh-state" role="status">Actualizando...</span> : null}
          <p className="last-updated">Ultima actualizacion: {formatActivityTime(lastUpdatedAt ?? summary.lastUpdatedAt)}</p>
        </div>
      </header>

      {error ? <p className="error" role="status">{error}</p> : null}

      <section className="kpi-grid" aria-label="Metricas principales">
        <MetricCard label="Total PnL" value={formatSignedMoney(summary.totalPnl)} tone={summary.totalPnl >= 0 ? "positive" : "negative"} />
        <MetricCard label="ROI" value={`${formatDecimal(summary.roi)}%`} tone={summary.roi >= 0 ? "positive" : "negative"} />
        <MetricCard label="Trades" value={summary.totalTrades.toString()} hint={`${summary.wonTrades} ganadas · ${summary.lostTrades} perdidas`} />
        <MetricCard label="Win rate" value={`${formatDecimal(summary.winRate)}%`} />
        <MetricCard label="Max drawdown" value={formatMoney(summary.maxDrawdown)} tone="negative" />
        <MetricCard label="Open exposure" value={formatMoney(summary.openExposure)} />
      </section>

      <section className="dashboard-grid">
        <ChartCard title="Equity curve" meta="Cumulative PnL" className="equity-card">
          <EquityChart points={data?.equity ?? []} dailyPnl={data?.dailyPnl ?? []} loading={isLoading} />
        </ChartCard>
        <ChartCard title="Daily PnL" meta="Net PnL grouped by settlement date" className="daily-card">
          <DailyPnlChart points={data?.dailyPnl ?? []} loading={isLoading} />
        </ChartCard>
        <ChartCard title="Win/Loss" className="secondary-chart">
          <WinLossChart summary={summary} />
        </ChartCard>
        <ChartCard title="ROI by strategy" className="secondary-chart">
          <StrategyBreakdown items={data?.strategyBreakdown ?? []} loading={isLoading} />
        </ChartCard>
      </section>

      <TradesTable
        trades={data?.trades.items ?? []}
        totalItems={data?.trades.totalItems ?? 0}
        totalPages={data?.trades.totalPages ?? 0}
        filters={tradeFilters}
        setFilters={setTradeFilters}
        strategyOptions={strategyOptions}
        loading={isLoading}
      />
    </main>
  );
}

function MetricCard({ label, value, hint, tone = "neutral" }: {
  label: string;
  value: string;
  hint?: string;
  tone?: "positive" | "negative" | "neutral";
}) {
  return (
    <article className={`metric-card metric-${tone}`}>
      <p className="label">{label}</p>
      <p className="metric-value">{value}</p>
      {hint ? <p className="hint">{hint}</p> : null}
    </article>
  );
}

function ChartCard({ title, meta, className = "", children }: {
  title: string;
  meta?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={`chart-card ${className}`}>
      <div className="section-title">
        <h2>{title}</h2>
        {meta ? <span>{meta}</span> : null}
      </div>
      {children}
    </section>
  );
}

function EquityChart({ points, dailyPnl, loading }: {
  points: DashboardEquityPoint[];
  dailyPnl: DashboardDailyPnlPoint[];
  loading: boolean;
}) {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState<TooltipPosition | null>(null);
  if (points.length === 0) {
    return <p className="empty">{loading ? "Cargando equity..." : "No hay puntos de equity para este rango."}</p>;
  }
  const currentIndex = Math.max(0, Math.min(activeIndex ?? points.length - 1, points.length - 1));
  const values = points.map((point) => point.cumulativePnl);
  const min = Math.min(...values, 0);
  const max = Math.max(...values, 0);
  const span = max - min || 1;
  const width = 760;
  const height = 300;
  const left = 58;
  const right = 18;
  const top = 18;
  const bottom = 34;
  const plotWidth = width - left - right;
  const plotHeight = height - top - bottom;
  const yForValue = (value: number) => top + (1 - ((value - min) / span)) * plotHeight;
  const xForIndex = (index: number) => left + (points.length === 1 ? 0 : (index / (points.length - 1)) * plotWidth);
  const coordinates = points.map((point, index) => {
    const x = xForIndex(index);
    const y = yForValue(point.cumulativePnl);
    return { x, y, point };
  });
  const path = coordinates.map(({ x, y }) => `${x},${y}`).join(" ");
  const zeroY = yForValue(0);
  const active = coordinates[currentIndex];
  const first = points[0];
  const last = points[points.length - 1];
  const best = points.reduce((candidate, point) => point.cumulativePnl > candidate.cumulativePnl ? point : candidate, points[0]);
  const worst = points.reduce((candidate, point) => point.cumulativePnl < candidate.cumulativePnl ? point : candidate, points[0]);
  const maxDrawdown = Math.max(...points.map((point) => point.drawdown), 0);
  const worstDailyLoss = dailyPnl.filter((point) => point.pnl < 0).reduce<DashboardDailyPnlPoint | null>((candidate, point) => {
    if (!candidate || point.pnl < candidate.pnl) {
      return point;
    }
    return candidate;
  }, null);
  const recoveryFromLow = Math.max(0, last.cumulativePnl - worst.cumulativePnl);
  const xTicks = chartTicks(points, 4);
  const yTicks = valueTicks(min, max, 4);
  const handleMouseMove = (event: MouseEvent<HTMLDivElement>) => {
    setTooltipPosition(cursorPosition(event));
    const svgX = svgXPosition(event, width);
    if (svgX === null) {
      return;
    }
    const nearestIndex = coordinates.reduce((candidate, coordinate, index) => {
      const candidateDistance = Math.abs(coordinates[candidate].x - svgX);
      const distance = Math.abs(coordinate.x - svgX);
      return distance < candidateDistance ? index : candidate;
    }, 0);
    setActiveIndex(nearestIndex);
  };

  return (
    <div className="equity-chart">
      <p className="chart-description">Cumulative settled PnL over selected range</p>
      <div className="chart-stage" onMouseMove={handleMouseMove} onMouseLeave={() => setTooltipPosition(null)}>
        <svg viewBox={`0 0 ${width} ${height}`} aria-label="Equity curve" role="img">
          {yTicks.map((tick) => (
            <g key={tick}>
              <line className="grid-line" x1={left} x2={left + plotWidth} y1={yForValue(tick)} y2={yForValue(tick)} />
              <text className="axis-label-text" x={left - 10} y={yForValue(tick) + 4} textAnchor="end">{formatMoney(tick)}</text>
            </g>
          ))}
          {xTicks.map((index) => (
            <text key={points[index].timestamp} className="axis-label-text" x={xForIndex(index)} y={height - 9} textAnchor="middle">
              {formatShortDate(points[index].timestamp)}
            </text>
          ))}
        <line className="zero-line" x1={left} x2={left + plotWidth} y1={zeroY} y2={zeroY} vectorEffect="non-scaling-stroke" />
        <polyline points={path} fill="none" stroke="currentColor" strokeWidth="2.8" vectorEffect="non-scaling-stroke" />
        {coordinates.map(({ x, y, point }, index) => (
          <circle
            key={`${point.timestamp}-${index}`}
            className="chart-hit"
            cx={x}
            cy={y}
            r="8"
            tabIndex={0}
            onMouseEnter={() => setActiveIndex(index)}
            onFocus={() => setActiveIndex(index)}
          >
            <title>{equityTooltipText(point)}</title>
          </circle>
        ))}
        {active ? (
          <g>
            <line className="crosshair" x1={active.x} x2={active.x} y1={top} y2={top + plotHeight} />
            <circle className="chart-marker" cx={active.x} cy={active.y} r="4" vectorEffect="non-scaling-stroke" />
          </g>
        ) : null}
      </svg>
      {active ? (
        <div
          className="chart-tooltip floating"
          role="status"
          style={tooltipStyle(tooltipPosition?.x ?? active.x, tooltipPosition?.y ?? active.y)}
        >
          <strong>{formatActivityTime(active.point.timestamp)}</strong>
          <span>Cumulative PnL: {formatSignedMoney(active.point.cumulativePnl)}</span>
          <span>Trade PnL: {formatSignedMoney(active.point.pnl)} · Daily PnL: {formatSignedMoney(active.point.dailyPnl)}</span>
          <span>Drawdown: {formatMoney(active.point.drawdown)}</span>
          <span>{active.point.sequenceNumber} trades acumulados · ROI acumulado {formatDecimal(active.point.cumulativeRoi)}%</span>
        </div>
      ) : null}
      </div>
      <div className="risk-insights">
        <div className="risk-insights-header">
          <strong>Risk insights</strong>
          <span>Range health at a glance</span>
        </div>
        <div className="chart-summary-grid">
          <ChartStat label="Initial / final" value={`${formatSignedMoney(first.cumulativePnl)} -> ${formatSignedMoney(last.cumulativePnl)}`} />
          <ChartStat label="Best point" value={formatSignedMoney(best.cumulativePnl)} />
          <ChartStat label="Worst point" value={formatSignedMoney(worst.cumulativePnl)} />
          <ChartStat label="Max drawdown" value={formatMoney(maxDrawdown)} />
          <ChartStat label="Current drawdown" value={formatMoney(last.drawdown)} />
          <ChartStat label="Worst daily loss" value={worstDailyLoss ? formatSignedMoney(worstDailyLoss.pnl) : "Sin perdidas"} tone={worstDailyLoss ? "negative" : "neutral"} />
          <ChartStat label="Recovery from low" value={formatSignedMoney(recoveryFromLow)} tone={recoveryFromLow > 0 ? "positive" : "neutral"} />
        </div>
      </div>
    </div>
  );
}

function DailyPnlChart({ points, loading }: { points: DashboardDailyPnlPoint[]; loading: boolean }) {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState<TooltipPosition | null>(null);
  if (points.length === 0) {
    return <p className="empty">{loading ? "Cargando PnL diario..." : "No hay PnL diario para este rango."}</p>;
  }
  const visiblePoints = points.slice(-14);
  const currentIndex = Math.max(0, Math.min(activeIndex ?? visiblePoints.length - 1, visiblePoints.length - 1));
  const active = visiblePoints[currentIndex];
  const maxAbs = Math.max(...visiblePoints.map((point) => Math.abs(point.pnl)), 1);
  const width = 760;
  const height = 250;
  const left = 58;
  const right = 18;
  const top = 18;
  const bottom = 34;
  const plotWidth = width - left - right;
  const plotHeight = height - top - bottom;
  const zeroY = top + plotHeight / 2;
  const barSlot = plotWidth / visiblePoints.length;
  const barWidth = Math.min(34, Math.max(12, barSlot * 0.58));
  const yTicks = valueTicks(-maxAbs, maxAbs, 4);
  const handleMouseMove = (event: MouseEvent<HTMLDivElement>) => {
    setTooltipPosition(cursorPosition(event));
    const svgX = svgXPosition(event, width);
    if (svgX === null) {
      return;
    }
    const nearestIndex = visiblePoints.reduce((candidate, _point, index) => {
      const candidateX = left + candidate * barSlot + barSlot / 2;
      const currentX = left + index * barSlot + barSlot / 2;
      return Math.abs(currentX - svgX) < Math.abs(candidateX - svgX) ? index : candidate;
    }, 0);
    setActiveIndex(nearestIndex);
  };
  return (
    <div className="daily-pnl-chart">
      <p className="chart-description">Net PnL grouped by settlement date</p>
      <div className="chart-stage" onMouseMove={handleMouseMove} onMouseLeave={() => setTooltipPosition(null)}>
        <svg viewBox={`0 0 ${width} ${height}`} aria-label="Daily PnL" role="img">
          {yTicks.map((tick) => (
            <g key={tick}>
              <line className="grid-line" x1={left} x2={left + plotWidth} y1={zeroY - (tick / maxAbs) * (plotHeight / 2)} y2={zeroY - (tick / maxAbs) * (plotHeight / 2)} />
              <text className="axis-label-text" x={left - 10} y={zeroY - (tick / maxAbs) * (plotHeight / 2) + 4} textAnchor="end">{formatMoney(tick)}</text>
            </g>
          ))}
          <line className="zero-line" x1={left} x2={left + plotWidth} y1={zeroY} y2={zeroY} />
        {visiblePoints.map((point, index) => (
          <g key={point.day}>
            <rect
              className={`pnl-bar ${point.pnl >= 0 ? "bar-positive" : "bar-negative"}`}
              data-bar-tone={point.pnl >= 0 ? "positive" : "negative"}
              x={left + index * barSlot + (barSlot - barWidth) / 2}
              y={point.pnl >= 0 ? zeroY - (Math.abs(point.pnl) / maxAbs) * (plotHeight / 2) : zeroY}
              width={barWidth}
              height={Math.max(4, (Math.abs(point.pnl) / maxAbs) * (plotHeight / 2))}
              tabIndex={0}
              onMouseEnter={() => setActiveIndex(index)}
              onFocus={() => setActiveIndex(index)}
            >
              <title>{dailyTooltipText(point)}</title>
            </rect>
            <text className="axis-label-text" x={left + index * barSlot + barSlot / 2} y={height - 9} textAnchor="middle">
              {formatShortDate(point.day)}
            </text>
          </g>
        ))}
        </svg>
      {active ? (
        <div
          className="chart-tooltip floating"
          role="status"
          style={tooltipStyle(
            tooltipPosition?.x ?? left + currentIndex * barSlot + barSlot / 2,
            tooltipPosition?.y ?? pointTooltipY(active.pnl, zeroY, maxAbs, plotHeight)
          )}
        >
          <strong>{formatShortDate(active.day)}</strong>
          <span>PnL del dia: {formatSignedMoney(active.pnl)}</span>
          <span>{active.trades} trades · {active.wonTrades} ganadas · {active.lostTrades} perdidas</span>
          <span>Stake total: {formatMoney(active.totalStake)}</span>
          <span>ROI del dia: {formatDecimal(active.roi)}%</span>
        </div>
      ) : null}
      </div>
    </div>
  );
}

function WinLossChart({ summary }: { summary: DashboardSummary }) {
  const total = summary.wonTrades + summary.lostTrades || 1;
  const winWidth = (summary.wonTrades / total) * 100;
  return (
    <div className="distribution">
      <div className="distribution-track" aria-label="Win/Loss distribution">
        <span className="distribution-win" style={{ width: `${winWidth}%` }} />
      </div>
      <div className="distribution-labels">
        <span>{summary.wonTrades} ganadas</span>
        <span>{summary.lostTrades} perdidas</span>
      </div>
    </div>
  );
}

function StrategyBreakdown({ items, loading }: { items: DashboardBreakdownItem[]; loading: boolean }) {
  if (items.length === 0) {
    return <p className="empty">{loading ? "Cargando estrategias..." : "No hay desglose por estrategia."}</p>;
  }
  return (
    <div className="breakdown-list">
      {items.slice(0, 5).map((item) => (
        <article className="breakdown-item" key={item.name}>
          <div>
            <strong>{item.name}</strong>
            <p>{item.trades} trades · {formatDecimal(item.winRate)}% win rate</p>
          </div>
          <div>
            <strong>{formatSignedMoney(item.pnl)}</strong>
            <p>{formatDecimal(item.roi)}% ROI</p>
          </div>
        </article>
      ))}
    </div>
  );
}

function TradesTable({
  trades,
  totalItems,
  totalPages,
  filters,
  setFilters,
  strategyOptions,
  loading
}: {
  trades: DashboardTrade[];
  totalItems: number;
  totalPages: number;
  filters: DashboardTradeFilters;
  setFilters: (filters: Partial<DashboardTradeFilters>) => void;
  strategyOptions: string[];
  loading: boolean;
}) {
  const setFilter = (filter: Partial<DashboardTradeFilters>) => setFilters({ ...filter, page: filter.page ?? 0 });
  const sortBy = (sort: TradeSort) => {
    const nextOrder: SortOrder = filters.sort === sort && filters.order === "desc" ? "asc" : "desc";
    setFilter({ sort, order: nextOrder });
  };
  return (
    <section className="trades-panel">
      <div className="section-title">
        <h2>Trades</h2>
        <span>{totalItems} {totalItems === 1 ? "resultado" : "resultados"}</span>
      </div>
      <div className="trade-filters">
        <label>
          Estado
          <select value={filters.status} onChange={(event) => setFilter({ status: event.target.value })}>
            <option value="ALL">All</option>
            <option value="EXECUTED">Realizada</option>
            <option value="SETTLED">Liquidada</option>
            <option value="AWAITING_CONFIRMATION">Pendiente</option>
            <option value="CANCELLED">Rechazada</option>
            <option value="FAILED">Fallida</option>
          </select>
        </label>
        <label>
          Resultado
          <select value={filters.result} onChange={(event) => setFilter({ result: event.target.value })}>
            <option value="ALL">All</option>
            <option value="WIN">Ganada</option>
            <option value="LOSE">Perdida</option>
            <option value="PENDING">Pendiente</option>
          </select>
        </label>
        <label>
          Estrategia
          <select value={filters.strategy} onChange={(event) => setFilter({ strategy: event.target.value })}>
            <option value="ALL">All</option>
            {strategyOptions.map((strategy) => <option value={strategy} key={strategy}>{strategy}</option>)}
          </select>
        </label>
        <label className="search-filter">
          Buscar
          <input
            value={filters.search}
            onChange={(event) => setFilter({ search: event.target.value })}
            placeholder="Mercado o seleccion"
          />
        </label>
        <label>
          Por pagina
          <select value={filters.size} onChange={(event) => setFilter({ size: Number(event.target.value) })}>
            <option value={25}>25</option>
            <option value={50}>50</option>
            <option value={100}>100</option>
          </select>
        </label>
      </div>
      {trades.length === 0 ? (
        <p className="empty">{loading ? "Cargando trades..." : "No hay trades para los filtros seleccionados."}</p>
      ) : (
        <div className="trades-table" role="table" aria-label="Trades">
          <div className="trade-row trade-head" role="row">
            <button type="button" role="columnheader" onClick={() => sortBy("timestamp")}>Fecha</button>
            <span role="columnheader">Mercado</span>
            <span role="columnheader">Seleccion</span>
            <span role="columnheader">Estrategia</span>
            <button type="button" role="columnheader" onClick={() => sortBy("odds")}>Odds</button>
            <button type="button" role="columnheader" onClick={() => sortBy("stake")}>Stake</button>
            <span role="columnheader">Estado</span>
            <span role="columnheader">Resultado</span>
            <button type="button" role="columnheader" onClick={() => sortBy("pnl")}>PnL</button>
          </div>
          {trades.map((trade) => (
            <article className="trade-row" key={`${trade.timestamp}-${trade.marketName}-${trade.selection}`} role="row">
              <time data-label="Fecha" role="cell" dateTime={trade.timestamp}>{formatActivityTime(trade.timestamp)}</time>
              <strong data-label="Mercado" role="cell">{trade.marketName}</strong>
              <span data-label="Seleccion" role="cell">{translateSelection(trade.selection)}</span>
              <span data-label="Estrategia" role="cell">{trade.strategy}</span>
              <span data-label="Odds" role="cell">{formatDecimal(trade.odds)}</span>
              <span data-label="Stake" role="cell">{formatMoney(trade.stake)}</span>
              <span data-label="Estado" role="cell">{translateOperationStatus(trade.status)}</span>
              <span data-label="Resultado" role="cell">{translateResult(trade.result)}</span>
              <span data-label="PnL" role="cell">{trade.pnl === null ? "-" : formatSignedMoney(trade.pnl)}</span>
            </article>
          ))}
        </div>
      )}
      <div className="pagination">
        <button type="button" disabled={filters.page <= 0} onClick={() => setFilters({ page: filters.page - 1 })}>Anterior</button>
        <span>Pagina {totalItems === 0 ? 0 : filters.page + 1} de {totalPages}</span>
        <button type="button" disabled={filters.page + 1 >= totalPages} onClick={() => setFilters({ page: filters.page + 1 })}>Siguiente</button>
      </div>
    </section>
  );
}

function ChartStat({ label, value, tone = "neutral" }: {
  label: string;
  value: string;
  tone?: "positive" | "negative" | "neutral";
}) {
  return (
    <article className={`chart-stat stat-${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function formatShortDate(value: string) {
  return new Intl.DateTimeFormat("es-ES", { day: "2-digit", month: "short" }).format(new Date(value));
}

function chartTicks<T>(items: T[], targetCount: number) {
  if (items.length === 1) {
    return [0];
  }
  const maxIndex = items.length - 1;
  return Array.from({ length: targetCount }, (_, index) => Math.round((index / (targetCount - 1)) * maxIndex))
    .filter((value, index, values) => values.indexOf(value) === index);
}

function valueTicks(min: number, max: number, count: number) {
  if (min === max) {
    return [min];
  }
  const span = max - min || 1;
  return Array.from({ length: count }, (_, index) => min + (span / (count - 1)) * index);
}

function equityTooltipText(point: DashboardEquityPoint) {
  return [
    formatActivityTime(point.timestamp),
    `Cumulative PnL ${formatSignedMoney(point.cumulativePnl)}`,
    `Trade PnL ${formatSignedMoney(point.pnl)}`,
    `Daily PnL ${formatSignedMoney(point.dailyPnl)}`,
    `Drawdown ${formatMoney(point.drawdown)}`,
    `${point.sequenceNumber} trades acumulados`,
    `ROI ${formatDecimal(point.cumulativeRoi)}%`
  ].join(" · ");
}

function dailyTooltipText(point: DashboardDailyPnlPoint) {
  return [
    formatShortDate(point.day),
    `PnL del dia ${formatSignedMoney(point.pnl)}`,
    `${point.trades} trades`,
    `${point.wonTrades} ganadas`,
    `${point.lostTrades} perdidas`,
    `Stake ${formatMoney(point.totalStake)}`,
    `ROI ${formatDecimal(point.roi)}%`
  ].join(" · ");
}

function tooltipStyle(x: number, y: number) {
  return {
    "--tooltip-x": `${x}px`,
    "--tooltip-y": `${Math.max(18, y)}px`
  } as CSSProperties;
}

function cursorPosition(event: MouseEvent<HTMLDivElement>) {
  const rect = event.currentTarget.getBoundingClientRect();
  return tooltipPositionWithinStage(event.clientX - rect.left, event.clientY - rect.top, rect.width);
}

export function tooltipPositionWithinStage(cursorX: number, cursorY: number, stageWidth: number) {
  const maxX = Math.max(tooltipEdgePadding, stageWidth - tooltipMaxWidth - tooltipEdgePadding);
  return {
    x: Math.min(cursorX + tooltipOffset, maxX),
    y: cursorY
  };
}

function svgXPosition(event: MouseEvent<HTMLDivElement>, viewBoxWidth: number) {
  const svg = event.currentTarget.querySelector("svg");
  if (!svg) {
    return null;
  }
  const rect = svg.getBoundingClientRect();
  if (rect.width === 0) {
    return null;
  }
  return ((event.clientX - rect.left) / rect.width) * viewBoxWidth;
}

function pointTooltipY(pnl: number, zeroY: number, maxAbs: number, plotHeight: number) {
  if (pnl >= 0) {
    return zeroY - (Math.abs(pnl) / maxAbs) * (plotHeight / 2);
  }
  return zeroY + (Math.abs(pnl) / maxAbs) * (plotHeight / 2);
}
