import { expect, test } from "@playwright/test";
import { expectNoSeriousA11yViolations } from "../e2e/a11y.ts";
import { loginAs, ROLE_HOME, skipWithoutBaseUrl, submitLogin, type Role } from "./auth.ts";

// Read-only smoke checks against a deployed app: nothing here creates, edits, or deletes data.
skipWithoutBaseUrl();

test("login page loads", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
  await expectNoSeriousA11yViolations(page, "login");
});

for (const role of Object.keys(ROLE_HOME) as Role[]) {
  test(`${role} signs in and lands on the ${role} home page`, async ({ page }) => {
    await loginAs(page, role);
    await expectNoSeriousA11yViolations(page, `${role}-home`);
  });
}

// Rainy day: a made-up address on a reserved domain, so no real account is ever locked out.
test("unknown email shows an error and stays on login", async ({ page }) => {
  await submitLogin(page, "no-such-user@example.invalid", "definitely-not-the-password");
  // Any alert passes for now, including one caused by a backend outage rather than rejected credentials.
  // TODO: assert the invalid-credentials alert text once the first live run shows the real backend message.
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page).toHaveURL(url => url.pathname === "/login");
});

// Rainy day: athletes cannot open account administration and are sent back to their portal.
test("athlete opening Users is redirected to the portal", async ({ page }) => {
  await loginAs(page, "athlete");
  await page.goto("/users");
  await expect(page).toHaveURL(url => url.pathname === ROLE_HOME.athlete.path);
  await expect(page.getByRole("heading", { level: 1, name: ROLE_HOME.athlete.heading })).toBeVisible();
});
