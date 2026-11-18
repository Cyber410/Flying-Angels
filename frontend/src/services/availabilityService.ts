import { apiFetch } from "./apiClient";
import { checkResponse } from "./apiResponse";

export type AvailabilityStatus = "PENDING" | "AWAITING_RESPONSE" | "AVAILABLE" | "UNAVAILABLE";
export type AvailabilityAnswer = "AVAILABLE" | "UNAVAILABLE";
export type ResponseChannel = "PHONE" | "WHATSAPP" | "EMAIL" | "TEXT" | "IN_PERSON" | "OTHER";
export type AssignedEvent = { eventId: number; eventName: string; categoryId: number; categoryName: string; eventDate: string };
export type AvailabilityRequest = {
  id: number; athleteId: number; firstName: string; lastName: string; meetId: number; meetName: string;
  startDate: string; endDate: string; location: string | null; active: boolean; canRespond: boolean;
  status: AvailabilityStatus; version: number; assignmentRevision: number; lastAnswer: AvailabilityAnswer | null;
  answeredRevision: number | null; respondedAt: string | null; events: AssignedEvent[];
  responseDeadline: string | null; timeZone: string;
};
export type ResponseAudit = { id: number; status: AvailabilityAnswer; communicationMethod: ResponseChannel | "PORTAL";
  assignmentRevision: number; respondedAt: string; recordedBy: string; staffUserId: number | null;
  athleteUserId: number | null; note: string | null };
export type AvailabilityRow = Omit<AvailabilityRequest, "location" | "active" | "timeZone"> & {
  assignedEventCount: number; latestResponse: ResponseAudit | null;
};
export type AvailabilityFilters = { meetId: string; status: string; view: "UPCOMING" | "PAST" | "ALL"; search: string };
export type AvailabilityOverview = { content: AvailabilityRow[]; page: number; totalElements: number; totalPages: number;
  summary: { total: number; available: number; unavailable: number; awaitingResponse: number } };

// Distinguish a stale proposal from an ordinary request failure so a fresh confirmation is required.
export class AvailabilityConflict extends Error {}

// Fetch only the signed-in athlete's upcoming nominated meets.
export async function getMyAvailability(signal?: AbortSignal): Promise<AvailabilityRequest[]> {
  return readJson("/api/portal/availability", signal);
}

// Reload the request and its concurrency tokens before showing a response form.
export async function getAvailability(id: number, portal: boolean, signal?: AbortSignal): Promise<AvailabilityRequest> {
  return readJson(`${portal ? "/api/portal/availability" : "/api/availability"}/${id}`, signal);
}

// Submit the displayed proposal revision; never silently retry a conflicting response.
export async function saveAvailability(request: AvailabilityRequest, answer: AvailabilityAnswer, portal: boolean,
  external?: { communicationMethod: ResponseChannel; note: string }): Promise<AvailabilityRequest> {
  const response = await apiFetch(`${portal ? "/api/portal/availability" : "/api/availability"}/${request.id}/response`, {
    method: "PUT", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status: answer, version: request.version, assignmentRevision: request.assignmentRevision,
      ...(!portal ? external : {}) }),
  });
  if (response.status === 409) {
    try { await checkResponse(response, "This request changed. Reload and confirm again."); }
    catch (failure) { throw new AvailabilityConflict(failure instanceof Error ? failure.message : "Reload and confirm again."); }
  }
  await checkResponse(response, "Unable to save availability. Your previous response has not been replaced.");
  return response.json();
}

// Request the staff's filtered, paginated response overview and meet-wide summary.
export async function getAvailabilityOverview(filters: AvailabilityFilters, page: number, signal?: AbortSignal): Promise<AvailabilityOverview> {
  const query = new URLSearchParams({ view: filters.view, search: filters.search, page: String(page), size: "25" });
  if (filters.meetId) query.set("meetId", filters.meetId);
  if (filters.status) query.set("status", filters.status);
  return readJson(`/api/availability?${query}`, signal);
}

// Read the staff-only audit trail without exposing external notes to the athlete portal.
export async function getAvailabilityHistory(id: number, signal?: AbortSignal): Promise<ResponseAudit[]> {
  return readJson(`/api/availability/${id}/responses`, signal);
}

// Share cancellation and error handling across read-only availability requests.
async function readJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await apiFetch(url, { signal, cache: "no-store" });
  await checkResponse(response, "Unable to load availability.");
  return response.json();
}

// Keep pending status labels consistent across the portal and staff overview.
export function availabilityLabel(status: AvailabilityStatus): string {
  return status === "AVAILABLE" ? "Available" : status === "UNAVAILABLE" ? "Unavailable" : "Awaiting response";
}

// Display an explicit local date and time for response deadlines and saved replies.
export function availabilityDateTime(value: string, timeZone?: string): string {
  return new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short", ...(timeZone ? { timeZone } : {}) });
}
