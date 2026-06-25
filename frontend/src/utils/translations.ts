const selectionTranslations: Record<string, string> = {
  "The Draw": "Empate"
};

const statusTranslations: Record<string, string> = {
  PENDING: "Pendiente",
  EXECUTED: "Realizada",
  SETTLED: "Liquidada",
  CANCELLED: "Cancelada",
  REJECTED: "Rechazada",
  AWAITING_CONFIRMATION: "Espera confirmacion",
  AWAITING_STAKE: "Espera importe",
  FAILED: "Fallida"
};

const resultTranslations: Record<string, string> = {
  WIN: "Ganada",
  LOSE: "Perdida",
  VOID: "Anulada"
};

export function translateSelection(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  return selectionTranslations[value] ?? value;
}

export function translateOperationStatus(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  return statusTranslations[value] ?? value;
}

export function translateResult(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  return resultTranslations[value] ?? value;
}
