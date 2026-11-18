import { beforeEach, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AvailabilityResponseDialog from "./AvailabilityResponseDialog";
import { AvailabilityConflict, getAvailability, saveAvailability } from "../services/availabilityService";
import { availabilityRequest } from "../test/availabilityTestData";

vi.mock("../services/availabilityService", async /* Preserve real conflict types and labels while replacing transport. */ importOriginal => {
  const actual = await importOriginal<typeof import("../services/availabilityService")>();
  return { ...actual, getAvailability: vi.fn(), saveAvailability: vi.fn() };
});
beforeEach(/* Reset the current request before each dialog check. */ () => {
  vi.resetAllMocks(); vi.mocked(getAvailability).mockResolvedValue(availabilityRequest());
  vi.mocked(saveAvailability).mockResolvedValue(availabilityRequest({ status: "AVAILABLE", version: 5 }));
});

it("requires an explicit portal choice and submits the freshly loaded version", /* Verify user confirmation and revision handling. */ async () => {
  const saved = vi.fn(); const user = userEvent.setup();
  render(<AvailabilityResponseDialog id={8} portal onClose={vi.fn()} onSaved={saved} />);
  const submit = await screen.findByRole("button", { name: "Save response" }); expect(submit).toBeDisabled();
  expect(screen.queryByLabelText("Staff note (optional)")).not.toBeInTheDocument();
  await user.click(screen.getByRole("radio", { name: /Available — I can attend/ })); await user.click(submit);
  expect(saveAvailability).toHaveBeenCalledWith(availabilityRequest(), "AVAILABLE", true, undefined);
  expect(saved).toHaveBeenCalledWith(expect.objectContaining({ status: "AVAILABLE", version: 5 }));
});

it("blocks conflicting retries until the request is reloaded and reconfirmed", /* Verify that stale submissions are never silently retried. */ async () => {
  vi.mocked(saveAvailability).mockRejectedValueOnce(new AvailabilityConflict("Assignments changed"));
  const user = userEvent.setup(); render(<AvailabilityResponseDialog id={8} portal onClose={vi.fn()} onSaved={vi.fn()} />);
  await user.click(await screen.findByRole("radio", { name: /Available — I can attend/ }));
  await user.click(screen.getByRole("button", { name: "Save response" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Assignments changed");
  expect(screen.getByRole("button", { name: "Save response" })).toBeDisabled();
  vi.mocked(getAvailability).mockResolvedValue(availabilityRequest({ version: 6, assignmentRevision: 4 }));
  await user.click(screen.getByRole("button", { name: "Reload request" }));
  expect(await screen.findByRole("button", { name: "Save response" })).toBeDisabled();
  expect(saveAvailability).toHaveBeenCalledTimes(1);
});

it("requires a staff response source and keeps the note with the external reply", /* Verify staff-only response metadata. */ async () => {
  const user = userEvent.setup(); render(<AvailabilityResponseDialog id={8} onClose={vi.fn()} onSaved={vi.fn()} />);
  await user.click(await screen.findByRole("radio", { name: /Unavailable — cannot attend/ }));
  expect(screen.getByRole("button", { name: "Save response" })).toBeDisabled();
  await user.selectOptions(screen.getByRole("combobox", { name: "Response received via" }), "WHATSAPP");
  await user.type(screen.getByLabelText("Staff note (optional)"), "Guardian confirmed a scheduling conflict.");
  await user.click(screen.getByRole("button", { name: "Save response" }));
  expect(saveAvailability).toHaveBeenCalledWith(availabilityRequest(), "UNAVAILABLE", false, { communicationMethod: "WHATSAPP", note: "Guardian confirmed a scheduling conflict." });
});

it("shows a closed response window without offering a save action", /* Verify that ended response windows cannot be submitted. */ async () => {
  vi.mocked(getAvailability).mockResolvedValue(availabilityRequest({ canRespond: false }));
  render(<AvailabilityResponseDialog id={8} portal onClose={vi.fn()} onSaved={vi.fn()} />);
  expect(await screen.findByText("Responses are closed for this meet.")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Save response" })).not.toBeInTheDocument();
});
