import { describe, expect, test } from "vitest";
import { translateOperationStatus, translateResult, translateSelection } from "./translations";

describe("translations", () => {
  test("translates product-facing selections", () => {
    expect(translateSelection("The Draw")).toBe("Empate");
  });

  test("translates operation statuses and results", () => {
    expect(translateOperationStatus("PENDING")).toBe("Pendiente");
    expect(translateOperationStatus("SETTLED")).toBe("Liquidada");
    expect(translateOperationStatus("CANCELLED")).toBe("Cancelada");
    expect(translateOperationStatus("REJECTED")).toBe("Rechazada");
    expect(translateResult("WIN")).toBe("Ganada");
    expect(translateResult("LOSE")).toBe("Perdida");
  });
});
