import { getEvents } from "./eventService";
import { getMeets } from "./meetService";
import { loadEventProfiles } from "./eventProfileService";
import { getBrowserAssignments } from "./eventAssignmentStore";
import { getResultAttempts, includeSharedResult } from "./eventAttemptStore";
import { getEventNote } from "./eventNotesStore";
import type { AthleteHistoryMeet, AthleteMeetHistory } from "../types/athleteHistory";
import type { BrowserEventAssignment } from "../types/event";

export async function getAthleteMeetHistory(athleteId: number, signal?: AbortSignal): Promise<AthleteMeetHistory> {
  const [eventOutcome, meetOutcome] = await Promise.allSettled([getEvents(signal), getMeets(signal)]);
  signal?.throwIfAborted();
  if (eventOutcome.status === "rejected") throw new Error("Meet history could not be loaded. The athlete's information is still available.");
  const events = eventOutcome.value;
  const meetById = new Map((meetOutcome.status === "fulfilled" ? meetOutcome.value : []).map((meet) => [meet.id, meet] as const));
  const { profiles, failedEvents } = await loadEventProfiles(events.filter((event) => event.participantCount > 0), signal);
  const profileById = new Map(profiles.map((profile) => [profile.event.id, profile] as const));
  const meets = new Map<number, AthleteHistoryMeet>();
  const warnings = new Set<string>();
  if (meetOutcome.status === "rejected") warnings.add("Meet details could not be loaded. Event dates are shown instead of meet dates.");

  for (const event of events) {
    const participant = profileById.get(event.id)?.results.find((result) => result.athleteId === athleteId);
    let browserAssignment: BrowserEventAssignment | undefined;
    try {
      browserAssignment = getBrowserAssignments(event).find((assignment) => assignment.athleteId === athleteId);
    } catch {
      warnings.add("Some browser-only assignments could not be read. Shared history is still shown where available.");
    }
    const selectedParticipant = participant ?? browserAssignment;
    if (!selectedParticipant) continue;
    // The event index carries context missing from the athlete result DTO.
    let attempts = includeSharedResult([], selectedParticipant);
    let note = "";
    try {
      attempts = getResultAttempts(event.id, selectedParticipant);
    } catch {
      warnings.add("Some browser-only attempts could not be read. Available shared results are still shown.");
    }
    try {
      note = getEventNote(event.id, athleteId);
    } catch {
      warnings.add("Some performance notes could not be read from this browser.");
    }
    const meet = meetById.get(event.meetId);
    let group = meets.get(event.meetId);
    if (!group) {
      group = {
        id: event.meetId, name: meet?.name ?? event.meetName,
        date: meet?.meetDate ?? event.eventDate, dateIsFromEvent: !meet,
        location: meet?.location ?? null, events: [],
      };
      meets.set(group.id, group);
    } else if (group.dateIsFromEvent && event.eventDate > group.date) {
      group.date = event.eventDate;
    }
    group.events.push({ event, participant: selectedParticipant, attempts, note });
  }

  const ordered = [...meets.values()].sort((a, b) => b.date.localeCompare(a.date) || a.name.localeCompare(b.name));
  for (const meet of ordered) meet.events.sort((a, b) => a.event.eventDate.localeCompare(b.event.eventDate) || a.event.name.localeCompare(b.event.name));
  return { meets: ordered, warnings: [...warnings], failedEvents };
}
