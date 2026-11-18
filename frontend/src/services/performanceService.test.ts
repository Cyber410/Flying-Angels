import { afterEach, describe, expect, it, vi } from "vitest";
import { getPerformanceHistory } from "./performanceService";
import { apiFetch } from "./apiClient";
import { performanceHistory } from "../test/performanceTestData";
vi.mock("./apiClient");
afterEach(/* Clean up rendered content and mocks after each test. */ () => vi.resetAllMocks());

describe("performance history requests", /* Group the performance history requests checks. */ () => {
  it("uses a staff athlete ID for staff history", /* Verify that uses a staff athlete ID for staff history. */ async () => {
    vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify(performanceHistory())));
    await getPerformanceHistory(7, false);
    expect(apiFetch).toHaveBeenCalledWith("/api/athletes/7/history", { signal: undefined });
  });
  it("uses only the session-scoped path for the athlete portal", /* Verify that uses only the session-scoped path for the athlete portal. */ async () => {
    vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify(performanceHistory())));
    await getPerformanceHistory(999, true);
    expect(apiFetch).toHaveBeenCalledWith("/api/portal/history", { signal: undefined });
  });
  it("rejects invalid staff IDs before making a request", /* Verify that rejects invalid staff IDs before making a request. */ async () => {
    await expect(getPerformanceHistory(-1, false)).rejects.toThrow("valid athlete ID"); expect(apiFetch).not.toHaveBeenCalled();
  });
});
