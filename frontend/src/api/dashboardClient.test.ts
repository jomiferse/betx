import { afterEach, describe, expect, test, vi } from "vitest";
import { getDashboardData } from "./dashboardClient";

describe("dashboardClient", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  test("uses versioned dashboard analytics endpoints", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({})
    });
    vi.stubGlobal("fetch", fetchMock);

    await getDashboardData("30D", {
      page: 1,
      size: 50,
      status: "SETTLED",
      result: "WIN",
      strategy: "value-football",
      search: "real",
      sort: "pnl",
      order: "desc"
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/v1/dashboard/summary?range=30D", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/v1/dashboard/equity?range=30D", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/v1/dashboard/daily-pnl?range=30D", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(4, "/api/v1/dashboard/breakdown/strategy?range=30D", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(
      5,
      "/api/v1/dashboard/trades?range=30D&page=1&size=50&status=SETTLED&result=WIN&strategy=value-football&search=real&sort=pnl&order=desc",
      undefined
    );
  });
});
