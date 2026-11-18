import { apiFetch } from "./apiClient";
import { checkResponse } from "./apiResponse";
import type { PerformanceHistory } from "../types/performance";

// Load server-calculated bests through the staff or authenticated-athlete endpoint.
export async function getPerformanceHistory(athleteId: number | undefined, portal: boolean, signal?: AbortSignal): Promise<PerformanceHistory> {
  if (!portal && (!athleteId || !Number.isSafeInteger(athleteId) || athleteId < 1)) throw new Error("A valid athlete ID is required.");
  const url = portal ? "/api/portal/history" : `/api/athletes/${athleteId}/history`;
  const response = await apiFetch(url, { signal });
  await checkResponse(response, "Personal bests could not be loaded. Please try again.");
  return response.json();
}
