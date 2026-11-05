import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import AssignAthletesDialog from "./AssignAthletesDialog";
import { getAthletes } from "../services/athleteService";
import { assignEventAthletes } from "../services/eventService";
import { buildAthlete } from "../test/athleteTestData";
import { sampleMeetProfile } from "../data/sampleMeet";

vi.mock("../services/athleteService");vi.mock("../services/eventService");
beforeEach(()=>vi.resetAllMocks());
it("persists selected athletes before reporting a successful assignment",async()=>{
  const user=userEvent.setup();vi.mocked(getAthletes).mockResolvedValue([buildAthlete()]);
  vi.mocked(assignEventAthletes).mockResolvedValue([{participationId:1,athleteId:1,firstName:"Maya",lastName:"Johnson",eventId:1,eventName:"100M",meetId:1,meetName:"Open",categoryId:1,categoryName:"100M",note:""}]);
  const assigned=vi.fn();render(<AssignAthletesDialog event={{...sampleMeetProfile.events[0],id:1}} assignedIds={[]} onAssigned={assigned} onClose={vi.fn()} />);
  await user.click(await screen.findByRole("checkbox"));await user.click(screen.getByRole("button",{name:"Assign Selected (1)"}));
  await waitFor(()=>expect(assigned).toHaveBeenCalledWith([expect.objectContaining({participationId:1,result:null})]));
  expect(assignEventAthletes).toHaveBeenCalledWith(1,[1]);
});
