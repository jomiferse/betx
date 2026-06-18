import type { ActivityItem, InterfaceStatusView } from "../types/interface";

const API_ROOT = "/api/v1/interface";

async function readJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_ROOT}${path}`, init);
  if (!response.ok) {
    throw new Error("No se pudo actualizar BetX.");
  }
  return response.json() as Promise<T>;
}

export function getStatus(): Promise<InterfaceStatusView> {
  return readJson<InterfaceStatusView>("/status");
}

export function getActivity(): Promise<ActivityItem[]> {
  return readJson<ActivityItem[]>("/activity");
}

export function activateBetx(): Promise<InterfaceStatusView> {
  return readJson<InterfaceStatusView>("/activate", { method: "POST" });
}

export function pauseBetx(): Promise<InterfaceStatusView> {
  return readJson<InterfaceStatusView>("/pause", { method: "POST" });
}
