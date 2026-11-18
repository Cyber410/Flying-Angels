import { apiFetch } from "./apiClient";
import { checkResponse } from "./apiResponse";
import type { PerformanceUnit } from "../types/performance";
import type { RankingEvent } from "./portalRankingsService";

export type RankingCategory = { id: number; name: string; unit: PerformanceUnit; rankingOrder: "ASC" | "DESC" | null };
export type RankingMeet = { id: number; name: string; startDate: string; endDate: string };
export type AgeConfiguration = { configured: boolean; ageRule: string; bands: { label: string; minAge: number; maxAge: number }[] };
export type RankingChoices = { categories: RankingCategory[]; meets: RankingMeet[]; events: RankingEvent[]; ages: AgeConfiguration };
export type StaffRankingFilters = { categoryId: string; meetId: string; eventId: string; gender: string;
  ageGroup: string; minAge: string; maxAge: string; ageAsOf: string; rankOrder?: "ASC" | "DESC" };
export type StaffRankingRow = { position: number; athleteId: number; firstName: string; lastName: string;
  gender: string | null; ageAtReference: number | null; archived: boolean; resultId: number;
  result: string | null; resultLabel: string | null; score: number; meetId: number; meetName: string;
  eventId: number; eventName: string; eventDate: string; profileAccessible: boolean; ageGroup: string | null };
export type StaffRankings = { category: RankingCategory; status: "AVAILABLE" | "NO_EVENT_RESULTS" | "NO_MATCHING_ATHLETES";
  content: StaffRankingRow[]; page: number; size: number; totalElements: number; totalPages: number };

// Load server-defined disciplines, meets, event instances, and club age bands together.
export async function getRankingChoices(signal?: AbortSignal): Promise<RankingChoices> {
  const [categories, meets, events, ages] = await Promise.all([
    readJson<RankingCategory[]>("/api/rankings/categories", signal), readJson<RankingMeet[]>("/api/rankings/meets", signal),
    readJson<RankingEvent[]>("/api/rankings/events", signal), readJson<AgeConfiguration>("/api/age-groups", signal),
  ]);
  return { categories, meets, events, ages };
}

// Fetch one staff ranking page using only supported filters and server-calculated positions.
export async function getStaffRankings(filters: StaffRankingFilters, page: number, signal?: AbortSignal): Promise<StaffRankings> {
  const query = new URLSearchParams({ categoryId: filters.categoryId, page: String(page), size: "25" });
  query.set("rankOrder", filters.rankOrder ?? "ASC");
  for (const key of ["meetId", "eventId", "gender", "ageAsOf"] as const) if (filters[key]) query.set(key, filters[key]);
  if (filters.ageGroup) query.set("ageGroup", filters.ageGroup);
  else for (const key of ["minAge", "maxAge"] as const) if (filters[key]) query.set(key, filters[key]);
  return readJson(`/api/rankings?${query}`, signal);
}

// Share error and cancellation handling across staff ranking reads.
async function readJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await apiFetch(url, { signal, cache: "no-store" });
  await checkResponse(response, "Unable to load rankings.");
  return response.json();
}
