import { beforeEach, expect, it, vi } from "vitest";
import { apiFetch } from "./apiClient";
import { getStaffRankings } from "./rankingsService";
import type { StaffRankingFilters } from "./rankingsService";

vi.mock("./apiClient");
beforeEach(/* Reset the request boundary between transport checks. */ () => { vi.resetAllMocks(); vi.mocked(apiFetch).mockResolvedValue(new Response("{}")); });
const filters: StaffRankingFilters = { categoryId: "2", meetId: "3", eventId: "10", gender: "Female", ageGroup: "", minAge: "12", maxAge: "15", ageAsOf: "2026-10-01" };

it("sends numeric age filters and pagination without unsupported seasons", /* Verify the backend query contract. */ async () => {
  await getStaffRankings(filters, 2);
  const query = new URL(String(vi.mocked(apiFetch).mock.calls[0][0]), "http://example.test").searchParams;
  expect(query.get("page")).toBe("2"); expect(query.get("eventId")).toBe("10");
  expect(query.get("minAge")).toBe("12"); expect(query.get("ageAsOf")).toBe("2026-10-01"); expect(query.has("season")).toBe(false);
});

it("sends an age band without conflicting numeric bounds", /* Verify that only one age-filter mode reaches the backend. */ async () => {
  await getStaffRankings({ ...filters, ageGroup: "Academy Junior" }, 0);
  const query = new URL(String(vi.mocked(apiFetch).mock.calls[0][0]), "http://example.test").searchParams;
  expect(query.get("ageGroup")).toBe("Academy Junior"); expect(query.has("minAge")).toBe(false); expect(query.has("maxAge")).toBe(false);
});

it("includes descending rank order with the page request", /* Verify sorting is applied to the complete backend result set. */ async () => {
  await getStaffRankings({ ...filters, rankOrder: "DESC" }, 1);
  const query = new URL(String(vi.mocked(apiFetch).mock.calls[0][0]), "http://example.test").searchParams;
  expect(query.get("rankOrder")).toBe("DESC"); expect(query.get("page")).toBe("1");
});
