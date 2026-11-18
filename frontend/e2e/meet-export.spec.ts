// End-to-end tests for "Export meet registration and results as CSV".
// The backend is mocked with page.route, so only the front end needs to run.
import { expect, test, type Page } from "@playwright/test";
import { readFile } from "node:fs/promises";

const meet = {
  id: 7, name: "Fall Invitational", meetDate: "2026-10-24", endDate: null,
  location: "Toronto, ON", externalUrl: null, eventCount: 1,
};
const meetProfile = {
  meet,
  events: [{
    id: 70, name: "100m Sprint", eventDate: "2026-10-24", location: "Toronto, ON",
    eventType: "Track", externalUrl: null, meetId: 7, meetName: "Fall Invitational",
    category: { id: 1, name: "100m Sprint", rankingOrder: "ASC" }, rankingOrder: "ASC",
    participantCount: 2,
  }],
};

const registrationCsv = [
  "meet_id,meet_name,event_name,athlete_id,first_name,last_name,participation_id",
  "7,Fall Invitational,100m Sprint,1,Maya,Johnson,501",
  "7,Fall Invitational,100m Sprint,2,Liam,Chen,502",
].join("\r\n");

const resultsCsv = [
  "meet_id,meet_name,event_name,athlete_id,first_name,last_name,result_id,result_label,result",
  "7,Fall Invitational,100m Sprint,1,Maya,Johnson,901,Attempt 1,12.90s",
  "7,Fall Invitational,100m Sprint,1,Maya,Johnson,902,Attempt 2,12.45s",
].join("\r\n");

type Counts = { registration: number; results: number };

// Pretends to be the Spring Boot API for one meet.
async function mockMeetApi(page: Page, counts: Counts) {
  await page.route("**/api/**", route => route.fulfill({ json: [] }));
  // Model the authenticated staff session required by the integrated application.
  await page.route("**/api/auth/me", route => route.fulfill({ json: {
    authenticationRequired: true, user: { id: 1, name: "Demo Admin", email: "admin@example.test", role: "ADMIN", active: true },
    permissions: ["READ_STAFF", "MANAGE_MEETS", "MANAGE_EVENTS", "MANAGE_RESULTS", "MANAGE_USERS", "EXPORT"],
  } }));
  await page.route(/\/api\/events\/70$/, route => route.fulfill({ json: { event: meetProfile.events[0], results: [], participations: [] } }));
  await page.route(/\/api\/meets\/7$/, route => route.fulfill({ json: meetProfile }));
  await page.route(/\/api\/meets\/7\/exports$/, route => route.fulfill({
    json: {
      meetId: 7, meetName: meet.name, options: [
        { type: "REGISTRATION", displayName: "Registration List", recordCount: counts.registration, available: counts.registration > 0 },
        { type: "RESULTS", displayName: "Competition Results", recordCount: counts.results, available: counts.results > 0 },
      ],
    },
  }));
  await page.route(/\/api\/meets\/7\/export\?/, route => {
    const type = new URL(route.request().url()).searchParams.get("type");
    const isRegistration = type === "REGISTRATION";
    const count = isRegistration ? counts.registration : counts.results;
    if (count === 0) {
      return route.fulfill({
        status: 409,
        json: {
          code: isRegistration ? "NO_REGISTRATION_RECORDS" : "NO_RESULT_RECORDS",
          message: isRegistration ? "No registration records are available for this meet" : "No recorded results are available for this meet",
        },
      });
    }
    const fileName = `meet-7-fall-invitational-${isRegistration ? "registration" : "results"}.csv`;
    return route.fulfill({
      status: 200,
      contentType: "text/csv; charset=utf-8",
      headers: { "Content-Disposition": `attachment; filename="${fileName}"` },
      body: isRegistration ? registrationCsv : resultsCsv,
    });
  });
}

async function openExportDialog(page: Page, counts: Counts = { registration: 2, results: 2 }) {
  await mockMeetApi(page, counts);
  await page.goto("/meets/7");
  await expect(page.getByRole("heading", { level: 1, name: "Fall Invitational" })).toBeVisible();
  await page.getByRole("button", { name: "Export", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Export Meet Data" });
  await expect(dialog).toBeVisible();
  return dialog;
}

// Clicks Export CSV and returns the downloaded file name and its CSV lines.
async function exportAndRead(page: Page): Promise<{ fileName: string; lines: string[] }> {
  const downloadPromise = page.waitForEvent("download");
  const responsePromise = page.waitForResponse(/\/api\/meets\/7\/export\?/);
  await page.getByRole("button", { name: "Export CSV" }).click();
  const [download, response] = await Promise.all([downloadPromise, responsePromise]);
  expect(response.ok()).toBe(true);
  const path = await download.path();
  if (!path) throw new Error("The browser did not save the exported CSV.");
  return { fileName: download.suggestedFilename(), lines: (await readFile(path, "utf8")).split("\r\n") };
}

test.describe("Meet Details CSV export", () => {
  test("shows both export options with record counts", async ({ page }) => {
    const dialog = await openExportDialog(page);

    await expect(dialog.getByRole("radio", { name: /Registration List/ })).toBeChecked();
    await expect(dialog.getByRole("radio", { name: /Competition Results/ })).toBeVisible();
    await expect(dialog.getByText("2 records")).toHaveCount(2);
  });

  test("downloads the Registration List with one row per assignment", async ({ page }) => {
    await openExportDialog(page);

    const { fileName, lines } = await exportAndRead(page);

    expect(fileName).toBe("meet-7-fall-invitational-registration.csv");
    expect(lines).toHaveLength(3); // header + 2 athlete-event assignments
    expect(lines[1]).toContain("Maya,Johnson");
    await expect(page.getByRole("dialog")).toBeHidden();
    await expect(page.getByText(/Registration List exported/)).toBeVisible();
  });

  test("downloads Competition Results with one row per recorded performance", async ({ page }) => {
    const dialog = await openExportDialog(page);
    await dialog.getByRole("radio", { name: /Competition Results/ }).check();

    const { fileName, lines } = await exportAndRead(page);

    expect(fileName).toBe("meet-7-fall-invitational-results.csv");
    expect(lines.filter(line => line.includes("Maya,Johnson"))).toHaveLength(2); // two attempts
  });

  test("shows a message when no athletes are assigned", async ({ page }) => {
    const dialog = await openExportDialog(page, { registration: 0, results: 0 });

    await page.getByRole("button", { name: "Export CSV" }).click();

    await expect(dialog.getByText(/No registration records are available/)).toBeVisible();
    await expect(dialog).toBeVisible(); // user stays in the dialog and can go back
  });

  test("shows a message when no results are recorded", async ({ page }) => {
    const dialog = await openExportDialog(page, { registration: 2, results: 0 });
    await dialog.getByRole("radio", { name: /Competition Results/ }).check();

    await page.getByRole("button", { name: "Export CSV" }).click();

    await expect(dialog.getByText(/No results are available/)).toBeVisible();
  });

  test("lets the user switch from the wrong export type", async ({ page }) => {
    const dialog = await openExportDialog(page, { registration: 2, results: 0 });
    await dialog.getByRole("radio", { name: /Competition Results/ }).check();
    await page.getByRole("button", { name: "Export CSV" }).click();
    await expect(dialog.getByText(/No results are available/)).toBeVisible();

    await dialog.getByRole("radio", { name: /Registration List/ }).check();
    await expect(dialog.getByText(/No results are available/)).toBeHidden();
    const { fileName } = await exportAndRead(page);

    expect(fileName).toContain("registration");
  });

  test("cancel closes the dialog without downloading", async ({ page }) => {
    await openExportDialog(page);
    let downloaded = false;
    page.on("download", () => { downloaded = true; });

    await page.getByRole("button", { name: "Cancel" }).click();

    await expect(page.getByRole("dialog")).toBeHidden();
    await expect(page.getByRole("heading", { level: 1, name: "Fall Invitational" })).toBeVisible();
    expect(downloaded).toBe(false);
  });
});
