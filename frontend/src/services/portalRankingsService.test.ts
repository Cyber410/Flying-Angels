import { beforeEach, expect, it, vi } from "vitest";
import { apiFetch } from "./apiClient";
import { getPortalRankings } from "./portalRankingsService";
import type { RankingEvent } from "./portalRankingsService";
vi.mock("./apiClient");
// Reset requests before checking the scoped query contract.
beforeEach(() => { vi.resetAllMocks(); vi.mocked(apiFetch).mockResolvedValue(new Response("{}")); });
const event: RankingEvent = { id: 10, name: "100m", eventDate: "2026-10-18", meetId: 3, meetName: "Demo Meet", categoryId: 2, categoryName: "100m", unit: "SECONDS", rankingOrder: "ASC" };
// Send age bounds, including zero, without relying on unconfigured named backend bands.
it("sends gender and age bounds using the selected event date", async () => {
  await getPortalRankings(event, 0, undefined, { gender: "Female", minAge: 0, maxAge: 4 });
  const query = new URL(String(vi.mocked(apiFetch).mock.calls[0][0]), "http://example.test").searchParams;
  expect(query.get("gender")).toBe("Female"); expect(query.get("minAge")).toBe("0"); expect(query.get("maxAge")).toBe("4");
  expect(query.get("ageAsOf")).toBe(event.eventDate); expect(query.get("eventId")).toBe("10"); expect(query.has("ageGroup")).toBe(false);
});
// An unfiltered cohort must omit bounds rather than constrain ages accidentally.
it("omits optional cohort filters by default", async () => {
  await getPortalRankings(event, 1);
  const query = new URL(String(vi.mocked(apiFetch).mock.calls[0][0]), "http://example.test").searchParams;
  expect(query.has("gender")).toBe(false); expect(query.has("minAge")).toBe(false); expect(query.has("maxAge")).toBe(false); expect(query.get("page")).toBe("1");
});
