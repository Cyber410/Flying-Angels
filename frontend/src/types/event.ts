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

// A pending selection has no API result ID and no fabricated performance.
export type BrowserEventAssignment = Omit<EventResult, "resultId" | "result" | "score"> & {
  resultId: null;
  result: null;
  score: null;
};

export type EventParticipant = EventResult | BrowserEventAssignment;

export type ResultAttempt = {
  id: string;
  result: string;
  score: number;
};

export type EventProfile = {
  event: MeetEvent;
  results: EventResult[];
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
