import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import EventResultDialog from "./EventResultDialog";
import { createParticipantResult, updateEventAttempt } from "../services/eventService";
import { sampleMeetProfile } from "../data/sampleMeet";
import type { EventResult } from "../types/event";

vi.mock("../services/eventService");beforeEach(()=>vi.resetAllMocks());
const participant:EventResult={resultId:1,athleteId:1,firstName:"Maya",lastName:"Johnson",categoryId:1,categoryName:"100M",result:"13s",score:13};
it("edits the explicit result ID without appending a new attempt",async()=>{
  const user=userEvent.setup();const saved=vi.fn();vi.mocked(updateEventAttempt).mockResolvedValue({...participant,result:"12.8s",score:12.8});
  render(<EventResultDialog event={{...sampleMeetProfile.events[0],id:1}} participant={participant} attempts={[{id:"1",result:"13s",score:13}]} editingAttempt={{id:"1",result:"13s",score:13}} onSaved={saved} onClose={vi.fn()} />);
  await user.clear(screen.getByLabelText("Time"));await user.type(screen.getByLabelText("Time"),"12.8");await user.click(screen.getByRole("button",{name:"Save Result"}));
  await waitFor(()=>expect(updateEventAttempt).toHaveBeenCalledWith(1,1,1,"12.8s",12.8));
  expect(createParticipantResult).not.toHaveBeenCalled();expect(saved).toHaveBeenCalledWith(expect.objectContaining({score:12.8}),[expect.objectContaining({id:"1",score:12.8})]);
});
