import type { EventCategory, EventProfile, EventRequest, EventResult, MeetEvent } from "../types/event";
import { checkResponse } from "./apiResponse";

export async function getEventCategories(): Promise<EventCategory[]> {
  const response = await fetch("/api/event-categories");
  await checkResponse(response, "Unable to load event types.");
  return response.json();
}

export async function getEvents(signal?: AbortSignal): Promise<MeetEvent[]> {
  const response = signal ? await fetch("/api/events", { signal }) : await fetch("/api/events");
  await checkResponse(response, "Unable to load events.");
  return response.json();
}

export async function getEventProfile(id: number, signal?: AbortSignal): Promise<EventProfile> {
  const response = signal ? await fetch(`/api/events/${id}`, { signal }) : await fetch(`/api/events/${id}`);
  await checkResponse(response, "Unable to load event participants.");
  return response.json();
}

export async function createEvent(request: EventRequest): Promise<MeetEvent> {
  const response = await fetch("/api/events", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  await checkResponse(response, "Unable to create event.");
  return response.json();
}

export async function createParticipantResult(
  eventId: number, athleteId: number, result: string, score: number,
): Promise<EventResult> {
  const response = await fetch(`/api/events/${eventId}/results`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ athleteId, result, score }),
  });
  await checkResponse(response, "Unable to save the athlete's first result.");
  return response.json();
}

export async function saveParticipantResult(
  eventId: number, athleteId: number, result: string, score: number,
): Promise<EventResult> {
  const response = await fetch(`/api/events/${eventId}/participants/${athleteId}/result`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ result, score }),
  });
  await checkResponse(response, "Unable to save result.");
  return response.json();
}

export async function removeParticipant(eventId: number, resultId: number): Promise<void> {
  const response = await fetch(`/api/events/${eventId}/results/${resultId}`, { method: "DELETE" });
  await checkResponse(response, "Unable to remove athlete from event.");
}
