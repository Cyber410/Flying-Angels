import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import MeetsListPage from "./MeetsListPage";
import { createMeet, deleteMeet, getMeets, updateMeet } from "../services/meetService";
import type { Meet } from "../types/meet";

vi.mock("../services/meetService");

const fall: Meet = {
  id: 1, name: "Fall Invitational", meetDate: "2026-10-24",
  location: "Toronto, ON", externalUrl: null, eventCount: 3,
};
const summer: Meet = {
  id: 2, name: "Summer Championships", meetDate: "2026-08-15",
  location: "Ottawa, ON", externalUrl: "https://example.com/summer", eventCount: 2,
};
const spring: Meet = {
  id: 3, name: "Spring Classic", meetDate: "2026-05-10",
  location: null, externalUrl: null, eventCount: 0,
};

// jsdom does not implement the browser's modal display/focus behavior.
const originalShowModal = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, "showModal");
const originalClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, "close");
beforeAll(() => {
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
    configurable: true, value: function (this: HTMLDialogElement) { this.open = true; },
  });
  Object.defineProperty(HTMLDialogElement.prototype, "close", {
    configurable: true, value: function (this: HTMLDialogElement) { this.open = false; },
  });
});
afterAll(() => {
  if (originalShowModal) Object.defineProperty(HTMLDialogElement.prototype, "showModal", originalShowModal);
  else Reflect.deleteProperty(HTMLDialogElement.prototype, "showModal");
  if (originalClose) Object.defineProperty(HTMLDialogElement.prototype, "close", originalClose);
  else Reflect.deleteProperty(HTMLDialogElement.prototype, "close");
});

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(getMeets).mockResolvedValue([spring, fall, summer]);
});

function renderPage() {
  return render(<MemoryRouter initialEntries={["/meets"]}><MeetsListPage /></MemoryRouter>);
}

async function loadedPage() {
  renderPage();
  await screen.findByRole("rowheader", { name: fall.name });
}

describe("Meets List", () => {
  it("shows loading then renders real fields in date-descending order", async () => {
    renderPage();
    expect(screen.getByText("Loading meets...")).toBeInTheDocument();
    await screen.findByRole("rowheader", { name: fall.name });
    expect(screen.getAllByRole("rowheader").map((cell) => cell.textContent)).toEqual([
      fall.name, summer.name, spring.name,
    ]);
    const row = screen.getByRole("rowheader", { name: fall.name }).closest("tr")!;
    expect(within(row).getByText("Oct 24, 2026")).toBeInTheDocument();
    expect(within(row).getByText("Toronto, ON")).toBeInTheDocument();
    expect(within(row).getByText("3")).toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Status" })).not.toBeInTheDocument();
  });

  it("searches names and locations without additional API calls", async () => {
    const user = userEvent.setup();
    await loadedPage();
    const search = screen.getByRole("searchbox", { name: "Search meets" });
    await user.type(search, "  OTTAWA ");
    expect(screen.getAllByRole("rowheader")).toHaveLength(1);
    expect(screen.getByRole("rowheader", { name: summer.name })).toBeInTheDocument();
    await user.clear(search);
    await user.type(search, "fall");
    expect(screen.getByRole("rowheader", { name: fall.name })).toBeInTheDocument();
    expect(getMeets).toHaveBeenCalledTimes(1);
  });

  it("combines inclusive date bounds and search, and clears all filters", async () => {
    const user = userEvent.setup();
    await loadedPage();
    fireEvent.change(screen.getByLabelText("From date"), { target: { value: "2026-08-15" } });
    fireEvent.change(screen.getByLabelText("To date"), { target: { value: "2026-10-24" } });
    expect(screen.getAllByRole("rowheader")).toHaveLength(2);
    await user.type(screen.getByRole("searchbox"), "toronto");
    expect(screen.getAllByRole("rowheader")).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(screen.getAllByRole("rowheader")).toHaveLength(3);
    expect(screen.getByLabelText("From date")).toHaveValue("");
    expect(screen.getByLabelText("To date")).toHaveValue("");
  });

  it("explains an inverted date range", async () => {
    await loadedPage();
    fireEvent.change(screen.getByLabelText("From date"), { target: { value: "2026-10-24" } });
    fireEvent.change(screen.getByLabelText("To date"), { target: { value: "2026-08-15" } });
    expect(screen.getByRole("alert")).toHaveTextContent("From date must be on or before To date.");
    expect(screen.queryAllByRole("rowheader")).toHaveLength(0);
  });

  it("paginates and resets to the first page after filtering or changing page size", async () => {
    const user = userEvent.setup();
    vi.mocked(getMeets).mockResolvedValue(Array.from({ length: 12 }, (_, index) => ({
      ...fall, id: index + 1, name: `Meet ${String(index + 1).padStart(2, "0")}`,
    })));
    renderPage();
    await screen.findByRole("rowheader", { name: "Meet 01" });
    expect(screen.getAllByRole("rowheader")).toHaveLength(10);
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Next page" }));
    expect(screen.getAllByRole("rowheader")).toHaveLength(2);
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    await user.type(screen.getByRole("searchbox"), "Meet 01");
    expect(screen.getByRole("rowheader", { name: "Meet 01" })).toBeInTheDocument();
    expect(screen.getByText("Page 1 of 1")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Clear filters" }));
    await user.selectOptions(screen.getByRole("combobox", { name: "Meets per page" }), "25");
    expect(screen.getAllByRole("rowheader")).toHaveLength(12);
    expect(screen.getByRole("button", { name: "Next page" })).toBeDisabled();
  });

  it("distinguishes an empty list from no search matches", async () => {
    const user = userEvent.setup();
    vi.mocked(getMeets).mockResolvedValue([]);
    renderPage();
    await screen.findByText("No meets yet. Create your first meet to get started.");
    await user.type(screen.getByRole("searchbox"), "missing");
    expect(screen.getByText("No meets match your search or date range.")).toBeInTheDocument();
    expect(screen.getByText("0–0 of 0 meets")).toBeInTheDocument();
  });

  it("allows a failed load to be retried", async () => {
    const user = userEvent.setup();
    vi.mocked(getMeets).mockRejectedValueOnce(new Error("Unable to load meets."));
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent("Unable to load meets.");
    await user.click(screen.getByRole("button", { name: "Try again" }));
    await screen.findByRole("rowheader", { name: fall.name });
    expect(getMeets).toHaveBeenCalledTimes(2);
  });

  it("creates a meet with trimmed fields and null optional values", async () => {
    const user = userEvent.setup();
    const created = { ...fall, id: 4, name: "Winter Open", meetDate: "2026-12-01", location: null, eventCount: 0 };
    vi.mocked(createMeet).mockResolvedValue(created);
    await loadedPage();
    await user.click(screen.getByRole("button", { name: "+ New Meet" }));
    const dialog = screen.getByRole("dialog", { name: "New Meet" });
    await user.type(within(dialog).getByLabelText("Meet name"), "  Winter Open  ");
    fireEvent.change(within(dialog).getByLabelText("Meet date"), { target: { value: "2026-12-01" } });
    await user.click(within(dialog).getByRole("button", { name: "Create Meet" }));
    await screen.findByRole("rowheader", { name: "Winter Open" });
    expect(createMeet).toHaveBeenCalledWith({
      name: "Winter Open", meetDate: "2026-12-01", location: null, externalUrl: null,
    });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("retains form values and displays a failed save without adding a row", async () => {
    const user = userEvent.setup();
    vi.mocked(createMeet).mockRejectedValue(new Error("Meet date is required"));
    await loadedPage();
    await user.click(screen.getByRole("button", { name: "+ New Meet" }));
    await user.type(screen.getByLabelText("Meet name"), "Winter Open");
    fireEvent.change(screen.getByLabelText("Meet date"), { target: { value: "2026-12-01" } });
    await user.click(screen.getByRole("button", { name: "Create Meet" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Meet date is required");
    expect(screen.getByLabelText("Meet name")).toHaveValue("Winter Open");
    expect(screen.getAllByRole("rowheader")).toHaveLength(3);
  });

  it("rejects a whitespace-only name without making an API request", async () => {
    const user = userEvent.setup();
    await loadedPage();
    await user.click(screen.getByRole("button", { name: "+ New Meet" }));
    await user.type(screen.getByLabelText("Meet name"), "   ");
    fireEvent.change(screen.getByLabelText("Meet date"), { target: { value: "2026-12-01" } });
    await user.click(screen.getByRole("button", { name: "Create Meet" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Meet name is required.");
    expect(createMeet).not.toHaveBeenCalled();
  });

  it("prefills and saves edits to the correct meet", async () => {
    const user = userEvent.setup();
    vi.mocked(updateMeet).mockResolvedValue({ ...summer, name: "Summer Open" });
    await loadedPage();
    await user.click(screen.getByRole("button", { name: `Edit ${summer.name}` }));
    expect(screen.getByLabelText("Meet date")).toHaveValue(summer.meetDate);
    expect(screen.getByLabelText("Website (optional)")).toHaveValue(summer.externalUrl);
    await user.clear(screen.getByLabelText("Meet name"));
    await user.type(screen.getByLabelText("Meet name"), "Summer Open");
    await user.click(screen.getByRole("button", { name: "Save Changes" }));
    await screen.findByRole("rowheader", { name: "Summer Open" });
    expect(updateMeet).toHaveBeenCalledWith(summer.id, {
      name: "Summer Open", meetDate: summer.meetDate,
      location: summer.location, externalUrl: summer.externalUrl,
    });
    expect(screen.getByRole("rowheader", { name: fall.name })).toBeInTheDocument();
  });

  it("requires deletion confirmation, supports cancel, and clamps the last page", async () => {
    const user = userEvent.setup();
    vi.mocked(deleteMeet).mockResolvedValue(undefined);
    vi.mocked(getMeets).mockResolvedValue(Array.from({ length: 11 }, (_, index) => ({
      ...fall, id: index + 1, name: `Meet ${String(index + 1).padStart(2, "0")}`,
    })));
    renderPage();
    await screen.findByRole("rowheader", { name: "Meet 01" });
    await user.click(screen.getByRole("button", { name: "Next page" }));
    await user.click(screen.getByRole("button", { name: "Delete Meet 11" }));
    expect(deleteMeet).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Delete Meet 11" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Delete Meet" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(deleteMeet).toHaveBeenCalledWith(11);
    expect(screen.getByText("Page 1 of 1")).toBeInTheDocument();
    expect(screen.getAllByRole("rowheader")).toHaveLength(10);
  });

  it("keeps the meet and dialog when deletion fails", async () => {
    const user = userEvent.setup();
    vi.mocked(deleteMeet).mockRejectedValue(new Error("Unable to delete meet."));
    await loadedPage();
    await user.click(screen.getByRole("button", { name: `Delete ${fall.name}` }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Delete Meet" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Unable to delete meet.");
    expect(screen.getByRole("rowheader", { name: fall.name })).toBeInTheDocument();
  });

  it("blocks duplicate submissions and dismissal while saving", async () => {
    const user = userEvent.setup();
    let finishSave!: (meet: Meet) => void;
    vi.mocked(createMeet).mockReturnValue(new Promise((resolve) => { finishSave = resolve; }));
    await loadedPage();
    await user.click(screen.getByRole("button", { name: "+ New Meet" }));
    await user.type(screen.getByLabelText("Meet name"), "Winter Open");
    fireEvent.change(screen.getByLabelText("Meet date"), { target: { value: "2026-12-01" } });
    await user.click(screen.getByRole("button", { name: "Create Meet" }));
    expect(screen.getByRole("button", { name: "Saving..." })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Cancel" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Close dialog" })).toBeDisabled();
    fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    finishSave({ ...fall, id: 4, name: "Winter Open" });
    await screen.findByRole("rowheader", { name: "Winter Open" });
    expect(createMeet).toHaveBeenCalledTimes(1);
  });
});
