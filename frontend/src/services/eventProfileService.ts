import { getEventProfile } from "./eventService";
import type { EventProfile, FailedEventProfile, MeetEvent } from "../types/event";

// The API has no combined history/results query. Bound requests for both pages.
export async function loadEventProfiles(events: MeetEvent[], signal?: AbortSignal) {
  const profiles: EventProfile[] = [];
  const failedEvents: FailedEventProfile[] = [];
  for (let offset = 0; offset < events.length; offset += 6) {
    signal?.throwIfAborted();
    const batch = events.slice(offset, offset + 6);
    const outcomes = await Promise.allSettled(batch.map((event) => getEventProfile(event.id, signal)));
    signal?.throwIfAborted();
    outcomes.forEach((outcome, index) => {
      const event = batch[index];
      if (outcome.status === "fulfilled") profiles.push(outcome.value);
      else failedEvents.push({
        eventId: event.id, eventName: event.name, meetName: event.meetName,
        message: outcome.reason instanceof Error ? outcome.reason.message : "Unable to load event results.",
      });
    });
  }
  return { profiles, failedEvents };
}
