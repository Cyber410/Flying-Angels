// Join saved participation with every attempt, retaining one best result per athlete.
import type { EventParticipant, EventProfile, ResultAttempt } from "../types/event";
import { bestResultAttempt } from "./eventResult";

export function participantAttempts(participant: EventParticipant): ResultAttempt[] {
  if (participant.attempts) return participant.attempts;
  return participant.resultId !== null && participant.result !== null && participant.score !== null
    ? [{ id: String(participant.resultId), result: participant.result, score: participant.score }] : [];
}

export function eventParticipants(profile: EventProfile): EventParticipant[] {
  const byAthlete = new Map<number, EventParticipant>();
  for (const participant of profile.participants ?? []) {
    byAthlete.set(participant.athleteId, {
      ...participant, resultId: null, result: null, score: null, attempts: [],
    });
  }
  for (const result of profile.results) {
    let participant = byAthlete.get(result.athleteId);
    if (!participant) {
      participant = { ...result, attempts: [], note: "" };
      byAthlete.set(result.athleteId, participant);
    }
    if (result.result !== null && result.score !== null) {
      const attempt: ResultAttempt = { id: String(result.resultId), result: result.result, score: result.score };
      participant.attempts = [...(participant.attempts ?? []), attempt];
    }
  }
  return [...byAthlete.values()].map(participant => {
    const best = bestResultAttempt(participant.attempts ?? [], profile.event.rankingOrder);
    return best ? { ...participant, resultId: Number(best.id), result: best.result, score: best.score } : participant;
  });
}
