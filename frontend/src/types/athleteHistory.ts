import type { EventParticipant, FailedEventProfile, MeetEvent, ResultAttempt } from "./event";

export type AthleteHistoryEvent = {
  event: MeetEvent;
  participant: EventParticipant;
  attempts: ResultAttempt[];
  note: string;
};

export type AthleteHistoryMeet = {
  id: number;
  name: string;
  date: string;
  dateIsFromEvent: boolean;
  location: string | null;
  events: AthleteHistoryEvent[];
};

export type AthleteMeetHistory = {
  meets: AthleteHistoryMeet[];
  warnings: string[];
  failedEvents: FailedEventProfile[];
};
