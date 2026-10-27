import type { FailedEventProfile, MeetEvent } from "./event";

export type OverviewResult = {
  id: string;
  athleteId: number;
  firstName: string;
  lastName: string;
  gender: string;
  ageGroup: string;
  eventId: number;
  eventName: string;
  categoryId: number;
  categoryName: string;
  meetId: number;
  meetName: string;
  eventDate: string;
  result: string;
  score: number | null;
  source: "shared" | "browser";
};

export type ResultsOverview = {
  events: MeetEvent[];
  sharedResults: OverviewResult[];
  allAttempts: OverviewResult[];
  warnings: string[];
  failedEvents: FailedEventProfile[];
};
