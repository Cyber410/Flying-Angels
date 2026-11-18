import { apiFetch } from "./apiClient";
import { checkResponse } from "./apiResponse";
import type { EnrollmentInfo, InviteRequest, PendingInvitation, Session, UserAccount, UserUpdate } from "../types/auth";

// Read the server session without storing authentication data in local storage.
export async function getSession(): Promise<Session> {
  const response = await apiFetch("/api/auth/me", { cache: "no-store" });
  await checkResponse(response, "Unable to check your session. Please try again.");
  return response.json();
}

// Submit credentials and let Spring Security establish the session cookie.
export async function signIn(email: string, password: string): Promise<Session> {
  return requestJson("/api/auth/login", { email, password });
}

// Invalidate the server session before returning the interface to login.
export async function signOut(): Promise<void> {
  const response = await apiFetch("/api/auth/logout", { method: "POST" });
  await checkResponse(response, "Unable to sign out. Please try again.");
}

// Load existing accounts and pending invitations independently of athlete records.
export async function getUsers(): Promise<{ users: UserAccount[]; invitations: PendingInvitation[] }> {
  const responses = await Promise.all([apiFetch("/api/users"), apiFetch("/api/users/pending-invitations")]);
  await Promise.all(responses.map(/* Convert each response into the required display or request data. */ async (response) => checkResponse(response, "Unable to load user accounts.")));
  const users: UserAccount[] = await responses[0].json();
  const invitations: PendingInvitation[] = await responses[1].json();
  return { users, invitations };
}

// Send an invitation without creating an account before email verification.
export async function inviteUser(request: InviteRequest): Promise<PendingInvitation> {
  return requestJson("/api/users/pending-invitations", request);
}

// Replace an outstanding invitation email with a fresh link and code.
export async function resendInvitation(id: number): Promise<PendingInvitation> {
  return requestJson(`/api/users/pending-invitations/${id}/resend`, {});
}

// Cancel an unverified invitation so its link and code cannot create an account.
export async function cancelInvitation(id: number): Promise<void> {
  const response = await apiFetch(`/api/users/pending-invitations/${id}`, { method: "DELETE" });
  await checkResponse(response, "Unable to cancel this invitation.");
}

// Delete login access while retaining the linked athlete and competition records.
export async function deleteUser(id: number): Promise<void> {
  const response = await apiFetch(`/api/users/${id}`, { method: "DELETE" });
  await checkResponse(response, "Unable to delete this account.");
}

// Save administrator changes to an existing account's role, link, or active status.
export async function updateUser(id: number, request: UserUpdate): Promise<UserAccount> {
  return requestJson(`/api/users/${id}`, request, "PATCH");
}

// Read invitation details using the secret supplied in the email link.
export async function getEnrollment(token: string): Promise<EnrollmentInfo> {
  return requestJson("/api/auth/enrollment/info", { token });
}

// Verify the six-digit code before enabling password setup.
export async function verifyEnrollment(token: string, code: string): Promise<EnrollmentInfo> {
  return requestJson("/api/auth/enrollment/verify", { token, code });
}

// Request a replacement verification code subject to server resend limits.
export async function resendEnrollmentCode(token: string): Promise<EnrollmentInfo> {
  return requestJson("/api/auth/enrollment/resend", { token });
}

// Create the account only after the server has confirmed email ownership.
export async function completeEnrollment(token: string, password: string): Promise<UserAccount> {
  return requestJson("/api/auth/enrollment/complete", { token, password });
}

// Request recovery without revealing whether this email belongs to an account.
export async function requestPasswordReset(email: string): Promise<{ token: string; message: string }> {
  return requestJson("/api/auth/password-reset/request", { email });
}

// Replace a forgotten password only when the server accepts the emailed code.
export async function completePasswordReset(token: string, code: string, password: string): Promise<void> {
  const response = await apiFetch("/api/auth/password-reset/complete", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token, code, password }),
  });
  await checkResponse(response, "Unable to reset your password. Please try again.");
}

// Share JSON request handling across authentication and user-management actions.
async function requestJson<T>(url: string, body: unknown, method = "POST"): Promise<T> {
  const response = await apiFetch(url, { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  await checkResponse(response, "The request could not be completed. Please try again.");
  return response.json();
}
