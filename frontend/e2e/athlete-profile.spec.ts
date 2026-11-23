import { expect, test } from "@playwright/test";
import { expectNoSeriousA11yViolations } from "./a11y.ts";
import { event, mockProfile } from "./helpers.ts";

// UC-08: show the server-selected best rather than simply the newest performance.
test("athlete views personal best and progression", async ({ page }) => {
  await mockProfile(page);
  const progression = [12.8, 12.1, 12.4].map((score, index) => ({ resultId: index + 1, meetId: 2, meetName: "Demo Meet", eventId: 10, eventName: "100m", eventDate: `2026-10-${10 + index}`, result: String(score), resultLabel: null, score, recordedAt: null }));
  await page.route("**/api/portal/history", route => route.fulfill({ json: {
    athlete: { id: 7, firstName: "Fictional", lastName: "Runner" }, status: "AVAILABLE",
    performances: [{ categoryId: 1, categoryName: "100m", unit: "SECONDS", rankingOrder: "ASC", recordedAttemptCount: 3, validAttemptCount: 3, personalBest: progression[1], progressionStatus: "PROGRESSION_AVAILABLE", progression }],
  } }));
  await page.goto("/portal/profile");
  await page.getByRole("tab", { name: "Personal Bests" }).click();
  const table = page.getByRole("table", { name: "Personal bests by event" });
  await expect(table).toContainText("12.1 s");
  await expect(table).not.toContainText("12.4 s");
  await expect(page.getByRole("img", { name: /100m performance progression/ })).toBeVisible();
  await expectNoSeriousA11yViolations(page, "profile-personal-bests");
  await expect(table.getByRole("link")).toHaveCount(0);
});

// UC-09: filter the authorized event cohort and keep competitor names read-only.
test("athlete filters event rankings without competitor profile links", async ({ page }) => {
  await mockProfile(page);
  await page.route("**/api/portal/rankings/events", route => route.fulfill({ json: [event] }));
  await page.route("**/api/portal/rankings?**", route => route.fulfill({ json: {
    category: { name: "100m", unit: "SECONDS", rankingOrder: "ASC" }, status: "AVAILABLE", page: 0, size: 25, totalElements: 2, totalPages: 1,
    content: [
      { position: 1, firstName: "Demo", lastName: "Competitor", result: "12.1", score: 12.1, unit: "SECONDS", profileAccessible: false, athleteId: null },
      { position: 2, firstName: "Fictional", lastName: "Runner", result: "12.4", score: 12.4, unit: "SECONDS", profileAccessible: true, athleteId: 7 },
    ],
  } }));
  await page.goto("/portal/profile");
  await page.getByRole("tab", { name: "Rankings" }).click();
  const table = page.getByRole("table", { name: /100m rankings/ });
  await expect(table.getByRole("row", { name: /Fictional Runner/ })).toContainText("You");
  await expectNoSeriousA11yViolations(page, "profile-rankings");
  await expect(table.getByRole("link")).toHaveCount(0);
  await page.getByRole("combobox", { name: "Age group", exact: true }).selectOption("5:14");
  const changed = page.waitForRequest(request => request.url().includes("/api/portal/rankings?") && new URL(request.url()).searchParams.get("gender") === "Female");
  await page.getByRole("combobox", { name: "Gender", exact: true }).selectOption("Female");
  const query = new URL((await changed).url()).searchParams;
  expect(query.get("eventId")).toBe("10"); expect(query.get("minAge")).toBe("5"); expect(query.get("maxAge")).toBe("14");
  expect(query.get("ageAsOf")).toBe(event.eventDate);
  await expect(table.getByText("Demo Competitor")).toBeVisible();
});
