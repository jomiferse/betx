import type { ActivityItem } from "../types/interface";
import { formatActivityTime, formatDecimal, formatMoney, formatResultLabel } from "../utils/format";
import { translateOperationStatus, translateResult, translateSelection } from "../utils/translations";

interface ActivityListProps {
  items: ActivityItem[];
  error: string | null;
}

export function ActivityList({ items, error }: ActivityListProps) {
  return (
    <section className="activity">
      <div className="section-title">
        <h2>Actividad reciente</h2>
        <span>{items.length} {items.length === 1 ? "operacion" : "operaciones"}</span>
      </div>
      {error ? (
        <p className="empty" role="status">{error}</p>
      ) : items.length === 0 ? (
        <p className="empty">Todavia no hay actividad reciente.</p>
      ) : (
        <div className="activity-table" role="table" aria-label="Actividad reciente">
          <div className="activity-row activity-head" role="row">
            <span role="columnheader">Evento</span>
            <span role="columnheader">Seleccion</span>
            <span role="columnheader">Cuota</span>
            <span role="columnheader">Importe</span>
            <span role="columnheader">Estado</span>
            <span role="columnheader">Resultado</span>
            <span role="columnheader">Fecha</span>
          </div>
          {items.map((item) => (
            <article className="activity-row" key={item.id} role="row">
              <strong data-label="Evento" role="cell">{item.event || "-"}</strong>
              <span data-label="Seleccion" role="cell">{translateSelection(item.selection)}</span>
              <span data-label="Cuota" role="cell">{formatDecimal(item.odds)}</span>
              <span data-label="Importe" role="cell">{formatMoney(item.amount)}</span>
              <span data-label="Estado" role="cell">{translateOperationStatus(item.status)}</span>
              <span data-label="Resultado" role="cell">{formatResultLabel(item.result, item.netPnl, translateResult)}</span>
              <time data-label="Fecha" role="cell" dateTime={item.updatedAt}>{formatActivityTime(item.updatedAt)}</time>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
