import { ActivityList } from "../components/ActivityList";
import { StatusBadge } from "../components/StatusBadge";
import { SummaryPanel } from "../components/SummaryPanel";
import { useInterfaceData } from "../hooks/useInterfaceData";

export function HomePage() {
  const {
    status,
    activity,
    initialLoading,
    actionPending,
    currentAction,
    statusError,
    activityError,
    actionError,
    activate,
    pause
  } = useInterfaceData();
  const activateDisabled = actionPending || status?.status === "ACTIVE";
  const pauseDisabled = actionPending || status?.status !== "ACTIVE";

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">BetX</p>
          <h1>Inicio</h1>
        </div>
        <StatusBadge status={status?.status ?? "PAUSED"} />
      </header>

      {statusError ? <p className="error" role="status">No se ha podido obtener el estado de BetX.</p> : null}
      {actionError ? <p className="error" role="status">{actionError}</p> : null}

      <SummaryPanel status={status} loading={initialLoading} />

      <section className="actions" aria-label="Controles de BetX">
        <button type="button" onClick={activate} disabled={activateDisabled} aria-label="Activar BetX">
          {currentAction === "activate" ? "Activando..." : "Activar BetX"}
        </button>
        <button type="button" className="secondary" onClick={pause} disabled={pauseDisabled} aria-label="Pausar BetX">
          {currentAction === "pause" ? "Pausando..." : "Pausar BetX"}
        </button>
      </section>

      <ActivityList items={activity} error={activityError} />
    </main>
  );
}
