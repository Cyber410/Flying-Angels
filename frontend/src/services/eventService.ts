// Event definitions, categories, participants, and shared result requests.
import type { EventCategory, EventProfile, EventRequest, EventResult, MeetEvent, EventParticipation } from "../types/event";
import { checkResponse } from "./apiResponse";

// Loads the available discipline definitions and ranking rules.
export async function getEventCategories(): Promise<EventCategory[]> {
  const response = await fetch("/api/event-categories");
  await checkResponse(response, "Unable to load event types.");
  return response.json();
}

// Loads event summaries used to assemble history and result views.
export async function getEvents(signal?: AbortSignal): Promise<MeetEvent[]> {
  const response = signal ? await fetch("/api/events", { signal }) : await fetch("/api/events");
  await checkResponse(response, "Unable to load events.");
  return response.json();
}

// Loads the participants and shared results for one event.
export async function getEventProfile(id: number, signal?: AbortSignal): Promise<EventProfile> {
  const response = signal ? await fetch(`/api/events/${id}`, { signal }) : await fetch(`/api/events/${id}`);
  await checkResponse(response, "Unable to load event participants.");
  return response.json();
}

// Adds an event definition to its meet and selected date.
export async function createEvent(request: EventRequest): Promise<MeetEvent> {
  const response = await fetch("/api/events", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  await checkResponse(response, "Unable to create event.");
  return response.json();
}

// Saves the first shared result and participation record together.
export async function createParticipantResult(
  eventId: number, athleteId: number, result: string, score: number, clientId?: string,
): Promise<EventResult> {
  const response = await fetch(`/api/events/${eventId}/results`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ athleteId, result, score, clientId }),
  });
  await checkResponse(response, "Unable to save the athlete's first result.");
  return response.json();
}

// Assignments are shared immediately, even before an athlete has a result.
export async function assignEventAthletes(eventId: number, athleteIds: number[]): Promise<EventParticipation[]> {
  const response = await fetch(`/api/events/${eventId}/participants/bulk`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ athleteIds }),
  });
  await checkResponse(response, "Unable to assign athletes. No assignments were saved.");
  return response.json();
}

export async function updateEventAttempt(eventId: number, resultId: number, athleteId: number, result: string, score: number): Promise<EventResult> {
  const response = await fetch(`/api/events/${eventId}/results/${resultId}`, {
    method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ athleteId, result, score }),
  });
  await checkResponse(response, "Unable to update this attempt.");
  return response.json();
}

export async function removeEventParticipant(eventId: number, athleteId: number): Promise<void> {
  const response = await fetch(`/api/events/${eventId}/participants/${athleteId}`, { method: "DELETE" });
  await checkResponse(response, "Unable to remove athlete from event.");
}

export async function readEventNote(eventId: number, athleteId?: number): Promise<string> {
  const response = await fetch(noteUrl(eventId, athleteId));
  await checkResponse(response, "Unable to load the note.");
  const data: { text: string } = await response.json();
  return data.text;
}

export async function writeEventNote(eventId: number, text: string, athleteId?: number): Promise<string> {
  const response = await fetch(noteUrl(eventId, athleteId), {
    method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ text }),
  });
  await checkResponse(response, "Unable to save the note.");
  const data: { text: string } = await response.json();
  return data.text;
}

// Migration uses compare-and-set on the server, so a teammate's note cannot be overwritten.
export async function migrateEventNote(eventId: number, text: string): Promise<void> {
  const response = await fetch(`/api/events/${eventId}/note/migrate-browser-data`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ text }),
  });
  await checkResponse(response, "The saved note could not be added. It has been retained.");
}

function noteUrl(eventId: number, athleteId?: number) {
  return athleteId === undefined ? `/api/events/${eventId}/note` : `/api/events/${eventId}/participants/${athleteId}/note`;
}
