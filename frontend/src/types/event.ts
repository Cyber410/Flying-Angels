// Event, participant, result, and attempt data shared across meet screens.
export type RankingOrder = "ASC" | "DESC";

export type EventCategory = {
  id: number;
  name: string;
  rankingOrder: RankingOrder;
};

export type MeetEvent = {
  id: number;
  name: string;
  eventDate: string;
  location: string | null;
  eventType: string | null;
  externalUrl: string | null;
  meetId: number;
  meetName: string;
  category: EventCategory;
  rankingOrder: RankingOrder;
  participantCount: number;
  note?: string;
};

export type EventResult = {
  resultId: number;
  athleteId: number;
  firstName: string;
  lastName: string;
  categoryId: number;
  categoryName: string;
  result: string | null;
  score: number | null;
};

// Saved participation can exist without any result attempt.
export type PendingEventAssignment = Omit<EventResult, "resultId" | "result" | "score"> & {
  resultId: null;
  result: null;
  score: null;
};

// Retained solely for reading data saved by the retired browser-only version.
export type BrowserEventAssignment = PendingEventAssignment;

export type EventParticipant = (EventResult | PendingEventAssignment) & {
  participationId?: number;
  note?: string;
  attempts?: ResultAttempt[];
};

export type EventParticipation = {
  participationId: number; athleteId: number; firstName: string; lastName: string;
  eventId: number; eventName: string; meetId: number; meetName: string;
  categoryId: number; categoryName: string; note: string;
};

export type ResultAttempt = {
  id: string;
  result: string;
  score: number;
};

export type EventProfile = {
  event: MeetEvent;
  results: EventResult[];
  participants?: EventParticipation[];
};

export type FailedEventProfile = {
  eventId: number;
  eventName: string;
  meetName: string;
  message: string;
};

export type EventRequest = {
  name: string;
  eventDate: string;
  location: string | null;
  eventType: string | null;
  externalUrl: string | null;
  rankingOrder: RankingOrder;
  meetId: number;
  categoryId: number | null;
  categoryName: string | null;
};
