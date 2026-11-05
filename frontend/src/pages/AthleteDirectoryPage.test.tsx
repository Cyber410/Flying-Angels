import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AthleteDirectoryPage from "./AthleteDirectoryPage";
import { getAthleteById, getAthletes, setAthletesArchived } from "../services/athleteService";
import { buildAthlete, buildEvent, directoryAthletes, manyAthletes } from "../test/athleteTestData";

vi.mock("../services/athleteService");

// Mock services keep directory/archive checks independent of the live database.
beforeEach(() => {
  vi.resetAllMocks();
  vi.spyOn(console, "error").mockImplementation(() => {});
  vi.mocked(getAthletes).mockResolvedValue(directoryAthletes());
});
function renderPage() {
  return render(<MemoryRouter><Routes><Route path="/" element={<AthleteDirectoryPage />} /><Route path="/athletes/:id" element={<p>Profile opened</p>} /></Routes></MemoryRouter>);
}
async function loaded() { renderPage(); await screen.findByRole("link", { name: "Maya Johnson" }); }
function names() { return screen.getAllByRole("rowheader").map(cell => cell.textContent); }

describe("Athlete Directory", () => {
  it("shows loading and then the directory table", async () => {
    renderPage(); expect(screen.getByText("Loading athletes...")).toBeInTheDocument();
    await screen.findByRole("link", { name: "Maya Johnson" });
    expect(names()).toEqual(["Maya Johnson", "Liam Chen", "Sofia Martinez"]);
    expect(getAthletes).toHaveBeenCalledWith("all");
  });
  it("searches case-insensitively and clears filters", async () => {
    const user = userEvent.setup(); await loaded();
    await user.type(screen.getByRole("textbox", { name: "Search athletes by name" }), "  CHEN ");
    expect(names()).toEqual(["Liam Chen"]);
    await user.click(screen.getByRole("button", { name: "Clear" }));
    expect(names()).toHaveLength(3);
  });
  it("filters by age and event", async () => {
    const user = userEvent.setup();
    vi.mocked(getAthleteById).mockImplementation(async id => buildAthlete({id, events: [buildEvent({categoryName: id === 2 ? "200M" : "100M"})]}));
    await loaded();
    await user.click(screen.getByRole("button", {name: "All age groups"}));
    await user.click(screen.getByRole("button", {name: "15-18"}));
    expect(names()).toEqual(["Liam Chen"]);
    await user.click(screen.getByRole("button", {name: "All events"}));
    await user.click(await screen.findByRole("button", {name: "200M"}));
    expect(names()).toEqual(["Liam Chen"]);
  });
  it("shows empty and error states", async () => {
    vi.mocked(getAthletes).mockRejectedValue(new Error("offline")); renderPage();
    expect(await screen.findByText("Unable to load athletes.")).toBeInTheDocument();
  });
  it("opens a profile from its name", async () => {
    const user=userEvent.setup(); await loaded(); await user.click(screen.getByRole("link", {name:"Maya Johnson"}));
    expect(screen.getByText("Profile opened")).toBeInTheDocument();
  });
  it("opens the create-athlete form", async () => {
    const user=userEvent.setup(); await loaded(); await user.click(screen.getByRole("button", {name:"+ Add Athlete"}));
    expect(screen.getByRole("heading", {name:"Add Athlete"})).toBeInTheDocument();
  });
});

describe("Athlete archive", () => {
  it("separates active and archived athletes", async () => {
    const user=userEvent.setup(); vi.mocked(getAthletes).mockResolvedValue([...directoryAthletes(),buildAthlete({id:9,firstName:"Archived",lastName:"Runner",archived:true})]);
    await loaded(); expect(names()).toHaveLength(3);
    await user.click(screen.getByRole("button", {name:/Archived athletes/}));
    expect(names()).toEqual(["Archived Runner"]);
    expect(screen.getByRole("button", {name:"Restore selected"})).toBeDisabled();
  });
  it("keeps selection across pages and archives only after confirmation", async () => {
    const user=userEvent.setup(); const athletes=manyAthletes(12); vi.mocked(getAthletes).mockResolvedValue(athletes);
    vi.mocked(setAthletesArchived).mockResolvedValue(athletes.slice(0,10).map(athlete=>({...athlete,archived:true})));
    renderPage(); await screen.findByRole("link",{name:"Runner 1"});
    await user.click(screen.getByRole("checkbox",{name:"Select page"}));
    await user.click(screen.getByRole("button",{name:"Next page"}));
    expect(screen.getByText("10 selected across the directory")).toBeInTheDocument();
    await user.click(screen.getByRole("button",{name:"Archive selected"}));
    const dialog=screen.getByRole("dialog",{name:"Archive selected athletes?"});
    expect(within(dialog).getAllByRole("listitem")).toHaveLength(10);
    expect(setAthletesArchived).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button",{name:"Archive 10 athletes"}));
    await waitFor(()=>expect(names()).toHaveLength(2));
    expect(setAthletesArchived).toHaveBeenCalledWith(athletes.slice(0,10).map(athlete=>athlete.id),true);
  });
  it("can cancel without changing records", async () => {
    const user=userEvent.setup(); await loaded(); await user.click(screen.getByRole("checkbox",{name:"Select Maya Johnson"}));
    await user.click(screen.getByRole("button",{name:"Archive selected"})); await user.click(screen.getByRole("button",{name:"Cancel"}));
    expect(setAthletesArchived).not.toHaveBeenCalled(); expect(names()).toHaveLength(3);
  });
  it("restores an archived athlete", async () => {
    const user=userEvent.setup(); const archived=buildAthlete({archived:true}); vi.mocked(getAthletes).mockResolvedValue([archived]);
    vi.mocked(setAthletesArchived).mockResolvedValue([{...archived,archived:false}]); renderPage();
    await screen.findByText("No athletes found."); await user.click(screen.getByRole("button",{name:/Archived athletes/}));
    await user.click(screen.getByRole("checkbox",{name:"Select Maya Johnson"})); await user.click(screen.getByRole("button",{name:"Restore selected"}));
    await user.click(screen.getByRole("button",{name:"Restore 1 athlete"}));
    expect(await screen.findByText("No archived athletes.")).toBeInTheDocument();
    expect(setAthletesArchived).toHaveBeenCalledWith([1],false);
    await user.click(screen.getByRole("button",{name:/Active athletes/})); expect(names()).toEqual(["Maya Johnson"]);
  });
  it("keeps the selection and displays failures", async () => {
    const user=userEvent.setup(); vi.mocked(setAthletesArchived).mockRejectedValue(new Error("Please retry")); await loaded();
    await user.click(screen.getByRole("checkbox",{name:"Select Maya Johnson"})); await user.click(screen.getByRole("button",{name:"Archive selected"}));
    await user.click(screen.getByRole("button",{name:"Archive 1 athlete"}));
    expect(await screen.findByRole("alert")).toHaveTextContent("Please retry"); expect(names()).toHaveLength(3);
  });
});
