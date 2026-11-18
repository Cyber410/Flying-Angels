import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import PortalAthleteProfile from "./PortalAthleteProfile";
import { getPerformanceHistory } from "../services/performanceService";
import { getPortalRankingEvents } from "../services/portalRankingsService";
import { performanceHistory } from "../test/performanceTestData";
vi.mock("../services/performanceService");
vi.mock("../services/portalRankingsService");
beforeEach(/* Reset test state and mocks before each test. */ () => { vi.resetAllMocks(); vi.mocked(getPerformanceHistory).mockResolvedValue(performanceHistory()); vi.mocked(getPortalRankingEvents).mockResolvedValue([]); });

// Render the restricted profile without staff fields or arbitrary athlete identifiers.
function renderProfile() { render(<MemoryRouter><PortalAthleteProfile profile={{ firstName: "Fictional", lastName: "Runner", dateOfBirth: "2012-01-01", gender: "Female", results: [] }} /></MemoryRouter>); }

describe("athlete profile portal", /* Group the athlete profile portal checks. */ () => {
  it("offers personal bests without staff notes or editing actions", /* Verify that offers personal bests without staff notes or editing actions. */ async () => {
    const user = userEvent.setup(); renderProfile();
    expect(screen.queryByRole("tab", { name: "Notes" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /edit/i })).not.toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "Personal Bests" }));
    expect(await screen.findByRole("table", { name: "Personal bests by event" })).toBeInTheDocument();
    expect(getPerformanceHistory).toHaveBeenCalledWith(undefined, true, expect.any(AbortSignal));
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
  it("supports keyboard navigation between profile sections", /* Verify that supports keyboard navigation between profile sections. */ async () => {
    const user = userEvent.setup(); renderProfile();
    screen.getByRole("tab", { name: "Overview" }).focus();
    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "Meets & Results" })).toHaveFocus();
    expect(screen.getByRole("tab", { name: "Meets & Results" })).toHaveAttribute("aria-selected", "true");
    await user.keyboard("{End}");
    expect(screen.getByRole("tab", { name: "Rankings" })).toHaveFocus();
    await screen.findByText(/No meet events yet/);
    await user.keyboard("{ArrowLeft}");
    expect(screen.getByRole("tab", { name: "Personal Bests" })).toHaveFocus();
    await screen.findByRole("table", { name: "Personal bests by event" });
  });
});

// Keep competition and unlinked historical results independently discoverable.
it("combines history filters and clears an incompatible meet for historical marks", async () => {
  const user = userEvent.setup();
  render(<MemoryRouter><PortalAthleteProfile profile={{ firstName: "Demo", lastName: "Runner", dateOfBirth: "2012-01-01", gender: "Female", results: [
    { eventName: "100m", meetName: "Autumn", result: "12.4s", resultLabel: null },
    { eventName: "200m", meetName: "Spring", result: "26s", resultLabel: null },
    { eventName: "100m", meetName: null, result: "12.8s", resultLabel: null },
  ] }} /></MemoryRouter>);
  await user.click(screen.getByRole("tab", { name: "Meets & Results" }));
  await user.selectOptions(screen.getByRole("combobox", { name: "Meet" }), "Autumn");
  expect(screen.getByText("12.4s")).toBeVisible(); expect(screen.queryByText("26s")).not.toBeInTheDocument();
  await user.selectOptions(screen.getByRole("combobox", { name: "Record type" }), "historical");
  expect(screen.getByRole("combobox", { name: "Meet" })).toHaveValue("");
  expect(screen.getByText("12.8s")).toBeVisible(); expect(screen.queryByText("12.4s")).not.toBeInTheDocument();
  await user.selectOptions(screen.getByRole("combobox", { name: "Event" }), "200m");
  expect(screen.getByText("No results match these filters.")).toBeVisible();
  await user.click(screen.getByRole("button", { name: "Clear filters" }));
  expect(screen.getByText("3 matching results")).toBeVisible();
});
