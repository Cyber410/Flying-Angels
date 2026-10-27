import type { Athlete } from "../types/athlete";
import type { BrowserEventAssignment, MeetEvent } from "../types/event";

type StoredAthlete = Pick<Athlete, "id" | "firstName" | "lastName">;

function storageKey(eventId: number) {
  if (!Number.isSafeInteger(eventId) || eventId < 1) {
    throw new Error("Browser-only assignments require a live event.");
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
    throw new Error("Browser storage is unavailable. Enable it to save assignments without results.");
  }
  if (raw === null) return [];
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    throw new Error("Browser-only assignments could not be read. Stored data has not been overwritten.");
  }
  if (
    typeof data !== "object" || data === null ||
    !("version" in data) || data.version !== 1 ||
    !("athletes" in data) || !Array.isArray(data.athletes) ||
    !data.athletes.every(isStoredAthlete)
  ) {
    throw new Error("Browser-only assignments have an unsupported format. Stored data has not been overwritten.");
  }
  return data.athletes;
}

function writeStoredAthletes(eventId: number, athletes: StoredAthlete[]) {
  try {
    if (athletes.length) {
      localStorage.setItem(storageKey(eventId), JSON.stringify({ version: 1, athletes }));
    } else {
      localStorage.removeItem(storageKey(eventId));
    }
  } catch {
    throw new Error("The assignment could not be saved in this browser. Check browser storage and try again.");
  }
}

function toAssignment(event: MeetEvent, athlete: StoredAthlete): BrowserEventAssignment {
  return {
    resultId: null, athleteId: athlete.id,
    firstName: athlete.firstName, lastName: athlete.lastName,
    categoryId: event.category.id, categoryName: event.category.name,
    result: null, score: null,
  };
}

export function getBrowserAssignments(event: MeetEvent): BrowserEventAssignment[] {
  return readStoredAthletes(event.id).map((athlete) => toAssignment(event, athlete));
}

export function saveBrowserAssignment(event: MeetEvent, athlete: StoredAthlete): BrowserEventAssignment {
  const stored = readStoredAthletes(event.id);
  const snapshot = { id: athlete.id, firstName: athlete.firstName, lastName: athlete.lastName };
  writeStoredAthletes(event.id, [...stored.filter((item) => item.id !== athlete.id), snapshot]);
  return toAssignment(event, snapshot);
}

export function removeBrowserAssignment(eventId: number, athleteId: number): void {
  const stored = readStoredAthletes(eventId);
  writeStoredAthletes(eventId, stored.filter((athlete) => athlete.id !== athleteId));
}
