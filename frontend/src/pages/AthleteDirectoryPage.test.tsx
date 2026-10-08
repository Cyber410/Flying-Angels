import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AthleteDirectoryPage from "./AthleteDirectoryPage";
import {
  getAthleteById,
  getAthletes,
} from "../services/athleteService";
import {
  buildAthlete,
  buildEvent,
  directoryAthletes,
  manyAthletes,
} from "../test/athleteTestData";

/**
 * UC-01 Centralized Athlete Directory and Search - component tests.
 *
 * The service module is mocked, so these tests check only what the page
 * does with the data: what it shows, and how search, filters, pagination
 * and navigation behave. No backend or database is involved.
 */

vi.mock("../services/athleteService");

const mockGetAthletes = vi.mocked(getAthletes);
const mockGetAthleteById = vi.mocked(getAthleteById);

function renderDirectory() {
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/" element={<AthleteDirectoryPage />} />
        <Route
          path="/athletes/:athleteId"
          element={<p>Profile route reached</p>}
        />
      </Routes>
    </MemoryRouter>
  );
}

/** Each athlete in the list is a button whose name contains "Athlete #". */
function athleteRows() {
  return screen.queryAllByRole("button", { name: /Athlete #/ });
}

function searchBox() {
  return screen.getByPlaceholderText("Search by athlete name...");
}

beforeEach(() => {
  // The page logs caught errors; keep the test output readable.
  vi.spyOn(console, "error").mockImplementation(() => {});
});

describe("UC-01 Athlete Directory - loading the list", () => {
  it("shows a loading message while athletes are being retrieved", () => {
    mockGetAthletes.mockReturnValue(new Promise(() => {}));

    renderDirectory();

    expect(
      screen.getByText("Loading athletes...")
    ).toBeInTheDocument();
  });

  it("lists every athlete with name and athlete number", async () => {
    mockGetAthletes.mockResolvedValue(directoryAthletes());

    renderDirectory();

    expect(
      await screen.findByRole("button", {
        name: /Maya Johnson.*Athlete #\s*1/,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Liam Chen/ })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Sofia Martinez/ })
    ).toBeInTheDocument();
    expect(athleteRows()).toHaveLength(3);
    expect(
      screen.getByText("3 athletes found")
    ).toBeInTheDocument();
  });

  it("shows an empty state when there are no athletes", async () => {
    mockGetAthletes.mockResolvedValue([]);

    renderDirectory();

    expect(
      await screen.findByText("No athletes found.")
    ).toBeInTheDocument();
    expect(athleteRows()).toHaveLength(0);
  });

  it("shows an error message when the athletes cannot be loaded", async () => {
    mockGetAthletes.mockRejectedValue(new Error("Network down"));

    renderDirectory();

    expect(
      await screen.findByText("Unable to load athletes.")
    ).toBeInTheDocument();
    expect(
      screen.getByText("Athlete information could not be loaded.")
    ).toBeInTheDocument();
    expect(athleteRows()).toHaveLength(0);
  });
});

describe("UC-01 Athlete Directory - search", () => {
  beforeEach(() => {
    mockGetAthletes.mockResolvedValue(directoryAthletes());
  });

  it("narrows the list to athletes whose name matches", async () => {
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByRole("button", { name: /Maya Johnson/ });

    await user.type(searchBox(), "chen");

    expect(athleteRows()).toHaveLength(1);
    expect(
      screen.getByRole("button", { name: /Liam Chen/ })
    ).toBeInTheDocument();
    expect(
      screen.getByText("1 athlete found")
    ).toBeInTheDocument();
  });

  it("ignores letter case and surrounding spaces", async () => {
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByRole("button", { name: /Maya Johnson/ });

    await user.type(searchBox(), "  MAYA john  ");

    expect(athleteRows()).toHaveLength(1);
    expect(
      screen.getByRole("button", { name: /Maya Johnson/ })
    ).toBeInTheDocument();
  });

  it("shows the empty state when nothing matches", async () => {
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByRole("button", { name: /Maya Johnson/ });

    await user.type(searchBox(), "zzzz");

    expect(
      screen.getByText("No athletes found.")
    ).toBeInTheDocument();
    expect(
      screen.getByText("Try changing your search or filters.")
    ).toBeInTheDocument();
    expect(athleteRows()).toHaveLength(0);
  });

  it("restores the full list when 'Clear all' is clicked", async () => {
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByRole("button", { name: /Maya Johnson/ });
    await user.type(searchBox(), "zzzz");

    await user.click(
      screen.getByRole("button", { name: "Clear all" })
    );

    expect(searchBox()).toHaveValue("");
    expect(athleteRows()).toHaveLength(3);
  });
});

describe("UC-01 Athlete Directory - filters", () => {
  beforeEach(() => {
    mockGetAthletes.mockResolvedValue(directoryAthletes());
    // Opening the filter panel loads each athlete's profile for its events.
    mockGetAthleteById.mockImplementation(async (id) =>
      buildAthlete({
        id,
        events:
          id === 2
            ? [buildEvent({ athleteId: 2, categoryName: "200m" })]
            : [buildEvent({ athleteId: id, categoryName: "100M" })],
      })
    );
  });

  it("filters by age group", async () => {
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByRole("button", { name: /Maya Johnson/ });

    await user.click(
      screen.getByRole("button", { name: /^Filters/ })
    );
    await user.click(
      screen.getByRole("button", { name: "All age groups" })
    );
    await user.click(
      screen.getByRole("button", { name: /^15-18/ })
    );

    expect(athleteRows()).toHaveLength(1);
    expect(
      screen.getByRole("button", { name: /Liam Chen/ })
    ).toBeInTheDocument();
  });

  it("filters by event, matching event names regardless of case", async () => {
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByRole("button", { name: /Maya Johnson/ });

    await user.click(
      screen.getByRole("button", { name: /^Filters/ })
    );
    await user.click(
      await screen.findByRole("button", { name: "All events" })
    );
    await user.click(
      screen.getByRole("button", { name: /^200M/ })
    );

    expect(athleteRows()).toHaveLength(1);
    expect(
      screen.getByRole("button", { name: /Liam Chen/ })
    ).toBeInTheDocument();
  });

  it("restores the full list when 'Clear Filters' is clicked", async () => {
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByRole("button", { name: /Maya Johnson/ });
    await user.click(
      screen.getByRole("button", { name: /^Filters/ })
    );
    await user.click(
      screen.getByRole("button", { name: "All age groups" })
    );
    await user.click(
      screen.getByRole("button", { name: /^18\+/ })
    );
    expect(athleteRows()).toHaveLength(1);

    await user.click(
      screen.getByRole("button", { name: "Clear Filters" })
    );

    expect(athleteRows()).toHaveLength(3);
  });
});

describe("UC-01 Athlete Directory - pagination", () => {
  beforeEach(() => {
    mockGetAthletes.mockResolvedValue(manyAthletes(12));
  });

  it("shows 10 athletes per page and moves between pages", async () => {
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByText("Runner 1");

    expect(athleteRows()).toHaveLength(10);
    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Previous/ })
    ).toBeDisabled();

    await user.click(
      screen.getByRole("button", { name: /Next/ })
    );

    expect(athleteRows()).toHaveLength(2);
    expect(
      screen.getByRole("button", { name: /Runner 12/ })
    ).toBeInTheDocument();
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Next/ })
    ).toBeDisabled();
  });

  it("returns to page 1 when a search is entered on a later page", async () => {
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByText("Runner 1");
    await user.click(
      screen.getByRole("button", { name: /Next/ })
    );

    await user.type(searchBox(), "Runner");

    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
    expect(athleteRows()).toHaveLength(10);
  });
});

describe("UC-01 Athlete Directory - navigation to a profile", () => {
  it("opens the selected athlete's profile route", async () => {
    mockGetAthletes.mockResolvedValue(directoryAthletes());
    const user = userEvent.setup();
    renderDirectory();

    const row = await screen.findByRole("button", {
      name: /Liam Chen/,
    });
    expect(within(row).getByText("Liam Chen")).toBeInTheDocument();
    await user.click(row);

    expect(
      screen.getByText("Profile route reached")
    ).toBeInTheDocument();
  });
});
