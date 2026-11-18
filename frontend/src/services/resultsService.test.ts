import { beforeEach, expect, it, vi } from "vitest";
import { apiFetch } from "./apiClient";
import { getResultsOverview } from "./resultsService";
import type { MeetEvent } from "../types/event";

vi.mock("./apiClient");
// Reset the transport boundary before each snapshot check.
beforeEach(() => vi.resetAllMocks());
const sprint: MeetEvent = { id: 10, name: "100m", category: { id: 2, name: "100m", rankingOrder: "ASC" }, rankingOrder: "ASC", eventDate: "2026-10-24", meetId: 3, meetName: "Demo Meet", participantCount: 1, location: null, eventType: "Track", externalUrl: null };
const jump: MeetEvent = { ...sprint, id: 11, name: "Long Jump", category: { id: 4, name: "Long Jump", rankingOrder: "DESC" }, rankingOrder: "DESC", eventType: "Field" };
const athlete = { athleteId: 7, firstName: "Fictional", lastName: "Runner", gender: "Female", dateOfBirth: "2010-10-25" };

// One snapshot must preserve both best-score directions and every saved attempt.
it("loads once and preserves best results, attempts, demographics, and event-date ages", async () => {
  vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify({ events: [sprint, jump], results: [
    { ...athlete, resultId: 1, eventId: 10, categoryId: 2, categoryName: "100m", result: "12.4s", score: 12.4 },
    { ...athlete, resultId: 2, eventId: 10, categoryId: 2, categoryName: "100m", result: "12.1s", score: 12.1 },
    { ...athlete, resultId: 3, eventId: 11, categoryId: 4, categoryName: "Long Jump", result: "5.8m", score: 5.8 },
    { ...athlete, resultId: 4, eventId: 11, categoryId: 4, categoryName: "Long Jump", result: "6.2m", score: 6.2 },
  ] })));
  const controller = new AbortController(); const result = await getResultsOverview(controller.signal);
  expect(apiFetch).toHaveBeenCalledExactlyOnceWith("/api/results/overview", { signal: controller.signal, cache: "no-store" });
  expect(result.sharedResults).toHaveLength(2); expect(result.allAttempts).toHaveLength(4);
  expect(result.sharedResults.find(row => row.eventId === 10)?.result).toBe("12.1s");
  expect(result.sharedResults.find(row => row.eventId === 11)?.result).toBe("6.2m");
  expect(result.sharedResults.every(row => row.gender === "Female" && row.ageGroup === "15-18")).toBe(true);
  expect(result.failedEvents).toEqual([]);
});

// Empty results need no event-by-event or athlete-directory fallback requests.
it("returns an empty overview from a single successful request", async () => {
  vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify({ events: [sprint], results: [] })));
  const result = await getResultsOverview();
  expect(result.events).toEqual([sprint]); expect(result.sharedResults).toEqual([]); expect(result.allAttempts).toEqual([]);
  expect(apiFetch).toHaveBeenCalledOnce();
});

// Show a useful request failure instead of silently presenting incomplete results.
it("reports backend errors without falling back to the slow request chain", async () => {
  vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify({ message: "Results unavailable" }), { status: 503 }));
  await expect(getResultsOverview()).rejects.toThrow("Results unavailable");
  expect(apiFetch).toHaveBeenCalledOnce();
});

// Ignore an abandoned snapshot after the user navigates away from Results.
it("respects an aborted request before assembling result rows", async () => {
  const controller = new AbortController(); controller.abort();
  vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify({ events: [], results: [] })));
  await expect(getResultsOverview(controller.signal)).rejects.toMatchObject({ name: "AbortError" });
});
