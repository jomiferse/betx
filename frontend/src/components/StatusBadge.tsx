import type { BetxStatus } from "../types/interface";

const labels: Record<BetxStatus, string> = {
  ACTIVE: "Activo",
  PAUSED: "Pausado",
  NEEDS_ATTENTION: "Necesita atencion"
};

export function StatusBadge({ status }: { status: BetxStatus }) {
  return <span className={`status status-${status.toLowerCase()}`}>{labels[status]}</span>;
}
