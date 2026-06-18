import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { HomePage } from "./HomePage";
import type { ActivityItem, InterfaceStatusView } from "../types/interface";

const hookState = vi.hoisted(() => ({
  value: {
    status: null as InterfaceStatusView | null,
    activity: [] as ActivityItem[],
    initialLoading: false,
    actionPending: false,
    currentAction: null as "activate" | "pause" | null,
    statusError: null as string | null,
    activityError: null as string | null,
    actionError: null as string | null,
    refresh: vi.fn(),
    activate: vi.fn(),
    pause: vi.fn()
  }
}));

vi.mock("../hooks/useInterfaceData", () => ({
  useInterfaceData: () => hookState.value
}));

const activeStatus: InterfaceStatusView = {
  status: "ACTIVE",
  message: "BetX esta activo.",
  availableBalance: 125.5,
  lastUpdatedAt: "2026-06-18T10:00:00Z",
  lastCycleAt: "2026-06-18T10:00:00Z",
  manualConfirmationEnabled: true
};

const pausedStatus: InterfaceStatusView = {
  ...activeStatus,
  status: "PAUSED",
  message: "BetX esta pausado.",
  lastCycleAt: null,
  manualConfirmationEnabled: false
};

function render() {
  return renderToStaticMarkup(<HomePage />);
}

describe("HomePage", () => {
  beforeEach(() => {
    hookState.value = {
      status: activeStatus,
      activity: [],
      initialLoading: false,
      actionPending: false,
      currentAction: null,
      statusError: null,
      activityError: null,
      actionError: null,
      refresh: vi.fn(),
      activate: vi.fn(),
      pause: vi.fn()
    };
  });

  test("renders the active state and disables activation", () => {
    const html = render();

    expect(html).toContain("BetX esta activo.");
    expect(html).toContain("Ultimo ciclo:");
    expect(html).toContain("Activada");
    expect(html).toContain("Las operaciones requieren aprobacion antes de ejecutarse.");
    expect(html).toContain("Activar BetX</button>");
    expect(html).toContain("<button type=\"button\" disabled=\"\"");
  });

  test("renders the paused state and disables pause", () => {
    hookState.value.status = pausedStatus;

    const html = render();

    expect(html).toContain("BetX esta pausado.");
    expect(html).toContain("Desactivada");
    expect(html).toContain("BetX puede ejecutar operaciones sin aprobacion previa.");
    expect(html).toContain("Pausar BetX</button>");
  });

  test("renders initial loading and status errors", () => {
    hookState.value.status = null;
    hookState.value.initialLoading = true;
    hookState.value.statusError = "No se ha podido obtener el estado de BetX.";

    const html = render();

    expect(html).toContain("Consultando estado...");
    expect(html).toContain("No se ha podido obtener el estado de BetX.");
  });

  test("renders empty activity and activity errors", () => {
    let html = render();
    expect(html).toContain("Todavia no hay actividad reciente.");

    hookState.value.activityError = "No se ha podido obtener la actividad reciente.";
    html = render();
    expect(html).toContain("No se ha podido obtener la actividad reciente.");
  });

  test("renders won and lost operations with product translations", () => {
    hookState.value.activity = [{
      id: "win",
      event: "Real Madrid vs Barcelona",
      selection: "The Draw",
      odds: 3.2,
      amount: 5,
      status: "SETTLED",
      result: "WIN",
      netPnl: 4,
      updatedAt: "2026-06-18T10:00:00Z"
    }, {
      id: "lose",
      event: "Atleti vs Valencia",
      selection: "Local",
      odds: 2,
      amount: 1,
      status: "SETTLED",
      result: "LOSE",
      netPnl: -1,
      updatedAt: "2026-06-18T10:01:00Z"
    }];

    const html = render();

    expect(html).toContain("Evento");
    expect(html).toContain("Seleccion");
    expect(html).toContain("Resultado");
    expect(html).toContain("Fecha");
    expect(html).toContain("Empate");
    expect(html).toContain("Liquidada");
    expect(html).toContain("Ganada");
    expect(html).toContain("Perdida");
    expect(html).toContain("+");
    expect(html).toContain("-1");
    expect(html.toLowerCase()).not.toContain("paper");
    expect(html.toLowerCase()).not.toContain("backtest");
  });

  test("shows action progress and action errors", () => {
    hookState.value.currentAction = "activate";
    hookState.value.actionPending = true;
    hookState.value.actionError = "No se ha podido activar BetX. Intentalo de nuevo.";

    const html = render();

    expect(html).toContain("Activando...");
    expect(html).toContain("No se ha podido activar BetX. Intentalo de nuevo.");
  });
});
