import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import AthletePersonalBests from "./AthletePersonalBests";
import { getPerformanceHistory } from "../services/performanceService";
import { performanceHistory, timedPerformance } from "../test/performanceTestData";
vi.mock("../services/performanceService");
beforeEach(/* Reset test state and mocks before each test. */ () => { vi.resetAllMocks(); vi.mocked(getPerformanceHistory).mockResolvedValue(performanceHistory()); });

// Render the shared performance view in its staff or athlete context.
function renderBests(portal = false) { render(<MemoryRouter><AthletePersonalBests athleteId={portal ? undefined : 7} portal={portal} /></MemoryRouter>); }

describe("personal bests", /* Group the personal bests checks. */ () => {
  it("uses the server best rather than the latest result and displays the dated progression", /* Verify that uses the server best rather than the latest result and displays the dated progression. */ async () => {
    renderBests(); const table = await screen.findByRole("table", { name: "Personal bests by event" });
    expect(within(table).getByText("12.1 s")).toBeInTheDocument();
    expect(within(table).getByRole("link", { name: "Summer Meet" })).toHaveAttribute("href", "/meets/2");
    expect(within(table).queryByText("12.4 s")).not.toBeInTheDocument();
    expect(screen.getByRole("img", { name: /100m performance progression/ })).toBeInTheDocument();
    expect(screen.getByText("4 valid of 5 recorded attempts")).toBeInTheDocument();
  });
  it("distinguishes an invalid-only event from an event with no recorded results", /* Verify that distinguishes an invalid-only event from an event with no recorded results. */ async () => {
    const user = userEvent.setup(); renderBests(); await screen.findByRole("combobox", { name: "View progression for" });
    await user.selectOptions(screen.getByRole("combobox", { name: "View progression for" }), "2:METRES:DESC");
    expect(screen.getByText("No valid competition performances are available for this event.")).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    await user.selectOptions(screen.getByRole("combobox", { name: "View progression for" }), "3:METRES:DESC");
    expect(screen.getByText("No results have been recorded for this event yet.")).toBeInTheDocument();
  });
  it("shows a single performance without inventing a trend", /* Verify that shows a single performance without inventing a trend. */ async () => {
    const series = timedPerformance(); series.progression = series.progression.slice(0, 1); series.personalBest = series.progression[0]; series.recordedAttemptCount = 1; series.validAttemptCount = 1; series.progressionStatus = "SINGLE_COMPETITION";
    vi.mocked(getPerformanceHistory).mockResolvedValue({ ...performanceHistory(), performances: [series] });
    renderBests(); expect(await screen.findByText(/One competition recorded/)).toBeInTheDocument(); expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });
  it("loads the own-athlete endpoint and omits staff meet links in the portal", /* Verify that loads the own-athlete endpoint and omits staff meet links in the portal. */ async () => {
    renderBests(true); await screen.findByRole("table", { name: "Personal bests by event" });
    expect(getPerformanceHistory).toHaveBeenCalledWith(undefined, true, expect.any(AbortSignal));
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
  it("handles empty history and retries a failed request", /* Verify that handles empty history and retries a failed request. */ async () => {
    vi.mocked(getPerformanceHistory).mockRejectedValueOnce(new Error("Connection interrupted"));
    const user = userEvent.setup(); renderBests(); expect(await screen.findByRole("alert")).toHaveTextContent("Connection interrupted");
    vi.mocked(getPerformanceHistory).mockResolvedValue({ ...performanceHistory(), performances: [], status: "NO_COMPETITION_HISTORY" });
    await user.click(screen.getByRole("button", { name: "Refresh" })); expect(await screen.findByText(/No competition results yet/)).toBeInTheDocument();
  });
});

// Keep the table and progression aligned while preserving the server's all-time best.
it("filters personal bests by event and result type with a clearable empty state", async () => {
  const user = userEvent.setup(); renderBests(true);
  await screen.findByRole("table", { name: "Personal bests by event" });
  await user.selectOptions(screen.getByRole("combobox", { name: "Event" }), "1");
  expect(screen.getByRole("img", { name: /100m performance progression/ })).toBeInTheDocument();
  expect(screen.queryByRole("rowheader", { name: "Long Jump" })).not.toBeInTheDocument();
  await user.selectOptions(screen.getByRole("combobox", { name: "Result type" }), "METRES");
  expect(screen.getByText(/No personal best events match/)).toBeInTheDocument();
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Clear filters" }));
  expect(screen.getByRole("rowheader", { name: "Long Jump" })).toBeInTheDocument();
  expect(within(screen.getByRole("table", { name: "Personal bests by event" })).getByText("12.1 s")).toBeInTheDocument();
});
