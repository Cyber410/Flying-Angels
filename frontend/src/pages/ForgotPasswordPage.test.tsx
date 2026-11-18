import { beforeEach, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import ForgotPasswordPage from "./ForgotPasswordPage";
import { completePasswordReset, requestPasswordReset } from "../services/authService";

vi.mock("../services/authService");
// Keep request responses identical for known and unknown account addresses.
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(requestPasswordReset).mockResolvedValue({ token: "fictional-challenge", message: "If an active account uses this email, a reset code will arrive shortly." });
  vi.mocked(completePasswordReset).mockResolvedValue();
});

// Enter recovery through the same public page used from the sign-in screen.
async function requestCode() {
  const user = userEvent.setup();
  render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>);
  await user.type(screen.getByLabelText("Email address"), "fictional@example.test");
  await user.click(screen.getByRole("button", { name: "Send reset code" }));
  await screen.findByLabelText("Reset code");
  return user;
}

// Require an emailed code and a matching password before confirming success.
it("requests a code and resets without automatically signing in", async () => {
  const user = await requestCode();
  expect(requestPasswordReset).toHaveBeenCalledWith("fictional@example.test");
  expect(screen.getByRole("status")).toHaveTextContent("If an active account");
  expect(screen.getByRole("button", { name: /Request another code in/ })).toBeDisabled();
  expect(screen.getByRole("button", { name: "Reset password" })).toBeDisabled();
  await user.type(screen.getByLabelText("Reset code"), "123456");
  await user.type(screen.getByLabelText("New password"), "fictional-new-passphrase");
  await user.type(screen.getByLabelText("Confirm password"), "fictional-new-passphrase");
  await user.click(screen.getByRole("button", { name: "Reset password" }));
  expect(await screen.findByRole("heading", { name: "Password updated" })).toBeInTheDocument();
  expect(completePasswordReset).toHaveBeenCalledWith("fictional-challenge", "123456", "fictional-new-passphrase");
  expect(screen.getByRole("link", { name: "Back to sign in" })).toHaveAttribute("href", "/login");
  expect(screen.queryByLabelText("New password")).not.toBeInTheDocument();
});

// A confirmation typo must not consume an attempt at the backend.
it("rejects mismatched passwords before calling the reset API", async () => {
  const user = await requestCode();
  await user.type(screen.getByLabelText("Reset code"), "123456");
  await user.type(screen.getByLabelText("New password"), "fictional-passphrase-one");
  await user.type(screen.getByLabelText("Confirm password"), "fictional-passphrase-two");
  await user.click(screen.getByRole("button", { name: "Reset password" }));
  expect(screen.getByRole("alert")).toHaveTextContent("do not match");
  expect(completePasswordReset).not.toHaveBeenCalled();
});

// Retain a recoverable form when the server rejects an expired or incorrect code.
it("shows rejected codes and lets the user restart with another email", async () => {
  vi.mocked(completePasswordReset).mockRejectedValue(new Error("The reset code is incorrect, expired, or already used."));
  const user = await requestCode();
  await user.type(screen.getByLabelText("Reset code"), "123456");
  await user.type(screen.getByLabelText("New password"), "fictional-new-passphrase");
  await user.type(screen.getByLabelText("Confirm password"), "fictional-new-passphrase");
  await user.click(screen.getByRole("button", { name: "Reset password" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("expired");
  await user.click(screen.getByRole("button", { name: "Use a different email" }));
  expect(screen.getByLabelText("Email address")).toBeInTheDocument();
  expect(screen.queryByLabelText("New password")).not.toBeInTheDocument();
});

// A failed request should stay at email entry and offer a normal retry.
it("handles request failures without advancing to password entry", async () => {
  vi.mocked(requestPasswordReset).mockRejectedValue(new Error("Connection interrupted"));
  const user = userEvent.setup(); render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>);
  await user.type(screen.getByLabelText("Email address"), "fictional@example.test");
  await user.click(screen.getByRole("button", { name: "Send reset code" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Connection interrupted");
  expect(screen.queryByLabelText("New password")).not.toBeInTheDocument();
});
