// Assemble athlete history from shared participation, attempts, and performance notes.
import { getEvents } from "./eventService";
import { getMeets } from "./meetService";
import { loadEventProfiles } from "./eventProfileService";
import { eventParticipants, participantAttempts } from "../utils/eventParticipants";
import type { AthleteHistoryMeet, AthleteMeetHistory } from "../types/athleteHistory";

export async function getAthleteMeetHistory(athleteId: number, signal?: AbortSignal): Promise<AthleteMeetHistory> {
  const [eventOutcome, meetOutcome] = await Promise.allSettled([getEvents(signal), getMeets(signal)]);
  signal?.throwIfAborted();
  if (eventOutcome.status === "rejected") throw new Error("Meet history could not be loaded. The athlete's information is still available.");
  const events = eventOutcome.value;
  const meetById = new Map((meetOutcome.status === "fulfilled" ? meetOutcome.value : []).map(meet => [meet.id, meet]));
  const { profiles, failedEvents } = await loadEventProfiles(events.filter(event => event.participantCount > 0), signal);
  const meets = new Map<number, AthleteHistoryMeet>();
  const warnings: string[] = meetOutcome.status === "rejected" ? ["Meet details could not be loaded. Event dates are shown instead."] : [];

  for (const profile of profiles) {
    const participant = eventParticipants(profile).find(item => item.athleteId === athleteId);
    if (!participant) continue;
    const event = profile.event;
    const meet = meetById.get(event.meetId);
    let group = meets.get(event.meetId);
    if (!group) {
      group = { id: event.meetId, name: meet?.name ?? event.meetName, date: meet?.meetDate ?? event.eventDate,
        endDate: meet?.endDate, dateIsFromEvent: !meet, location: meet?.location ?? null, events: [] };
      meets.set(group.id, group);
    } else if (group.dateIsFromEvent && event.eventDate > group.date) group.date = event.eventDate;
    group.events.push({ event, participant, attempts: participantAttempts(participant), note: participant.note ?? "" });
  }
  const ordered = [...meets.values()].sort((a,b) => b.date.localeCompare(a.date) || a.name.localeCompare(b.name));
  ordered.forEach(meet => meet.events.sort((a,b) => a.event.eventDate.localeCompare(b.event.eventDate) || a.event.name.localeCompare(b.event.name)));
  return { meets: ordered, warnings, failedEvents };
}
