export type BetxStatus = "ACTIVE" | "PAUSED" | "NEEDS_ATTENTION";

export interface InterfaceStatusView {
  status: BetxStatus;
  message: string;
  availableBalance: number | null;
  lastUpdatedAt: string;
  lastCycleAt: string | null;
  manualConfirmationEnabled: boolean;
}

export interface ActivityItem {
  id: string;
  event: string;
  selection: string;
  odds: number | null;
  amount: number | null;
  status: string;
  result: string | null;
  netPnl: number | null;
  updatedAt: string;
}
