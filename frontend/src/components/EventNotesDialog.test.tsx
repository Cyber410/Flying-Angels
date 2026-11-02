import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import EventNotesDialog from "./EventNotesDialog";
import { readEventNote, writeEventNote } from "../services/eventService";
import { sampleMeetProfile } from "../data/sampleMeet";

vi.mock("../services/eventService");
beforeEach(()=>vi.resetAllMocks());
it("loads and saves notes through the API without using browser storage",async()=>{
  const user=userEvent.setup();vi.mocked(readEventNote).mockResolvedValue("Existing note");vi.mocked(writeEventNote).mockResolvedValue("Updated note");
  const saved=vi.fn();const localWrite=vi.spyOn(Storage.prototype,"setItem");
  render(<EventNotesDialog event={{...sampleMeetProfile.events[0],id:1}} sample={false} onSaved={saved} onClose={vi.fn()} />);
  const input=await screen.findByDisplayValue("Existing note");await user.clear(input);await user.type(input,"Updated note");
  await user.click(screen.getByRole("button",{name:"Save Note"}));
  await waitFor(()=>expect(saved).toHaveBeenCalledWith("Updated note"));
  expect(writeEventNote).toHaveBeenCalledWith(1,"Updated note",undefined);expect(localWrite).not.toHaveBeenCalled();
});
it("does not overwrite a note when its initial load fails",async()=>{
  vi.mocked(readEventNote).mockRejectedValue(new Error("offline"));
  render(<EventNotesDialog event={sampleMeetProfile.events[0]} sample={false} onSaved={vi.fn()} onClose={vi.fn()} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("offline");expect(screen.getByRole("button",{name:"Save Note"})).toBeDisabled();
});
