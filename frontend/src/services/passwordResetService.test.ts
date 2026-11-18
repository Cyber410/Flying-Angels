import { beforeEach, expect, it, vi } from "vitest";
import { apiFetch } from "./apiClient";
import { completePasswordReset, requestPasswordReset } from "./authService";

vi.mock("./apiClient");
// Reset transport mocks without persisting any recovery secrets.
beforeEach(() => vi.resetAllMocks());

// Keep email recovery on the public POST endpoint, with no secrets in the URL.
it("posts the account email and returns the opaque challenge", async () => {
  vi.mocked(apiFetch).mockResolvedValue(new Response(JSON.stringify({ token: "fictional-token", message: "Check your email" })));
  expect(await requestPasswordReset("fictional@example.test")).toEqual({ token: "fictional-token", message: "Check your email" });
  expect(apiFetch).toHaveBeenCalledWith("/api/auth/password-reset/request", expect.objectContaining({ method: "POST", body: JSON.stringify({ email: "fictional@example.test" }) }));
});

// Accept the backend's empty success response without trying to parse JSON.
it("submits code and password and handles a 204 success", async () => {
  vi.mocked(apiFetch).mockResolvedValue(new Response(null, { status: 204 }));
  await expect(completePasswordReset("fictional-token", "123456", "fictional-passphrase")).resolves.toBeUndefined();
  expect(apiFetch).toHaveBeenCalledWith("/api/auth/password-reset/complete", expect.objectContaining({ method: "POST", body: JSON.stringify({ token: "fictional-token", code: "123456", password: "fictional-passphrase" }) }));
});
