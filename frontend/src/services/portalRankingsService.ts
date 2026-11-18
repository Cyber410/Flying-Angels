import { apiFetch } from "./apiClient";
import { checkResponse } from "./apiResponse";
import type { PerformanceUnit } from "../types/performance";

export type RankingEvent = {
  id: number; name: string; eventDate: string; meetId: number; meetName: string;
  categoryId: number; categoryName: string; unit: PerformanceUnit; rankingOrder: "ASC" | "DESC";
};
export type RankingRow = {
  position: number; firstName: string; lastName: string; result: string | null; score: number;
  profileAccessible: boolean; athleteId: number | null; ageGroup: string | null; unit: PerformanceUnit;
};
export type PortalRanking = {
  category: { name: string; unit: PerformanceUnit; rankingOrder: "ASC" | "DESC" };
  status: "AVAILABLE" | "NO_EVENT_RESULTS" | "NO_MATCHING_ATHLETES";
  content: RankingRow[]; page: number; size: number; totalElements: number; totalPages: number;
};

// Load only the authenticated athlete's assigned or previously competed meet events.
export async function getPortalRankingEvents(signal?: AbortSignal): Promise<RankingEvent[]> {
  const response = await apiFetch("/api/portal/rankings/events", { signal });
  await checkResponse(response, "Unable to load your ranking events.");
  return response.json();
}

export type PortalRankingFilters = { gender?: string; minAge?: number; maxAge?: number };

// Ask the server for ranked performances within a single authorized competition event.
export async function getPortalRankings(event: RankingEvent, page: number, signal?: AbortSignal, filters: PortalRankingFilters = {}): Promise<PortalRanking> {
  const query = new URLSearchParams({ categoryId: String(event.categoryId), meetId: String(event.meetId),
    eventId: String(event.id), ageAsOf: event.eventDate, page: String(page), size: "25" });
  if (filters.gender) query.set("gender", filters.gender);
  if (filters.minAge !== undefined) query.set("minAge", String(filters.minAge));
  if (filters.maxAge !== undefined) query.set("maxAge", String(filters.maxAge));
  const response = await apiFetch(`/api/portal/rankings?${query}`, { signal });
  await checkResponse(response, "Unable to load rankings for this event.");
  return response.json();
}
