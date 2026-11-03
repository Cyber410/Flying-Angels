import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import ResultsPage from "./ResultsPage";
import { getResultsOverview } from "../services/resultsService";
import { sampleMeetProfile } from "../data/sampleMeet";
import type { ResultsOverview } from "../types/results";

vi.mock("../services/resultsService");
const data: ResultsOverview={events:sampleMeetProfile.events,sharedResults:[{
  id:"result-1",athleteId:1,firstName:"Maya",lastName:"Johnson",gender:"Female",ageGroup:"15-18",
  eventId:-11,eventName:"100M",categoryId:-11,categoryName:"100M",meetId:-1,meetName:"Fall Invitational",
  eventDate:"2026-10-24",result:"13.2s",score:13.2,source:"shared",
}],allAttempts:[],warnings:[],failedEvents:[]};
beforeEach(()=>{vi.resetAllMocks();vi.mocked(getResultsOverview).mockResolvedValue(data);});
it("shows meet-linked results and filters by athlete name",async()=>{
  const user=userEvent.setup();render(<MemoryRouter><ResultsPage /></MemoryRouter>);
  expect(await screen.findByText("13.2s")).toBeInTheDocument();
  await user.type(screen.getByRole("searchbox"),"not present");expect(screen.queryByText("13.2s")).not.toBeInTheDocument();
  await user.click(screen.getAllByRole("button",{name:"Clear Filters"})[0]);expect(screen.getByText("13.2s")).toBeInTheDocument();
});
it("reports a failed result load",async()=>{
  vi.mocked(getResultsOverview).mockRejectedValue(new Error("Results unavailable"));render(<MemoryRouter><ResultsPage /></MemoryRouter>);
  expect(await screen.findByRole("alert")).toHaveTextContent("Results unavailable");
});
