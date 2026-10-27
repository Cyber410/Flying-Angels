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

export function includeSharedResult(attempts: ResultAttempt[], participant: EventParticipant): ResultAttempt[] {
  if (participant.result === null || participant.score === null) return attempts;
  const sharedScore = participant.score;
  if (attempts.some((attempt) => Math.abs(attempt.score - sharedScore) < 0.00005)) return attempts;
  return [...attempts, {
    id: `shared-${participant.resultId}-${participant.score}`,
    result: participant.result, score: participant.score,
  }];
}

export function getResultAttempts(eventId: number, participant: EventParticipant): ResultAttempt[] {
  // Sample results remain read-only and never access live attempt storage.
  if (eventId < 1) return includeSharedResult([], participant);
  try {
    const key = historyKey(eventId, participant.athleteId);
    const staged = readStagedHistory(`${key}:staged`);
    const history = staged?.ready ? staged : readHistory(key);
    return includeSharedResult(history?.attempts ?? [], participant);
  } catch {
    throw new Error("Attempt history could not be loaded from this browser. Existing data has not been overwritten.");
  }
}

// Stage before calling the API so unavailable/full storage cannot silently lose
// additional attempts. Only committed history is displayed after a reload.
export function stageResultAttempts(eventId: number, athleteId: number, attempts: ResultAttempt[]) {
  try {
    const key = historyKey(eventId, athleteId);
    readHistory(key);
    const previous = readStagedHistory(`${key}:staged`);
    if (previous?.ready) {
      localStorage.setItem(key, JSON.stringify({ version: 1, attempts: previous.attempts }));
      localStorage.removeItem(`${key}:staged`);
    }
    const staged: StagedHistory = { version: 1, attempts, ready: false };
    localStorage.setItem(`${key}:staged`, JSON.stringify(staged));
  } catch {
    throw new Error("Attempt history could not be saved in this browser. Check browser storage and try again.");
  }
}

export function commitResultAttempts(eventId: number, athleteId: number, attempts: ResultAttempt[]) {
  const key = historyKey(eventId, athleteId);
  const staged: StagedHistory = { version: 1, attempts, ready: true };
  localStorage.setItem(`${key}:staged`, JSON.stringify(staged));
  try {
    localStorage.setItem(key, JSON.stringify({ version: 1, attempts }));
    localStorage.removeItem(`${key}:staged`);
  } catch {
    // The committed staging entry is a recoverable copy if compaction fails.
  }
}

export function discardStagedResultAttempts(eventId: number, athleteId: number) {
  try {
    const key = historyKey(eventId, athleteId);
    if (!readStagedHistory(`${key}:staged`)?.ready) localStorage.removeItem(`${key}:staged`);
  } catch {
    // Uncommitted staging entries are ignored; preserve the existing history.
  }
}

export function removeResultAttempts(eventId: number, athleteId: number) {
  const key = historyKey(eventId, athleteId);
  localStorage.removeItem(`${key}:staged`);
  localStorage.removeItem(key);
}
