import { useCallback, useEffect, useRef, useState } from "react";
import { activateBetx, getActivity, getStatus, pauseBetx } from "../api/interfaceClient";
import type { ActivityItem, InterfaceStatusView } from "../types/interface";
import { confirmActivation, createInFlightGate } from "./actionGuards";

type CurrentAction = "activate" | "pause" | null;

interface InterfaceState {
  status: InterfaceStatusView | null;
  activity: ActivityItem[];
  initialLoading: boolean;
  actionPending: boolean;
  currentAction: CurrentAction;
  statusError: string | null;
  activityError: string | null;
  actionError: string | null;
  refresh: () => Promise<void>;
  activate: () => Promise<void>;
  pause: () => Promise<void>;
}

export function useInterfaceData(): InterfaceState {
  const [status, setStatus] = useState<InterfaceStatusView | null>(null);
  const [activity, setActivity] = useState<ActivityItem[]>([]);
  const [initialLoading, setInitialLoading] = useState(true);
  const [currentAction, setCurrentAction] = useState<CurrentAction>(null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [activityError, setActivityError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const refreshInFlight = useRef(false);
  const actionGate = useRef(createInFlightGate());

  const refresh = useCallback(async () => {
    if (refreshInFlight.current) {
      return;
    }
    refreshInFlight.current = true;
    try {
      const [nextStatus, nextActivity] = await Promise.allSettled([getStatus(), getActivity()]);
      if (nextStatus.status === "fulfilled") {
        setStatus(nextStatus.value);
        setStatusError(null);
      } else {
        setStatusError("No se ha podido obtener el estado de BetX.");
      }
      if (nextActivity.status === "fulfilled") {
        setActivity(nextActivity.value);
        setActivityError(null);
      } else {
        setActivityError("No se ha podido obtener la actividad reciente.");
      }
    } finally {
      refreshInFlight.current = false;
      setInitialLoading(false);
    }
  }, []);

  const runAction = useCallback(async (
    actionName: Exclude<CurrentAction, null>,
    action: () => Promise<InterfaceStatusView>
  ) => {
    if (actionGate.current.isRunning()) {
      return;
    }
    if (actionName === "activate") {
      if (!confirmActivation(window.confirm)) {
        return;
      }
    }
    await actionGate.current.run(async () => {
      setCurrentAction(actionName);
      setActionError(null);
      try {
        const nextStatus = await action();
        setStatus(nextStatus);
        await refresh();
      } catch (exc) {
        setActionError(actionName === "activate"
          ? "No se ha podido activar BetX. Intentalo de nuevo."
          : "No se ha podido pausar BetX. Intentalo de nuevo.");
      } finally {
        setCurrentAction(null);
      }
    });
  }, [refresh]);

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => {
      void refresh();
    }, 10_000);
    return () => window.clearInterval(timer);
  }, [refresh]);

  return {
    status,
    activity,
    initialLoading,
    actionPending: currentAction !== null,
    currentAction,
    statusError,
    activityError,
    actionError,
    refresh,
    activate: () => runAction("activate", activateBetx),
    pause: () => runAction("pause", pauseBetx)
  };
}
