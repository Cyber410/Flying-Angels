export type AthleteEvent = {
  resultId: number;
  athleteId: number;
  firstName: string;
  lastName: string;
  categoryId: number;
  categoryName: string;
  result: string;
  score: number;
};

export type Athlete = {
  id: number;
  firstName: string;
  lastName: string;
  dateOfBirth: string | null;
  gender?: string | null;
  note?: string | null;
  flyStatus?: string | null;
  flyaStatus?: string | null;
  events?: AthleteEvent[];
};

export type AthleteEventRequest = {
  eventName: string;
  result: string;
};

export type AthleteRequest = {
  firstName: string;
  lastName: string;
  note: string;
  flyStatus: string;
  flyaStatus: string;
  dateOfBirth: string | null;
  gender: string;
  events: AthleteEventRequest[];
};