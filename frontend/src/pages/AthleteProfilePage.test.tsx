import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AthleteProfilePage from "./AthleteProfilePage";
import { getAthleteById, updateAthlete } from "../services/athleteService";
import { getAthleteMeetHistory } from "../services/athleteHistoryService";
import { buildAthlete, buildEvent } from "../test/athleteTestData";

vi.mock("../services/athleteService");
vi.mock("../services/athleteHistoryService");
beforeEach(()=>{
  vi.resetAllMocks(); vi.spyOn(console,"error").mockImplementation(()=>{});
  vi.mocked(getAthleteById).mockResolvedValue(buildAthlete());
  vi.mocked(getAthleteMeetHistory).mockResolvedValue({meets:[],warnings:[],failedEvents:[]});
});
function renderPage(id="1") {return render(<MemoryRouter initialEntries={[`/athletes/${id}`]}><Routes><Route path="/athletes/:athleteId" element={<AthleteProfilePage />} /><Route path="/" element={<p>Directory opened</p>} /></Routes></MemoryRouter>);}

describe("Athlete Profile",()=>{
  it("loads details and separates notes from the overview",async()=>{
    const user=userEvent.setup(); renderPage(); await screen.findByRole("heading",{name:"Maya Johnson"});
    expect(screen.getByText("Date of Birth").nextElementSibling).toHaveTextContent("March 15, 2010");
    expect(screen.getByRole("tab",{name:"Overview"})).toHaveAttribute("aria-selected","true");
    await user.click(screen.getByRole("tab",{name:"Notes"}));
    expect(screen.getByRole("heading",{name:"Athlete note"})).toBeInTheDocument();
    expect(screen.getByRole("heading",{name:"Performance notes"})).toBeInTheDocument();
    expect(screen.getByText("Sprinter")).toBeVisible();
  });
  it("keeps historical seed results without inventing a meet",async()=>{
    const user=userEvent.setup(); vi.mocked(getAthleteById).mockResolvedValue(buildAthlete({events:[buildEvent()]}));
    renderPage(); await screen.findByRole("heading",{name:"Maya Johnson"}); await user.click(screen.getByRole("tab",{name:"Meets & Results"}));
    const olderResults=screen.getAllByText(/Other recorded results/).find(element=>element.tagName === "SUMMARY")!;
    await user.click(olderResults);
    expect(screen.getByRole("rowheader",{name:"100M"})).toBeVisible();
    expect(screen.getByText("12.45")).toBeVisible();
  });
  it("edits the athlete note through the existing save flow",async()=>{
    const user=userEvent.setup(); const changed=buildAthlete({note:"New coaching note"});
    vi.mocked(updateAthlete).mockResolvedValue(changed); renderPage(); await screen.findByRole("heading",{name:"Maya Johnson"});
    await user.click(screen.getByRole("tab",{name:"Notes"})); await user.click(screen.getByRole("button",{name:"Edit general athlete note"}));
    await user.clear(screen.getByRole("textbox",{name:"Athlete note"})); await user.type(screen.getByRole("textbox",{name:"Athlete note"}),"New coaching note");
    await user.click(screen.getByRole("button",{name:"Save Changes"}));
    expect(updateAthlete).toHaveBeenCalledWith(1,expect.objectContaining({note:"New coaching note"}));
  });
  it("identifies archived profiles and preserves navigation",async()=>{
    const user=userEvent.setup(); vi.mocked(getAthleteById).mockResolvedValue(buildAthlete({archived:true})); renderPage();
    expect(await screen.findByText(/Archived athlete/)).toBeInTheDocument();
    await user.click(screen.getByRole("button",{name:/Back to Athlete Directory/})); expect(screen.getByText("Directory opened")).toBeInTheDocument();
  });
  it("reports an invalid ID without requesting a profile",async()=>{
    renderPage("abc"); expect(await screen.findByText("Invalid athlete ID.")).toBeInTheDocument(); expect(getAthleteById).not.toHaveBeenCalled();
  });
  it("reports a failed profile load",async()=>{
    vi.mocked(getAthleteById).mockRejectedValue(new Error("offline")); renderPage();
    expect(await screen.findByRole("heading",{name:"Athlete not found"})).toBeInTheDocument();
  });
  it("confirms before deleting",async()=>{
    const user=userEvent.setup(); renderPage(); await screen.findByRole("heading",{name:"Maya Johnson"});
    await user.click(screen.getByText("Manage athlete")); await user.click(screen.getByRole("button",{name:"Delete Athlete"}));
    expect(screen.getByRole("heading",{name:"Delete Athlete?"})).toBeInTheDocument();
    await user.click(screen.getByRole("button",{name:"Cancel"}));
    expect(within(screen.getByRole("tabpanel",{name:"Overview"})).getByText("Manage athlete")).toBeVisible();
  });
});
