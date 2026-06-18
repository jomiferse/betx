import { afterEach, describe, expect, test, vi } from "vitest";
import { activateBetx, getActivity, getStatus, pauseBetx } from "./interfaceClient";

describe("interfaceClient", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  test("uses versioned interface endpoints", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({})
    });
    vi.stubGlobal("fetch", fetchMock);

    await getStatus();
    await getActivity();
    await activateBetx();
    await pauseBetx();

    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/v1/interface/status", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/v1/interface/activity", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/v1/interface/activate", { method: "POST" });
    expect(fetchMock).toHaveBeenNthCalledWith(4, "/api/v1/interface/pause", { method: "POST" });
  });
});
