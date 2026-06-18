export function formatMoney(value: number | null): string {
  if (value === null || value === undefined) {
    return "No disponible";
  }
  return new Intl.NumberFormat("es-ES", { style: "currency", currency: "EUR" }).format(value);
}

export function formatSignedMoney(value: number | null): string {
  if (value === null || value === undefined) {
    return "-";
  }
  const formatted = new Intl.NumberFormat("es-ES", { style: "currency", currency: "EUR" }).format(value);
  return value > 0 ? `+${formatted}` : formatted;
}

export function formatDecimal(value: number | null): string {
  if (value === null || value === undefined) {
    return "-";
  }
  return new Intl.NumberFormat("es-ES", { maximumFractionDigits: 2 }).format(value);
}

export function formatActivityTime(value: string | null, now = new Date()): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  const diffMs = now.getTime() - date.getTime();
  if (diffMs >= 0 && diffMs < 60_000) {
    return "Ahora";
  }
  if (diffMs >= 60_000 && diffMs < 60 * 60_000) {
    const minutes = Math.floor(diffMs / 60_000);
    return `Hace ${minutes} ${minutes === 1 ? "minuto" : "minutos"}`;
  }
  return new Intl.DateTimeFormat("es-ES", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(date);
}

export function formatResultLabel(result: string | null, netPnl: number | null, translator: (value: string | null) => string): string {
  if (!result || netPnl === null || netPnl === undefined) {
    return "-";
  }
  return `${translator(result)} · ${formatSignedMoney(netPnl)}`;
}
