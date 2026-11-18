import type { AvailabilityOverview, AvailabilityRequest } from "../services/availabilityService";

// Provide one fictional upcoming nomination with explicit concurrency tokens.
export function availabilityRequest(overrides: Partial<AvailabilityRequest> = {}): AvailabilityRequest {
  return { id: 8, athleteId: 7, firstName: "Fictional", lastName: "Runner", meetId: 2, meetName: "Winter Demo Meet",
    startDate: "2026-12-10", endDate: "2026-12-11", location: "Toronto", active: true, canRespond: true,
    status: "PENDING", version: 4, assignmentRevision: 3, lastAnswer: null, answeredRevision: null, respondedAt: null,
    events: [{ eventId: 10, eventName: "100m", categoryId: 1, categoryName: "100m", eventDate: "2026-12-10" }],
    responseDeadline: "2026-12-10T14:00:00Z", timeZone: "America/Toronto", ...overrides };
}

// Build a small staff overview without changing application or database data.
export function availabilityOverview(): AvailabilityOverview {
  return { content: [{ ...availabilityRequest(), assignedEventCount: 1, latestResponse: null }], page: 0, totalElements: 1,
    totalPages: 1, summary: { total: 1, available: 0, unavailable: 0, awaitingResponse: 1 } };
}
