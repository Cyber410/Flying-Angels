import type { EventResult, MeetEvent } from "../types/event";
import type { MeetProfile } from "../types/meet";

// Opt-in, read-only preview at /meets/demo. Never written to the API/database.
// Negative IDs keep these fictional records separate from live records.
const meet = {
  id: -1, name: "Fall Invitational", meetDate: "2026-10-24",
  location: "Toronto, ON", externalUrl: null, eventCount: 3,
};

function sampleEvent(id: number, name: string, participantCount: number, field = false): MeetEvent {
  return {
    id, name, participantCount, eventDate: meet.meetDate, location: meet.location,
    eventType: field ? "Field" : "Track", externalUrl: null,
    meetId: meet.id, meetName: meet.name,
    rankingOrder: field ? "DESC" : "ASC",
    category: { id, name, rankingOrder: field ? "DESC" : "ASC" },
  };
}

export const sampleMeetProfile: MeetProfile = {
  meet,
  events: [sampleEvent(-101, "100m Sprint", 3), sampleEvent(-102, "200m Sprint", 2), sampleEvent(-103, "Long Jump", 2, true)],
};

export const sampleEventResults: Record<number, EventResult[]> = {
  [-101]: [
    { resultId: -201, athleteId: -301, firstName: "Alex", lastName: "Johnson", categoryId: -101, categoryName: "100m Sprint", result: "12.45s", score: 12.45 },
    { resultId: -202, athleteId: -302, firstName: "Taylor", lastName: "Smith", categoryId: -101, categoryName: "100m Sprint", result: "12.81s", score: 12.81 },
    { resultId: -203, athleteId: -303, firstName: "Casey", lastName: "Brown", categoryId: -101, categoryName: "100m Sprint", result: null, score: null },
  ],
  [-102]: [
    { resultId: -204, athleteId: -301, firstName: "Alex", lastName: "Johnson", categoryId: -102, categoryName: "200m Sprint", result: "25.61s", score: 25.61 },
    { resultId: -205, athleteId: -302, firstName: "Taylor", lastName: "Smith", categoryId: -102, categoryName: "200m Sprint", result: "26.10s", score: 26.1 },
  ],
  [-103]: [
    { resultId: -206, athleteId: -301, firstName: "Alex", lastName: "Johnson", categoryId: -103, categoryName: "Long Jump", result: "4.92m", score: 4.92 },
    { resultId: -207, athleteId: -303, firstName: "Casey", lastName: "Brown", categoryId: -103, categoryName: "Long Jump", result: "4.36m", score: 4.36 },
  ],
};
