export const activationConfirmationText =
  "¿Quieres activar BetX? Empezará a analizar mercados y podrá ejecutar operaciones según la configuración actual.";

export function confirmActivation(confirmFn: (message: string) => boolean): boolean {
  return confirmFn(activationConfirmationText);
}

export function createInFlightGate() {
  let inFlight = false;

  return {
    isRunning: () => inFlight,
    async run<T>(operation: () => Promise<T>): Promise<T | undefined> {
      if (inFlight) {
        return undefined;
      }
      inFlight = true;
      try {
        return await operation();
      } finally {
        inFlight = false;
      }
    }
  };
}
