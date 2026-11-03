import { describe, expect, it } from "vitest";
import { bestResultAttempt, convertResultEntry, eventResultUnit, parseEventResult, parseResultEntry, resultEntryForAttempt } from "./eventResult";
import { sampleMeetProfile } from "../data/sampleMeet";

const track=sampleMeetProfile.events[0];
const field=sampleMeetProfile.events[2];

// Check user-entered units, canonical scores, invalid input, and best-attempt ranking.
describe("result entry",()=>{
  it("suggests units from the discipline",()=>{expect(eventResultUnit(track)).toBe("s");expect(eventResultUnit(field)).toBe("m");});
  it("normalizes times and distances",()=>{
    expect(parseEventResult("1:30",track)?.score).toBe(90);
    expect(parseEventResult("1.5min",track)?.score).toBe(90);
    expect(parseEventResult("450cm",field)?.score).toBe(4.5);
  });
  it("converts units without changing the physical result",()=>{
    const entry={...resultEntryForAttempt(track),value:"90"};
    const converted=convertResultEntry(entry,"min",track);
    expect(converted.value).toBe("1.5");expect(parseResultEntry(converted,track)?.score).toBe(90);
  });
  it("rejects negative, nonnumeric, and invalid clock values",()=>{
    expect(parseEventResult("-12",track)).toBeNull();expect(parseEventResult("DNF",track)).toBeNull();expect(parseEventResult("1:75",track)).toBeNull();
  });
  it("uses the correct best value for timed and field events",()=>{
    const attempts=[{id:"a",result:"12",score:12},{id:"b",result:"13",score:13}];
    expect(bestResultAttempt(attempts,"ASC")?.id).toBe("a");expect(bestResultAttempt(attempts,"DESC")?.id).toBe("b");
  });
});
