import { beforeEach, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import AvailabilityPage from "./AvailabilityPage";
import { getAvailabilityHistory, getAvailabilityOverview } from "../services/availabilityService";
import { getMeets } from "../services/meetService";
import { availabilityOverview } from "../test/availabilityTestData";

vi.mock("../services/availabilityService", async /* Keep shared formatting while stubbing staff reads. */ importOriginal => {
  const actual = await importOriginal<typeof import("../services/availabilityService")>();
  return { ...actual, getAvailabilityOverview: vi.fn(), getAvailabilityHistory: vi.fn() };
});
vi.mock("../services/meetService");
beforeEach(/* Restore a small fictional overview and its meet choices. */ () => {
  vi.resetAllMocks(); vi.mocked(getAvailabilityOverview).mockResolvedValue(availabilityOverview());
  vi.mocked(getAvailabilityHistory).mockResolvedValue([]);
  vi.mocked(getMeets).mockResolvedValue([{ id: 2, name: "Winter Demo Meet", meetDate: "2026-12-10", location: "Toronto", externalUrl: null, eventCount: 1 }]);
});

it("applies meet and athlete filters together and shows staff audit history", /* Verify filtering and staff-only history entry. */ async () => {
  const user = userEvent.setup(); render(<MemoryRouter><AvailabilityPage /></MemoryRouter>);
  await screen.findByRole("table"); await screen.findByRole("option", { name: "Winter Demo Meet" });
  await user.selectOptions(screen.getByRole("combobox", { name: "Meet" }), "2");
  await user.type(screen.getByRole("searchbox", { name: "Search athletes" }), "Runner");
  await user.click(screen.getByRole("button", { name: "Apply" })); await screen.findByRole("table");
  expect(getAvailabilityOverview).toHaveBeenLastCalledWith({ meetId: "2", status: "", view: "ALL", search: "Runner" }, 0, expect.any(AbortSignal));
  await user.click(screen.getByRole("button", { name: "History" }));
  expect(await screen.findByText("No responses have been recorded yet.")).toBeInTheDocument();
  expect(getAvailabilityHistory).toHaveBeenCalledWith(8, expect.any(AbortSignal));
});

it("opens a meet-specific link with all schedules so past nominations remain visible", /* Verify direct meet links do not inherit the upcoming-only default. */ async () => {
  render(<MemoryRouter initialEntries={["/availability?meetId=2"]}><AvailabilityPage /></MemoryRouter>);
  await screen.findByRole("table");
  expect(screen.getByRole("combobox", { name: "Schedule" })).toHaveValue("ALL");
  expect(getAvailabilityOverview).toHaveBeenCalledWith(expect.objectContaining({ meetId: "2", view: "ALL" }), 0, expect.any(AbortSignal));
});

it("explains an empty upcoming selection and displays its filtered zero summary", /* Verify that past-meet filtering cannot imply hidden matching requests. */ async () => {
  vi.mocked(getAvailabilityOverview).mockResolvedValue({ ...availabilityOverview(), content: [], totalElements: 0, totalPages: 0,
    summary: { total: 0, available: 0, unavailable: 0, awaitingResponse: 0 } });
  const user = userEvent.setup(); render(<MemoryRouter initialEntries={["/availability?meetId=2"]}><AvailabilityPage /></MemoryRouter>);
  await screen.findByRole("table");
  await user.selectOptions(screen.getByRole("combobox", { name: "Schedule" }), "UPCOMING");
  await user.click(screen.getByRole("button", { name: "Apply" }));
  expect(await screen.findByText(/For a past meet, choose Past or All/)).toBeInTheDocument();
  expect(screen.getByLabelText("Meet response summary")).toHaveTextContent("0Available0Awaiting response0Unavailable");
});

it("shows summary totals and a closed action for past response windows", /* Verify summary labels and response deadline enforcement. */ async () => {
  const overview = availabilityOverview(); overview.content[0].canRespond = false;
  vi.mocked(getAvailabilityOverview).mockResolvedValue(overview);
  render(<MemoryRouter><AvailabilityPage /></MemoryRouter>); await screen.findByRole("table");
  expect(screen.getByLabelText("Meet response summary")).toHaveTextContent("Awaiting response");
  expect(screen.getByText("Responses closed")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Record response" })).not.toBeInTheDocument();
});
