// Combines event results with athlete demographics, preserving archived history.
import { getAthletes } from "./athleteService";
import { getEvents } from "./eventService";
import { loadEventProfiles } from "./eventProfileService";
import { eventParticipants, participantAttempts } from "../utils/eventParticipants";
import { ageGroupAtEvent, displayGender } from "../utils/athleteDisplay";
import { parseEventResult } from "../utils/eventResult";
import { sampleEventResults, sampleMeetProfile } from "../data/sampleMeet";
import type { Athlete } from "../types/athlete";
import type { EventProfile, MeetEvent } from "../types/event";
import type { OverviewResult, ResultsOverview } from "../types/results";

function assembleOverview(events: MeetEvent[], profiles: EventProfile[], athletes: Athlete[]): ResultsOverview {
  const athleteById = new Map(athletes.map((athlete) => [athlete.id, athlete]));
  const sharedResults: OverviewResult[] = [];
  const allAttempts: OverviewResult[] = [];
  const warnings = new Set<string>();

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

  const compareRows = (a: OverviewResult, b: OverviewResult) =>
    b.eventDate.localeCompare(a.eventDate) || a.lastName.localeCompare(b.lastName) ||
    a.firstName.localeCompare(b.firstName) || a.eventName.localeCompare(b.eventName) || a.id.localeCompare(b.id);

  return {
    events, sharedResults: sharedResults.sort(compareRows), allAttempts: allAttempts.sort(compareRows),
    warnings: [...warnings], failedEvents: [],
  };
}

// Loads result context and demographics, retaining partial-failure warnings.
export async function getResultsOverview(signal?: AbortSignal): Promise<ResultsOverview> {
  const events = await getEvents(signal);
  const populatedEvents = events.filter((event) => event.participantCount > 0);
  if (populatedEvents.length === 0) return assembleOverview(events, [], []);

  const [athleteOutcome, profileOutcome] = await Promise.all([
    getAthletes("all").then(
      (athletes) => ({ athletes, failed: false }),
      () => ({ athletes: [] as Athlete[], failed: true }),
    ),
    loadEventProfiles(populatedEvents, signal),
  ]);
  signal?.throwIfAborted();
  if (profileOutcome.profiles.length === 0) throw new Error("Results could not be loaded for any event. Please try again.");
  const overview = assembleOverview(events, profileOutcome.profiles, athleteOutcome.athletes);
  overview.failedEvents = profileOutcome.failedEvents;
  if (athleteOutcome.failed) overview.warnings.push("Athlete details could not be loaded. Age group and gender are shown as Not provided.");
  return overview;
}

// Builds the opt-in read-only sample results without API writes.
export function getSampleResultsOverview(): ResultsOverview {
  const profiles = sampleMeetProfile.events.map((event) => ({ event, results: sampleEventResults[event.id] ?? [] }));
  return assembleOverview(sampleMeetProfile.events, profiles, []);
}
