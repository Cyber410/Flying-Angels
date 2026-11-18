import { beforeEach, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import RankingsPage from "./RankingsPage";
import { getRankingChoices, getStaffRankings } from "../services/rankingsService";
import type { RankingChoices, StaffRankings } from "../services/rankingsService";

vi.mock("../services/rankingsService");
const choices: RankingChoices = { categories: [{ id: 2, name: "100M", unit: "SECONDS", rankingOrder: "ASC" },
  { id: 4, name: "Long Jump", unit: "METRES", rankingOrder: "DESC" }], meets: [{ id: 3, name: "Demo Meet", startDate: "2026-10-01", endDate: "2026-10-01" }],
  events: [{ id: 10, name: "100M", eventDate: "2026-10-01", meetId: 3, meetName: "Demo Meet", categoryId: 2, categoryName: "100M", unit: "SECONDS", rankingOrder: "ASC" }],
  ages: { configured: false, ageRule: "Completed years", bands: [] } };
const rankings: StaffRankings = { category: choices.categories[0], status: "AVAILABLE", page: 0, size: 25,
  totalElements: 1, totalPages: 1, content: [{ position: 2, athleteId: 7, firstName: "Fictional", lastName: "Runner",
    gender: "Female", ageAtReference: 14, archived: false, resultId: 20, result: "12.45", resultLabel: null,
    score: 12.45, meetId: 3, meetName: "Demo Meet", eventId: 10, eventName: "100M", eventDate: "2026-10-01", profileAccessible: true, ageGroup: null }] };

beforeEach(/* Restore backend-shaped fictional choices and ranks for each frontend check. */ () => {
  vi.resetAllMocks(); vi.mocked(getRankingChoices).mockResolvedValue(choices); vi.mocked(getStaffRankings).mockResolvedValue(rankings);
});

it("keeps server ranks and links athletes to their personal bests", /* Verify staff navigation and explicit units. */ async () => {
  render(<MemoryRouter><RankingsPage /></MemoryRouter>);
  const link = await screen.findByRole("link", { name: "Fictional Runner" });
  expect(link).toHaveAttribute("href", "/athletes/7?tab=personal-bests");
  expect(screen.getByText("12.45 s")).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "2" })).toBeInTheDocument();
  expect(screen.queryByLabelText("Season")).not.toBeInTheDocument();
});

it("applies a competition event, gender, and age range using the event date", /* Verify supported filters and an explicit age reference. */ async () => {
  const user = userEvent.setup(); render(<MemoryRouter><RankingsPage /></MemoryRouter>); await screen.findByRole("table");
  await user.selectOptions(screen.getByRole("combobox", { name: "Meet" }), "3");
  await user.selectOptions(screen.getByRole("combobox", { name: "Competition event" }), "10");
  await user.selectOptions(screen.getByRole("combobox", { name: "Gender" }), "Female");
  await user.type(screen.getByLabelText("Minimum age"), "12"); await user.type(screen.getByLabelText("Maximum age"), "15");
  await user.click(screen.getByRole("button", { name: "Apply filters" })); await screen.findByRole("table");
  expect(getStaffRankings).toHaveBeenLastCalledWith({ categoryId: "2", meetId: "3", eventId: "10", gender: "Female", ageGroup: "", minAge: "12", maxAge: "15", ageAsOf: "2026-10-01", rankOrder: "ASC" }, 0, expect.any(AbortSignal));
});

it("uses club-supplied age bands instead of inventing group names", /* Verify configured age controls and field-event direction. */ async () => {
  vi.mocked(getRankingChoices).mockResolvedValue({ ...choices, ages: { configured: true, ageRule: "Completed years", bands: [{ label: "Academy Junior", minAge: 12, maxAge: 15 }] } });
  vi.mocked(getStaffRankings).mockResolvedValue({ ...rankings, category: choices.categories[1] });
  const user = userEvent.setup(); render(<MemoryRouter><RankingsPage /></MemoryRouter>); await screen.findByRole("table");
  expect(screen.queryByLabelText("Minimum age")).not.toBeInTheDocument();
  await user.selectOptions(screen.getByRole("combobox", { name: "Age group" }), "Academy Junior");
  await user.selectOptions(screen.getByRole("combobox", { name: "Event" }), "4");
  await user.click(screen.getByRole("button", { name: "Apply filters" })); await screen.findByRole("table");
  expect(screen.getByText(/Highest result ranks first/)).toBeInTheDocument();
  expect(getStaffRankings).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: "4", ageGroup: "Academy Junior", eventId: "" }), 0, expect.any(AbortSignal));
});

it("retries ranking failures and distinguishes filtered empty results", /* Verify recovery and useful empty-state wording. */ async () => {
  vi.mocked(getStaffRankings).mockRejectedValueOnce(new Error("Connection interrupted")).mockResolvedValue({ ...rankings, status: "NO_MATCHING_ATHLETES", content: [], totalElements: 0, totalPages: 0 });
  const user = userEvent.setup(); render(<MemoryRouter><RankingsPage /></MemoryRouter>);
  expect(await screen.findByRole("alert")).toHaveTextContent("Connection interrupted");
  await user.click(screen.getByRole("button", { name: "Try again" }));
  expect(await screen.findByText(/No athletes match these filters/)).toBeInTheDocument();
});

it("requests descending rank numbers when selected", /* Verify that descending order is sent to the server rather than sorting one page locally. */ async () => {
  const user = userEvent.setup(); render(<MemoryRouter><RankingsPage /></MemoryRouter>); await screen.findByRole("table");
  await user.selectOptions(screen.getByRole("combobox", { name: "Rank order" }), "DESC");
  await user.click(screen.getByRole("button", { name: "Apply filters" })); await screen.findByRole("table");
  expect(getStaffRankings).toHaveBeenLastCalledWith(expect.objectContaining({ rankOrder: "DESC" }), 0, expect.any(AbortSignal));
});
