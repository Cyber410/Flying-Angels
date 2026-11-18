// Combines event results with athlete demographics, preserving archived history.
import { apiFetch } from "./apiClient";
import { checkResponse } from "./apiResponse";
import { eventParticipants, participantAttempts } from "../utils/eventParticipants";
import { ageGroupAtEvent, displayGender } from "../utils/athleteDisplay";
import { parseEventResult } from "../utils/eventResult";
import { sampleEventResults, sampleMeetProfile } from "../data/sampleMeet";
import type { Athlete } from "../types/athlete";
import type { EventProfile, MeetEvent } from "../types/event";
import type { OverviewResult, ResultsOverview } from "../types/results";

// Combine event attempts and athlete demographics into the result overview.
function assembleOverview(events: MeetEvent[], profiles: EventProfile[], athletes: Athlete[]): ResultsOverview {
  const athleteById = new Map(athletes.map(/* Index each athlete by its ID. */ (athlete) => [athlete.id, athlete]));
  const sharedResults: OverviewResult[] = [];
  const allAttempts: OverviewResult[] = [];

  for (const profile of profiles) {
    const event = profile.event;
    for (const participant of eventParticipants(profile)) {
      if (!participant.result?.trim()) continue;
      const athlete = athleteById.get(participant.athleteId);
      const row: OverviewResult = {
        id: `shared:${event.id}:${participant.resultId}`,
        athleteId: participant.athleteId, firstName: participant.firstName, lastName: participant.lastName,
        gender: displayGender(athlete?.gender), ageGroup: ageGroupAtEvent(athlete?.dateOfBirth, event.eventDate),
        eventId: event.id, eventName: event.name, categoryId: event.category.id, categoryName: event.category.name,
        meetId: event.meetId, meetName: event.meetName, eventDate: event.eventDate,
        result: parseEventResult(participant.result, event)?.result ?? participant.result,
        score: participant.score, source: "shared",
      };
      sharedResults.push(row);

      const attempts = participantAttempts(participant);
      if (attempts.length === 0) allAttempts.push(row);
      for (const attempt of attempts) {
        allAttempts.push({
          ...row, id: `attempt:${event.id}:${participant.athleteId}:${attempt.id}`,
          result: parseEventResult(attempt.result, event)?.result ?? attempt.result,
          score: attempt.score, source: "shared",
        });
      }
    }
  }

  // Order results by event date, athlete name, event name, and stable ID.
  const compareRows = (a: OverviewResult, b: OverviewResult) =>
    b.eventDate.localeCompare(a.eventDate) || a.lastName.localeCompare(b.lastName) ||
    a.firstName.localeCompare(b.firstName) || a.eventName.localeCompare(b.eventName) || a.id.localeCompare(b.id);

  return {
    events, sharedResults: sharedResults.sort(compareRows), allAttempts: allAttempts.sort(compareRows),
    warnings: [], failedEvents: [],
  };
}

type SnapshotResult = {
  resultId: number; athleteId: number; firstName: string; lastName: string;
  gender: string | null; dateOfBirth: string | null; eventId: number;
  categoryId: number; categoryName: string; result: string | null; score: number | null;
};
type ResultsSnapshot = { events: MeetEvent[]; results: SnapshotResult[] };

// Fetch event context, attempts, and demographics together instead of requesting every event profile.
export async function getResultsOverview(signal?: AbortSignal): Promise<ResultsOverview> {
  const response = await apiFetch("/api/results/overview", { signal, cache: "no-store" });
  await checkResponse(response, "Results could not be loaded. Please try again.");
  const snapshot: ResultsSnapshot = await response.json();
  signal?.throwIfAborted();
  const profiles = new Map<number, EventProfile>(snapshot.events.map(event => [event.id, { event, results: [] }]));
  const athletes = new Map<number, Athlete>();
  // Preserve the existing best-attempt selection, units, age labels, and stable result ordering.
  for (const row of snapshot.results) {
    profiles.get(row.eventId)?.results.push(row);
    athletes.set(row.athleteId, { id: row.athleteId, firstName: row.firstName, lastName: row.lastName,
      gender: row.gender, dateOfBirth: row.dateOfBirth });
  }
  return assembleOverview(snapshot.events, [...profiles.values()], [...athletes.values()]);
}

// Builds the opt-in read-only sample results without API writes.
export function getSampleResultsOverview(): ResultsOverview {
  const profiles = sampleMeetProfile.events.map(/* Build profiles from each event. */ (event) => ({ event, results: sampleEventResults[event.id] ?? [] }));
  return assembleOverview(sampleMeetProfile.events, profiles, []);
}
