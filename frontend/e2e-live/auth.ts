import { expect, test, type Page } from "@playwright/test";

export type Role = "admin" | "coach" | "athlete";

// Where each role lands after signing in, and the heading that proves the page rendered.
export const ROLE_HOME: Record<Role, { path: string; heading: string }> = {
  admin: { path: "/", heading: "Athletes" },
  coach: { path: "/", heading: "Athletes" },
  athlete: { path: "/portal", heading: "My Meets" },
};

// Skip every test in the calling file when there is no deployed app to point at.
export function skipWithoutBaseUrl() {
  test.skip(!process.env.BASE_URL, "BASE_URL is not set; skipping live smoke tests. Set BASE_URL to the deployed app's URL to run them.");
}

// Read one role's credentials from the environment, skipping the current test when they are missing.
export function credentialsFor(role: Role): { email: string; password: string } {
  const prefix = `E2E_${role.toUpperCase()}`;
  const email = process.env[`${prefix}_EMAIL`];
  const password = process.env[`${prefix}_PASSWORD`];
  test.skip(!email || !password, `${prefix}_EMAIL and ${prefix}_PASSWORD are not set; skipping ${role} live tests.`);
  return { email: email ?? "", password: password ?? "" };
}

// Fill and submit the real login form without waiting for the outcome.
export async function submitLogin(page: Page, email: string, password: string) {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
}

// Sign in as a role through the login page and wait until its home page has rendered.
export async function loginAs(page: Page, role: Role) {
  const { email, password } = credentialsFor(role);
  await submitLogin(page, email, password);
  const home = ROLE_HOME[role];
  await expect(page).toHaveURL(url => url.pathname === home.path);
  await expect(page.getByRole("heading", { level: 1, name: home.heading })).toBeVisible();
}
