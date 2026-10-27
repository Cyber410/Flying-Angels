import { getAthletes } from "./athleteService";
import { getEvents } from "./eventService";
import { loadEventProfiles } from "./eventProfileService";
import { getResultAttempts, includeSharedResult } from "./eventAttemptStore";
import { ageGroupAtEvent, displayGender } from "../utils/athleteDisplay";
import { parseEventResult } from "../utils/eventResult";
import { sampleEventResults, sampleMeetProfile } from "../data/sampleMeet";
import type { Athlete } from "../types/athlete";
import type { EventProfile, MeetEvent } from "../types/event";
import type { OverviewResult, ResultsOverview } from "../types/results";

function assembleOverview(events: MeetEvent[], profiles: EventProfile[], athletes: Athlete[], sample: boolean): ResultsOverview {
  const athleteById = new Map(athletes.map((athlete) => [athlete.id, athlete]));
  const sharedResults: OverviewResult[] = [];
  const allAttempts: OverviewResult[] = [];
  const warnings = new Set<string>();

  for (const { event, results } of profiles) {
    for (const participant of results) {
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

      let attempts = includeSharedResult([], participant);
      if (!sample) {
        try {
          attempts = getResultAttempts(event.id, participant);
        } catch {
          warnings.add("Some browser-only attempt histories could not be read. Shared results are still shown.");
        }
      }
      const sharedAttempt = attempts.find((attempt) => participant.score !== null && Math.abs(attempt.score - participant.score) < 0.00005);
      if (attempts.length === 0) allAttempts.push(row);
      for (const attempt of attempts) {
        allAttempts.push({
          ...row, id: `attempt:${event.id}:${participant.athleteId}:${attempt.id}`,
          result: parseEventResult(attempt.result, event)?.result ?? attempt.result,
          score: attempt.score, source: attempt.id === sharedAttempt?.id ? "shared" : "browser",
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

export async function getResultsOverview(signal?: AbortSignal): Promise<ResultsOverview> {
  const events = await getEvents(signal);
  const populatedEvents = events.filter((event) => event.participantCount > 0);
  if (populatedEvents.length === 0) return assembleOverview(events, [], [], false);

  const [athleteOutcome, profileOutcome] = await Promise.all([
    getAthletes().then(
      (athletes) => ({ athletes, failed: false }),
      () => ({ athletes: [] as Athlete[], failed: true }),
    ),
    loadEventProfiles(populatedEvents, signal),
  ]);
  signal?.throwIfAborted();
  if (profileOutcome.profiles.length === 0) throw new Error("Results could not be loaded for any event. Please try again.");
  const overview = assembleOverview(events, profileOutcome.profiles, athleteOutcome.athletes, false);
  overview.failedEvents = profileOutcome.failedEvents;
  if (athleteOutcome.failed) overview.warnings.push("Athlete details could not be loaded. Age group and gender are shown as Not provided.");
  return overview;
}

export function getSampleResultsOverview(): ResultsOverview {
  const profiles = sampleMeetProfile.events.map((event) => ({ event, results: sampleEventResults[event.id] ?? [] }));
  return assembleOverview(sampleMeetProfile.events, profiles, [], true);
}
