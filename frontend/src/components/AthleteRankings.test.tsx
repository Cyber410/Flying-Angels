import { beforeEach, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AthleteRankings from "./AthleteRankings";
import { getPortalRankingEvents, getPortalRankings } from "../services/portalRankingsService";
import type { PortalRanking, RankingEvent } from "../services/portalRankingsService";

vi.mock("../services/portalRankingsService");
const event: RankingEvent = { id: 10, name: "100m", eventDate: "2026-11-01", meetId: 2,
  meetName: "Autumn Meet", categoryId: 1, categoryName: "100m", unit: "SECONDS", rankingOrder: "ASC" };
const rankings: PortalRanking = { category: { name: "100m", unit: "SECONDS", rankingOrder: "ASC" }, status: "AVAILABLE",
  content: [{ position: 1, firstName: "Robin", lastName: "Example", result: "12.1", score: 12.1, profileAccessible: false, athleteId: null, ageGroup: "U15", unit: "SECONDS" },
    { position: 2, firstName: "Sam", lastName: "Fiction", result: "12.4", score: 12.4, profileAccessible: true, athleteId: 7, ageGroup: "U15", unit: "SECONDS" }],
  page: 0, size: 25, totalElements: 2, totalPages: 1 };

beforeEach(/* Reset fictional rankings for each isolated frontend check. */ () => {
  vi.resetAllMocks(); vi.mocked(getPortalRankingEvents).mockResolvedValue([event]); vi.mocked(getPortalRankings).mockResolvedValue(rankings);
});

it("highlights the athlete and renders competitor names without profile links", /* Verify scoped display and the own-athlete indicator. */ async () => {
  render(<AthleteRankings />);
  const table = await screen.findByRole("table");
  expect(within(table).getByText("Robin Example")).toBeInTheDocument();
  expect(within(table).getAllByRole("columnheader")).toHaveLength(3);
  expect(within(within(table).getAllByRole("row")[1]).getAllByRole("cell")).toHaveLength(2);
  expect(within(table).getByText("You").closest("tr")).toHaveClass("ranking-own-row");
  expect(within(table).getByText("12.4 s")).toBeInTheDocument();
  expect(screen.queryByRole("link")).not.toBeInTheDocument();
  expect(getPortalRankings).toHaveBeenCalledWith(event, 0, expect.any(AbortSignal), expect.objectContaining({ gender: "" }));
});

it("resets the event selection when changing meet", /* Verify that meet changes request the matching event only. */ async () => {
  const other = { ...event, id: 11, meetId: 3, meetName: "Winter Meet" };
  vi.mocked(getPortalRankingEvents).mockResolvedValue([event, other]);
  render(<AthleteRankings />); await screen.findByRole("table");
  await userEvent.setup().selectOptions(screen.getByRole("combobox", { name: "Meet" }), "3");
  await screen.findByRole("table", { name: "100m rankings at Winter Meet" });
  expect(getPortalRankings).toHaveBeenLastCalledWith(other, 0, expect.any(AbortSignal), expect.objectContaining({ gender: "" }));
});

it("distinguishes no assigned events from events awaiting results", /* Verify useful empty states without invented rankings. */ async () => {
  vi.mocked(getPortalRankingEvents).mockResolvedValueOnce([]);
  const view = render(<AthleteRankings />);
  expect(await screen.findByText(/No meet events yet/)).toBeInTheDocument();
  expect(getPortalRankings).not.toHaveBeenCalled(); view.unmount();
  vi.mocked(getPortalRankings).mockResolvedValue({ ...rankings, content: [], totalElements: 0, totalPages: 0, status: "NO_EVENT_RESULTS" });
  render(<AthleteRankings />);
  expect(await screen.findByText(/No ranked results have been recorded/)).toBeInTheDocument();
});

it("retries failed rankings and pages using server positions", /* Verify recovery and server pagination without recomputing ranks. */ async () => {
  vi.mocked(getPortalRankings).mockRejectedValueOnce(new Error("Connection interrupted")).mockResolvedValue({ ...rankings, totalElements: 26, totalPages: 2 });
  render(<AthleteRankings />); expect(await screen.findByRole("alert")).toHaveTextContent("Connection interrupted");
  const user = userEvent.setup(); await user.click(screen.getByRole("button", { name: "Try again" }));
  await screen.findByRole("table"); await user.click(screen.getByRole("button", { name: "Next" }));
  await screen.findByText(/Page 2 of 2/);
  expect(getPortalRankings).toHaveBeenLastCalledWith(event, 1, expect.any(AbortSignal), expect.objectContaining({ gender: "" }));
});

// Check that filters request a complete, freshly paged cohort rather than filtering one page locally.
it("filters by age and gender, resets pagination, and clears the cohort", async () => {
  const user = userEvent.setup();
  vi.mocked(getPortalRankings).mockResolvedValue({ ...rankings, totalElements: 26, totalPages: 2 });
  render(<AthleteRankings />); await screen.findByRole("table");
  expect(screen.queryByRole("columnheader", { name: "Age group" })).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Next" }));
  await screen.findByText(/Page 2 of 2/);
  await user.selectOptions(screen.getByRole("combobox", { name: "Age group" }), "5:14");
  await screen.findByText(/Page 1 of 2/);
  expect(getPortalRankings).toHaveBeenLastCalledWith(event, 0, expect.any(AbortSignal), { gender: "", minAge: 5, maxAge: 14 });
  await user.selectOptions(screen.getByRole("combobox", { name: "Gender" }), "Female");
  await screen.findByRole("table");
  expect(getPortalRankings).toHaveBeenLastCalledWith(event, 0, expect.any(AbortSignal), { gender: "Female", minAge: 5, maxAge: 14 });
  vi.mocked(getPortalRankings).mockResolvedValue({ ...rankings, content: [], totalElements: 0, totalPages: 0 });
  await user.selectOptions(screen.getByRole("combobox", { name: "Age group" }), "19:150");
  expect(await screen.findByText(/No ranked results match these filters/)).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Clear filters" }));
  expect(await screen.findByText(/No ranked results have been recorded/)).toBeInTheDocument();
});
