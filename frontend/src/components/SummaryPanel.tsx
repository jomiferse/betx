import type { InterfaceStatusView } from "../types/interface";
import { formatActivityTime, formatMoney } from "../utils/format";

interface SummaryPanelProps {
  status: InterfaceStatusView | null;
  loading: boolean;
}

export function SummaryPanel({ status, loading }: SummaryPanelProps) {
  const isActive = status?.status === "ACTIVE";
  const isPaused = status?.status === "PAUSED";
  const statusMessage = isActive
    ? "BetX esta activo."
    : isPaused
      ? "BetX esta pausado."
      : status?.message ?? "Estado no disponible.";
  const confirmationDescription = status?.manualConfirmationEnabled
    ? "Las operaciones requieren aprobacion antes de ejecutarse."
    : "BetX puede ejecutar operaciones sin aprobacion previa.";

  return (
    <section className="summary" aria-label="Resumen de BetX">
      <div className="summary-item">
        <p className="label">Estado</p>
        <p className="message">{loading ? "Consultando estado..." : statusMessage}</p>
        {status?.lastCycleAt ? <p className="hint">Ultimo ciclo: {formatActivityTime(status.lastCycleAt)}</p> : null}
      </div>
      <div className="summary-item">
        <p className="label">Balance disponible</p>
        <p className="metric">{formatMoney(status?.availableBalance ?? null)}</p>
        <p className="hint">Cuenta Betfair</p>
      </div>
      <div className="summary-item">
        <p className="label">Confirmacion manual</p>
        <p className="metric">{status?.manualConfirmationEnabled ? "Activada" : "Desactivada"}</p>
        {status ? <p className="hint">{confirmationDescription}</p> : null}
      </div>
    </section>
  );
}
