// Read-only legacy assignment reader used solely by explicit migration.
import type { Athlete } from "../types/athlete";
import type { BrowserEventAssignment, MeetEvent } from "../types/event";

type StoredAthlete = Pick<Athlete, "id" | "firstName" | "lastName">;

function storageKey(eventId: number) {
  if (!Number.isSafeInteger(eventId) || eventId < 1) {
    throw new Error("Saved assignments require an existing event.");
  }
  return `flying-angels:pending-event-assignments:${eventId}`;
}

function isStoredAthlete(value: unknown): value is StoredAthlete {
  return typeof value === "object" && value !== null &&
    "id" in value && typeof value.id === "number" && Number.isSafeInteger(value.id) && value.id > 0 &&
    "firstName" in value && typeof value.firstName === "string" &&
    "lastName" in value && typeof value.lastName === "string";
}

function readStoredAthletes(eventId: number): StoredAthlete[] {
  let raw: string | null;
  try {
    raw = localStorage.getItem(storageKey(eventId));
  } catch {
    throw new Error("Saved assignments could not be accessed. Download the records for review.");
  }
  if (raw === null) return [];
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    throw new Error("Saved assignments could not be read. Existing records have not been overwritten.");
  }
  if (
    typeof data !== "object" || data === null ||
    !("version" in data) || data.version !== 1 ||
    !("athletes" in data) || !Array.isArray(data.athletes) ||
    !data.athletes.every(isStoredAthlete)
  ) {
    throw new Error("Saved assignments have an unsupported format. Existing records have not been overwritten.");
  }
  return data.athletes;
}

function toAssignment(event: MeetEvent, athlete: StoredAthlete): BrowserEventAssignment {
  return {
    resultId: null, athleteId: athlete.id,
    firstName: athlete.firstName, lastName: athlete.lastName,
    categoryId: event.category.id, categoryName: event.category.name,
    result: null, score: null,
  };
}

// Reads pending assignments without inventing result values.
export function getBrowserAssignments(event: MeetEvent): BrowserEventAssignment[] {
  return readStoredAthletes(event.id).map((athlete) => toAssignment(event, athlete));
}
