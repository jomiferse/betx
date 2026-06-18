import { describe, expect, test } from "vitest";
import { formatActivityTime, formatResultLabel, formatSignedMoney } from "./format";
import { translateResult } from "./translations";

describe("format", () => {
  test("formats relative and local activity dates", () => {
    const now = new Date("2026-06-18T10:05:00Z");

    expect(formatActivityTime("2026-06-18T10:01:00Z", now)).toBe("Hace 4 minutos");
    expect(formatActivityTime("2026-06-18T08:05:00Z", now)).toContain("18/6/26");
  });

  test("formats won and lost financial results without inventing missing values", () => {
    expect(formatResultLabel("WIN", 4, translateResult)).toContain("Ganada");
    expect(formatResultLabel("WIN", 4, translateResult)).toContain("+");
    expect(formatResultLabel("LOSE", -1, translateResult)).toContain("Perdida");
    expect(formatResultLabel(null, null, translateResult)).toBe("-");
  });

  test("formats signed money", () => {
    expect(formatSignedMoney(4)).toContain("+");
    expect(formatSignedMoney(-1)).toContain("-");
  });
});
