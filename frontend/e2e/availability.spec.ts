import { expect, test } from "@playwright/test";
import { meet, mockSession, nomination } from "./helpers.ts";

// UC-10: save an explicit athlete reply against the displayed proposal revision.
test("athlete responds to a nominated meet", async ({ page }) => {
  await mockSession(page);
  const saved = { ...nomination, status: "AVAILABLE", lastAnswer: "AVAILABLE", version: 5, answeredRevision: 3 };
  let answered = false;
  await page.route("**/api/portal/availability", route => route.fulfill({ json: [answered ? saved : nomination] }));
  await page.route("**/api/portal/availability/8", route => route.fulfill({ json: nomination }));
  await page.route("**/api/portal/availability/8/response", route => {
    expect(route.request().postDataJSON()).toEqual({ status: "AVAILABLE", version: 4, assignmentRevision: 3 });
    answered = true; return route.fulfill({ json: saved });
  });
  await page.goto("/portal");
  await page.getByRole("button", { name: "Respond", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Respond to meet invitation" });
  await expect(dialog.getByRole("button", { name: "Save response" })).toBeDisabled();
  await dialog.getByRole("radio", { name: /^Available/ }).check();
  await dialog.getByRole("button", { name: "Save response" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("status").filter({ hasText: "Response saved" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Update response" })).toBeVisible();
});

// A changed nomination must be reloaded and reviewed instead of silently resubmitted.
test("stale availability response requires reload and reconfirmation", async ({ page }) => {
  await mockSession(page);
  await page.route("**/api/portal/availability", route => route.fulfill({ json: [nomination] }));
  await page.route("**/api/portal/availability/8", route => route.fulfill({ json: nomination }));
  await page.route("**/api/portal/availability/8/response", route => route.fulfill({ status: 409, json: { message: "Assigned events changed. Reload and confirm again." } }));
  await page.goto("/portal");
  await page.getByRole("button", { name: "Respond", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("radio", { name: /^Available/ }).check();
  await dialog.getByRole("button", { name: "Save response" }).click();
  await expect(dialog.getByRole("alert")).toContainText("Assigned events changed");
  await expect(dialog.getByRole("button", { name: "Save response" })).toBeDisabled();
  await dialog.getByRole("button", { name: "Reload request" }).click();
  await expect(dialog.getByRole("radio", { name: /^Available/ })).not.toBeChecked();
  await expect(dialog.getByRole("button", { name: "Save response" })).toBeDisabled();
});

// UC-11: apply staff filters and record the source of a reply received outside the portal.
test("staff filters nominations and records an external response", async ({ page }) => {
  await mockSession(page, "ADMIN");
  let recorded = false;
  await page.route("**/api/meets", route => route.fulfill({ json: [meet] }));
  await page.route("**/api/availability?**", route => route.fulfill({ json: { content: [{ ...nomination, status: recorded ? "UNAVAILABLE" : "PENDING", assignedEventCount: 1, latestResponse: null }], page: 0, totalElements: 1, totalPages: 1, summary: { total: 1, available: 0, awaitingResponse: recorded ? 0 : 1, unavailable: recorded ? 1 : 0 } } }));
  await page.route("**/api/availability/8", route => route.fulfill({ json: nomination }));
  await page.route("**/api/availability/8/response", route => {
    expect(route.request().postDataJSON()).toMatchObject({ status: "UNAVAILABLE", communicationMethod: "WHATSAPP", note: "Confirmed by guardian", version: 4, assignmentRevision: 3 });
    recorded = true;
    return route.fulfill({ json: { ...nomination, status: "UNAVAILABLE", lastAnswer: "UNAVAILABLE", version: 5 } });
  });
  await page.goto("/availability");
  await page.getByRole("combobox", { name: "Meet", exact: true }).selectOption("2");
  await page.getByLabel("Search athletes").fill("Fictional");
  const filtered = page.waitForRequest(request => request.url().includes("/api/availability?") && new URL(request.url()).searchParams.get("search") === "Fictional");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  const query = new URL((await filtered).url()).searchParams;
  expect(query.get("meetId")).toBe("2"); expect(query.get("view")).toBe("ALL");
  await page.getByRole("button", { name: "Record response" }).click();
  const dialog = page.getByRole("dialog", { name: "Record external response" });
  await dialog.getByRole("radio", { name: /^Unavailable/ }).check();
  await expect(dialog.getByRole("button", { name: "Save response" })).toBeDisabled();
  await dialog.getByLabel("Response received via").selectOption("WHATSAPP");
  await dialog.getByLabel("Staff note (optional)").fill("Confirmed by guardian");
  await dialog.getByRole("button", { name: "Save response" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("status").filter({ hasText: "Response saved" })).toContainText("Unavailable");
  await expect(page.getByRole("table").getByRole("row", { name: /Fictional Runner/ })).toContainText("Unavailable");
});
