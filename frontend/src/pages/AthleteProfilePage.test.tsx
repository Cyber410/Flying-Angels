import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AthleteProfilePage from "./AthleteProfilePage";
import { getAthleteById } from "../services/athleteService";
import {
  buildAthlete,
  buildEvent,
} from "../test/athleteTestData";

/**
 * UC-02 Athlete Profile Retrieval and Navigation - component tests.
 *
 * The service module is mocked. Each test opens the page at a URL such as
 * /athletes/7, the same way the router does in the real app.
 */

vi.mock("../services/athleteService");

const mockGetAthleteById = vi.mocked(getAthleteById);

function renderProfile(athleteId: string) {
  return render(
    <MemoryRouter initialEntries={[`/athletes/${athleteId}`]}>
      <Routes>
        <Route
          path="/"
          element={<p>Directory route reached</p>}
        />
        <Route
          path="/athletes/:athleteId"
          element={<AthleteProfilePage />}
        />
      </Routes>
    </MemoryRouter>
  );
}

/** Finds the value displayed next to a field label such as "Gender". */
function fieldValue(label: string) {
  return screen.getByText(label).nextElementSibling;
}

beforeEach(() => {
  // The page logs caught errors; keep the test output readable.
  vi.spyOn(console, "error").mockImplementation(() => {});
});

describe("UC-02 Athlete Profile - retrieval", () => {
  it("shows a loading message while the profile is being retrieved", () => {
    mockGetAthleteById.mockReturnValue(new Promise(() => {}));

    renderProfile("7");

    expect(
      screen.getByRole("heading", { name: "Loading athlete..." })
    ).toBeInTheDocument();
  });

  it("requests the athlete from the URL and shows their details", async () => {
    mockGetAthleteById.mockResolvedValue(
      buildAthlete({
        id: 7,
        firstName: "Maya",
        lastName: "Johnson",
        dateOfBirth: "2010-03-15",
        gender: "Female",
        flyStatus: "Active",
        flyaStatus: "Inactive",
        note: "Sprinter",
      })
    );

    renderProfile("7");

    expect(
      await screen.findByRole("heading", { name: "Maya Johnson" })
    ).toBeInTheDocument();
    expect(mockGetAthleteById).toHaveBeenCalledWith(7);
    expect(fieldValue("First Name")).toHaveTextContent("Maya");
    expect(fieldValue("Last Name")).toHaveTextContent("Johnson");
    expect(fieldValue("Athlete ID")).toHaveTextContent("7");
    expect(fieldValue("Date of Birth")).toHaveTextContent(
      "March 15, 2010"
    );
    expect(fieldValue("Gender")).toHaveTextContent("Female");
    expect(fieldValue("FLY Status")).toHaveTextContent("Active");
    expect(fieldValue("FLYA Status")).toHaveTextContent("Inactive");
    expect(fieldValue("Note")).toHaveTextContent("Sprinter");
  });

  it("shows each event with its score", async () => {
    mockGetAthleteById.mockResolvedValue(
      buildAthlete({
        id: 7,
        events: [
          buildEvent({
            resultId: 1,
            categoryName: "100M",
            score: 850,
          }),
          buildEvent({
            resultId: 2,
            categoryName: "LONG JUMP",
            score: 640,
          }),
        ],
      })
    );

    renderProfile("7");

    expect(await screen.findByText("100M")).toBeInTheDocument();
    expect(screen.getByText("850")).toBeInTheDocument();
    expect(screen.getByText("LONG JUMP")).toBeInTheDocument();
    expect(screen.getByText("640")).toBeInTheDocument();
  });
});

describe("UC-02 Athlete Profile - missing data", () => {
  it("shows 'Not provided' for optional fields that are empty", async () => {
    mockGetAthleteById.mockResolvedValue(
      buildAthlete({
        id: 7,
        dateOfBirth: null,
        gender: null,
        flyStatus: "",
        flyaStatus: "   ",
        note: null,
      })
    );

    renderProfile("7");

    await screen.findByRole("heading", { name: "Maya Johnson" });
    for (const label of [
      "Date of Birth",
      "Age",
      "Gender",
      "FLY Status",
      "FLYA Status",
      "Note",
    ]) {
      expect(fieldValue(label)).toHaveTextContent("Not provided");
    }
  });

  it("shows a message when the athlete has no event results", async () => {
    mockGetAthleteById.mockResolvedValue(
      buildAthlete({ id: 7, events: [] })
    );

    renderProfile("7");

    expect(
      await screen.findByText(
        "No event results are available for this athlete."
      )
    ).toBeInTheDocument();
  });
});

describe("UC-02 Athlete Profile - errors", () => {
  it("shows 'Athlete not found' when the profile cannot be loaded", async () => {
    mockGetAthleteById.mockRejectedValue(
      new Error("Unable to load athlete.")
    );

    renderProfile("999");

    expect(
      await screen.findByRole("heading", {
        name: "Athlete not found",
      })
    ).toBeInTheDocument();
    expect(
      screen.getByText("Unable to load athlete profile.")
    ).toBeInTheDocument();
  });

  it("rejects a non-numeric ID without calling the API", async () => {
    renderProfile("abc");

    expect(
      await screen.findByText("Invalid athlete ID.")
    ).toBeInTheDocument();
    expect(mockGetAthleteById).not.toHaveBeenCalled();
  });
});

describe("UC-02 Athlete Profile - navigation", () => {
  it("returns to the directory from a loaded profile", async () => {
    mockGetAthleteById.mockResolvedValue(buildAthlete({ id: 7 }));
    const user = userEvent.setup();
    renderProfile("7");
    await screen.findByRole("heading", { name: "Maya Johnson" });

    await user.click(
      screen.getByRole("button", {
        name: /Back to Athlete Directory/,
      })
    );

    expect(
      screen.getByText("Directory route reached")
    ).toBeInTheDocument();
  });

  it("returns to the directory from the 'not found' screen", async () => {
    mockGetAthleteById.mockRejectedValue(new Error("404"));
    const user = userEvent.setup();
    renderProfile("999");
    await screen.findByRole("heading", {
      name: "Athlete not found",
    });

    await user.click(
      screen.getByRole("button", {
        name: /Back to Athlete Directory/,
      })
    );

    expect(
      screen.getByText("Directory route reached")
    ).toBeInTheDocument();
  });
});
