import type { MeetEvent } from "./event";

// Matches MeetResponse and MeetRequest in the existing Spring Boot API.
export type Meet = {
  id: number;
  name: string;
  meetDate: string;
  location: string | null;
  externalUrl: string | null;
  eventCount: number;
};

export type MeetRequest = {
  name: string;
  meetDate: string;
  location: string | null;
  externalUrl: string | null;
};
export type MeetProfile = {
  meet: Meet;
  events: MeetEvent[];
};
