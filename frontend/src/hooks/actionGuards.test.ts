import { describe, expect, test, vi } from "vitest";
import { activationConfirmationText, confirmActivation, createInFlightGate } from "./actionGuards";

describe("actionGuards", () => {
  test("asks for confirmation before activation", () => {
    const confirmFn = vi.fn().mockReturnValue(true);

    expect(confirmActivation(confirmFn)).toBe(true);
    expect(confirmFn).toHaveBeenCalledWith(activationConfirmationText);
  });

  test("prevents duplicate in-flight actions", async () => {
    const gate = createInFlightGate();
    let finishFirstAction: (() => void) | undefined;
    const firstAction = vi.fn(() => new Promise<void>((resolve) => {
      finishFirstAction = resolve;
    }));
    const secondAction = vi.fn(() => Promise.resolve());

    const first = gate.run(firstAction);
    const second = gate.run(secondAction);
    finishFirstAction?.();
    await first;
    await second;

    expect(firstAction).toHaveBeenCalledTimes(1);
    expect(secondAction).not.toHaveBeenCalled();
  });
});
