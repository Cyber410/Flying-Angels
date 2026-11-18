import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import ActivateAccountPage from "./ActivateAccountPage";
import { completeEnrollment, getEnrollment, resendEnrollmentCode, verifyEnrollment } from "../services/authService";

vi.mock("../services/authService");
const info = { email: "fictional@example.test", name: "Fictional Runner", role: "ATHLETE" as const, verified: false, expiresAt: "2026-12-01T12:00:00Z", codeExpiresAt: "2026-12-01T11:10:00Z" };
beforeEach(/* Reset test state and mocks before each test. */ () => {
  vi.resetAllMocks(); window.history.replaceState(null, "", "/activate#token=invitation-secret");
  vi.mocked(getEnrollment).mockResolvedValue(info);
});

// Render the public setup page with its normal navigation context.
function renderPage() { render(<MemoryRouter><ActivateAccountPage /></MemoryRouter>); }

describe("verified account setup", /* Group the verified account setup checks. */ () => {
  it("requires code verification before showing password setup or creating an account", /* Verify that requires code verification before showing password setup or creating an account. */ async () => {
    const user = userEvent.setup(); renderPage();
    await screen.findByLabelText("Verification code");
    expect(screen.queryByLabelText("New password")).not.toBeInTheDocument();
    expect(completeEnrollment).not.toHaveBeenCalled();
    vi.mocked(verifyEnrollment).mockResolvedValue({ ...info, verified: true });
    await user.type(screen.getByLabelText("Verification code"), "123456");
    await user.click(screen.getByRole("button", { name: "Verify email" }));
    await screen.findByLabelText("New password");
    expect(verifyEnrollment).toHaveBeenCalledWith("invitation-secret", "123456");
    expect(completeEnrollment).not.toHaveBeenCalled();
    vi.mocked(completeEnrollment).mockResolvedValue({ id: 5, email: info.email, name: info.name, role: "ATHLETE", active: true, athleteId: 7, accountStatus: "ACTIVE" });
    await user.type(screen.getByLabelText("New password"), "fictional-passphrase");
    await user.type(screen.getByLabelText("Confirm password"), "fictional-passphrase");
    await user.click(screen.getByRole("button", { name: "Create my account" }));
    expect(await screen.findByText("Your account is ready")).toBeInTheDocument();
    expect(completeEnrollment).toHaveBeenCalledWith("invitation-secret", "fictional-passphrase");
    expect(window.location.hash).toBe("");
  });

  it("keeps the code step visible when the server rejects a code", /* Verify that keeps the code step visible when the server rejects a code. */ async () => {
    vi.mocked(verifyEnrollment).mockRejectedValue(new Error("The verification code is incorrect."));
    const user = userEvent.setup(); renderPage(); await screen.findByLabelText("Verification code");
    await user.type(screen.getByLabelText("Verification code"), "000000"); await user.click(screen.getByRole("button", { name: "Verify email" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("incorrect");
    expect(screen.queryByLabelText("New password")).not.toBeInTheDocument(); expect(completeEnrollment).not.toHaveBeenCalled();
  });

  it("requests replacement email codes without creating an account", /* Verify that requests replacement email codes without creating an account. */ async () => {
    vi.mocked(resendEnrollmentCode).mockResolvedValue(info);
    const user = userEvent.setup(); renderPage(); await screen.findByLabelText("Verification code");
    await user.click(screen.getByRole("button", { name: "Send a new verification code" }));
    expect(await screen.findByRole("status")).toHaveTextContent("queued");
    expect(resendEnrollmentCode).toHaveBeenCalledWith("invitation-secret"); expect(completeEnrollment).not.toHaveBeenCalled();
  });
});
