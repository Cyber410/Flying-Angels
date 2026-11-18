import { expect, test } from "@playwright/test";
import { mockSession } from "./helpers.ts";

// UC-07: an invitation remains separate from a created login account.
test("Admin invites a Coach into Pending invitations", async ({ page }) => {
  await mockSession(page, "ADMIN");
  const invitation = { id: 9, name: "Demo Coach", email: "coach@example.test", role: "COACH", athleteId: null, status: "AWAITING_VERIFICATION" };
  let pending: typeof invitation[] = [];
  await page.route("**/api/users", route => route.fulfill({ json: [{ id: 1, name: "Demo Admin", email: "admin@example.test", role: "ADMIN", active: true, athleteId: null, accountStatus: "ACTIVE" }] }));
  await page.route("**/api/athletes?**", route => route.fulfill({ json: [] }));
  await page.route("**/api/users/pending-invitations", route => {
    if (route.request().method() === "POST") {
      expect(route.request().postDataJSON()).toMatchObject({ name: invitation.name, email: invitation.email, role: "COACH" });
      pending = [invitation]; return route.fulfill({ status: 201, json: invitation });
    }
    return route.fulfill({ json: pending });
  });
  await page.goto("/users");
  await page.getByRole("button", { name: "+ Invite user" }).click();
  const dialog = page.getByRole("dialog", { name: "Invite a user" });
  await dialog.getByLabel("Full name").fill(invitation.name);
  await dialog.getByLabel("Email address").fill(invitation.email);
  await dialog.getByRole("button", { name: "Send verification invitation" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("row", { name: /Demo Coach/ })).toContainText("Awaiting verification");
  await expect(page.getByRole("button", { name: "Accounts (1)" })).toBeVisible();
});

// UC-07: reject a wrong code before allowing password setup and account creation.
test("invited user verifies email before creating an account", async ({ page }) => {
  await mockSession(page, null);
  const info = { email: "runner@example.test", name: "Fictional Runner", role: "ATHLETE", verified: false };
  let completed = false;
  await page.route("**/api/auth/enrollment/info", route => route.fulfill({ json: info }));
  await page.route("**/api/auth/enrollment/verify", route => route.request().postDataJSON().code === "123456"
    ? route.fulfill({ json: { ...info, verified: true } })
    : route.fulfill({ status: 400, json: { message: "The verification code is incorrect." } }));
  await page.route("**/api/auth/enrollment/complete", route => { completed = true; return route.fulfill({ json: { id: 7 } }); });
  await page.goto("/activate#token=fictional-invitation");
  await expect(page.getByLabel("New password")).toBeHidden();
  await page.getByLabel("Verification code").fill("000000");
  await page.getByRole("button", { name: "Verify email" }).click();
  await expect(page.getByRole("alert")).toContainText("incorrect");
  expect(completed).toBe(false);
  await page.getByLabel("Verification code").fill("123456");
  await page.getByRole("button", { name: "Verify email" }).click();
  await page.getByLabel("New password").fill("fictional-passphrase");
  await page.getByLabel("Confirm password").fill("fictional-passphrase");
  await page.getByRole("button", { name: "Create my account" }).click();
  await expect(page.getByRole("heading", { name: "Your account is ready" })).toBeVisible();
  expect(completed).toBe(true);
  await expect(page).toHaveURL(/\/activate$/);
});

// An athlete cannot open the staff-only Users screen through a direct URL.
test("athlete is redirected away from user management", async ({ page }) => {
  await mockSession(page);
  await page.route("**/api/portal/availability", route => route.fulfill({ json: [] }));
  await page.goto("/users");
  await expect(page).toHaveURL(/\/portal$/);
  await expect(page.getByRole("heading", { name: "My Meets" })).toBeVisible();
  await expect(page.getByRole("button", { name: "+ Invite user" })).toBeHidden();
});

// Recovery uses a code and matching passwords without automatically signing in.
test("forgot password accepts a reset code and returns to sign in", async ({ page }) => {
  await mockSession(page, null);
  await page.route("**/api/auth/password-reset/request", route => route.fulfill({ json: { token: "fictional-reset-token", message: "If an active account uses this email, a reset code will arrive shortly." } }));
  await page.route("**/api/auth/password-reset/complete", route => {
    expect(route.request().postDataJSON()).toMatchObject({ token: "fictional-reset-token", code: "123456", password: "fictional-passphrase" });
    return route.fulfill({ status: 204 });
  });
  await page.goto("/login");
  await page.getByRole("link", { name: "Forgot password?" }).click();
  await page.getByLabel("Email address").fill("runner@example.test");
  await page.getByRole("button", { name: "Send reset code" }).click();
  await page.getByLabel("Reset code").fill("123456");
  await page.getByLabel(/^New password/).fill("fictional-passphrase");
  await page.getByLabel("Confirm password").fill("fictional-passphrase");
  await page.getByRole("button", { name: "Reset password", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Password updated" })).toBeVisible();
  await page.getByRole("link", { name: "Back to sign in" }).click();
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
});
