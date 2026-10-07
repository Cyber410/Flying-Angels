import type { Athlete, AthleteEvent } from "../types/athlete";

/**
 * Returns a date of birth (YYYY-MM-DD) for someone who is `age` years old
 * today. Ages are calculated from the current date in the app, so fixed
 * birth dates would make the age-group tests go stale over time.
 */
export function dateOfBirthForAge(age: number): string {
  const today = new Date();
  const year = today.getFullYear() - age;
  const month = String(today.getMonth() + 1).padStart(2, "0");
  // Day 01 is always on or before today, so the birthday has already passed.
  return `${year}-${month}-01`;
}

export function buildEvent(
  overrides: Partial<AthleteEvent> = {}
): AthleteEvent {
  return {
    resultId: 1,
    athleteId: 1,
    firstName: "Maya",
    lastName: "Johnson",
    categoryId: 1,
    categoryName: "100M",
    result: "12.45",
    score: 850,
    ...overrides,
  };
}

export function buildAthlete(
  overrides: Partial<Athlete> = {}
): Athlete {
  return {
    id: 1,
    firstName: "Maya",
    lastName: "Johnson",
    dateOfBirth: "2010-03-15",
    gender: "Female",
    note: "Sprinter",
    flyStatus: "Active",
    flyaStatus: "Inactive",
    events: [],
    ...overrides,
  };
}

/** Three athletes, one in each age group: 5-14, 15-18 and 18+. */
export function directoryAthletes(): Athlete[] {
  return [
    buildAthlete({
      id: 1,
      firstName: "Maya",
      lastName: "Johnson",
      dateOfBirth: dateOfBirthForAge(12),
    }),
    buildAthlete({
      id: 2,
      firstName: "Liam",
      lastName: "Chen",
      dateOfBirth: dateOfBirthForAge(16),
    }),
    buildAthlete({
      id: 3,
      firstName: "Sofia",
      lastName: "Martinez",
      dateOfBirth: dateOfBirthForAge(25),
    }),
  ];
}

/** `count` athletes named "Runner 1", "Runner 2", ... for pagination tests. */
export function manyAthletes(count: number): Athlete[] {
  return Array.from({ length: count }, (_, index) =>
    buildAthlete({
      id: index + 1,
      firstName: "Runner",
      lastName: String(index + 1),
    })
  );
}
