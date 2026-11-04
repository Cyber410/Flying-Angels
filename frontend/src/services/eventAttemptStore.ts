// Read-only legacy attempt reader used solely by explicit migration.
import type { EventParticipant, ResultAttempt } from "../types/event";

type AttemptHistory = { version: 1; attempts: ResultAttempt[] };
type StagedHistory = AttemptHistory & { ready: boolean };

function historyKey(eventId: number, athleteId: number) {
  if (!Number.isSafeInteger(eventId) || eventId < 1 || !Number.isSafeInteger(athleteId) || athleteId < 1) {
    throw new Error("Attempt history requires a live event and athlete.");
  }
  return `flying-angels:event-attempts:${eventId}:${athleteId}`;
}

function isAttempt(value: unknown): value is ResultAttempt {
  return typeof value === "object" && value !== null &&
    "id" in value && typeof value.id === "string" && value.id.length > 0 &&
    "result" in value && typeof value.result === "string" && value.result.length > 0 && value.result.length <= 50 &&
    "score" in value && typeof value.score === "number" && Number.isFinite(value.score) && value.score >= 0;
}

function readHistory(key: string): AttemptHistory | null {
  const raw = localStorage.getItem(key);
  if (raw === null) return null;
  const data: unknown = JSON.parse(raw);
  if (
    typeof data !== "object" || data === null ||
    !("version" in data) || data.version !== 1 ||
    !("attempts" in data) || !Array.isArray(data.attempts) || !data.attempts.every(isAttempt)
  ) {
    throw new Error("Saved attempt history has an unsupported format.");
  }
  return { version: 1, attempts: data.attempts };
}

function readStagedHistory(key: string): StagedHistory | null {
  const history = readHistory(key);
  if (!history) return null;
  const raw: unknown = JSON.parse(localStorage.getItem(key) ?? "null");
  if (typeof raw !== "object" || raw === null || !("ready" in raw) || typeof raw.ready !== "boolean") {
    throw new Error("Saved attempt history has an unsupported format.");
  }
  return { ...history, ready: raw.ready };
}

// Keeps the shared best result available without duplicating matching scores.
export function includeSharedResult(attempts: ResultAttempt[], participant: EventParticipant): ResultAttempt[] {
  if (participant.result === null || participant.score === null) return attempts;
  const sharedScore = participant.score;
  if (attempts.some((attempt) => Math.abs(attempt.score - sharedScore) < 0.00005)) return attempts;
  return [...attempts, {
    id: `shared-${participant.resultId}-${participant.score}`,
    result: participant.result, score: participant.score,
  }];
}

// Combines valid local attempt history with the available shared result.
export function getResultAttempts(eventId: number, participant: EventParticipant): ResultAttempt[] {
  // Sample results remain read-only and never access live attempt storage.
  if (eventId < 1) return includeSharedResult([], participant);
  try {
    const key = historyKey(eventId, participant.athleteId);
    const staged = readStagedHistory(`${key}:staged`);
    const history = staged?.ready ? staged : readHistory(key);
    return includeSharedResult(history?.attempts ?? [], participant);
  } catch {
    throw new Error("Saved attempt history could not be loaded. Existing records have not been overwritten.");
  }
}
