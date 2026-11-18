// Meet API contracts, including optional end dates and event profiles.
import type { MeetEvent } from "./event";

// Matches MeetResponse and MeetRequest in the existing Spring Boot API.
export type Meet = {
  id: number;
  name: string;
  meetDate: string;
  endDate?: string | null;
  location: string | null;
  externalUrl: string | null;
  eventCount: number;
  startDate?: string;
  availabilitySummary?: { total: number; available: number; awaitingResponse: number; unavailable: number };
};
export type MeetView = "UPCOMING" | "PAST" | "ALL";

export type MeetRequest = {
  name: string;
  meetDate: string;
  endDate?: string | null;
  location: string | null;
  externalUrl: string | null;
};
export type MeetProfile = {
  meet: Meet;
  events: MeetEvent[];
};
