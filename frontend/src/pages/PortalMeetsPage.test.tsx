import { beforeEach, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import PortalMeetsPage from "./PortalMeetsPage";
import { getMyAvailability } from "../services/availabilityService";
import { availabilityRequest } from "../test/availabilityTestData";

vi.mock("../services/availabilityService", async /* Retain labels and formatting while replacing portal list requests. */ importOriginal => {
  const actual = await importOriginal<typeof import("../services/availabilityService")>();
  return { ...actual, getMyAvailability: vi.fn() };
});
beforeEach(/* Reset portal requests between isolated meet-card checks. */ () => { vi.resetAllMocks(); });

it("explains when changed assignments require reconfirmation", /* Verify that stale previous answers are not shown as current confirmations. */ async () => {
  vi.mocked(getMyAvailability).mockResolvedValue([availabilityRequest({ lastAnswer: "AVAILABLE", answeredRevision: 2 })]);
  render(<MemoryRouter><PortalMeetsPage /></MemoryRouter>);
  expect(await screen.findByText(/Your assigned events changed/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Respond" })).toBeInTheDocument();
  expect(screen.getByText("Awaiting response")).toBeInTheDocument();
});

it("shows saved availability while disabling responses after the deadline", /* Verify clear attendance status and closed meet behaviour. */ async () => {
  vi.mocked(getMyAvailability).mockResolvedValue([availabilityRequest({ status: "AVAILABLE", canRespond: false })]);
  render(<MemoryRouter><PortalMeetsPage /></MemoryRouter>);
  expect(await screen.findByText("Responses closed")).toBeInTheDocument();
  expect(screen.getByText("Available")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Update response" })).not.toBeInTheDocument();
});
