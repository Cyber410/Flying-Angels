import type { Page } from "@playwright/test";

// Intercept every API call so browser tests never touch real accounts, email, or data.
export async function mockSession(page: Page, role: "ADMIN" | "ATHLETE" | null = "ATHLETE") {
  await page.route("**/api/**", route => route.fulfill({ status: 404, json: { message: "No browser-test fixture for this request" } }));
  await page.route("**/api/csrf", route => route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "fictional-csrf" } }));
  await page.route("**/api/auth/me", route => route.fulfill({ json: {
    authenticationRequired: true,
    user: role ? { id: 1, name: "Fictional Runner", email: "runner@example.test", role, active: true, athleteId: role === "ATHLETE" ? 7 : null } : null,
    permissions: role === "ADMIN" ? ["READ_STAFF", "MANAGE_USERS", "MANAGE_AVAILABILITY", "MANAGE_MEETS", "EXPORT"] : [],
  } }));
}

export const meet = { id: 2, name: "Winter Demo Meet", meetDate: "2026-12-10", endDate: "2026-12-11", location: "Toronto", externalUrl: null, eventCount: 1 };
export const event = { id: 10, name: "100m", eventDate: "2026-12-10", meetId: 2, meetName: meet.name, categoryId: 1, categoryName: "100m", unit: "SECONDS", rankingOrder: "ASC" };
export const profile = { firstName: "Fictional", lastName: "Runner", dateOfBirth: "2012-01-01", gender: "Female", results: [], availabilityRequests: [] };
export const nomination = {
  id: 8, athleteId: 7, firstName: profile.firstName, lastName: profile.lastName, meetId: meet.id, meetName: meet.name,
  startDate: meet.meetDate, endDate: meet.endDate, location: meet.location, active: true, canRespond: true,
  status: "PENDING", version: 4, assignmentRevision: 3, lastAnswer: null, answeredRevision: null, respondedAt: null,
  events: [{ eventId: 10, eventName: "100m", categoryId: 1, categoryName: "100m", eventDate: event.eventDate }],
  responseDeadline: "2026-12-10T14:00:00Z", timeZone: "America/Toronto",
};

// Supply the signed-in athlete's read-only profile for profile-tab tests.
export async function mockProfile(page: Page) {
  await mockSession(page);
  await page.route("**/api/portal/profile", route => route.fulfill({ json: profile }));
}
