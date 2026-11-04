import { expect, it } from "vitest";
import { eventParticipants } from "./eventParticipants";
import { sampleMeetProfile } from "../data/sampleMeet";

it("keeps pending assignments and all attempts while selecting the best score",()=>{
  const event=sampleMeetProfile.events[0];
  const participants=eventParticipants({event,participants:[{participationId:5,athleteId:1,firstName:"Maya",lastName:"Johnson",eventId:event.id,eventName:event.name,meetId:1,meetName:"Open",categoryId:1,categoryName:"100M",note:"Strong start"},{participationId:6,athleteId:2,firstName:"Pending",lastName:"Runner",eventId:event.id,eventName:event.name,meetId:1,meetName:"Open",categoryId:1,categoryName:"100M",note:""}],results:[
    {resultId:10,athleteId:1,firstName:"Maya",lastName:"Johnson",categoryId:1,categoryName:"100M",result:"13.4s",score:13.4},
    {resultId:11,athleteId:1,firstName:"Maya",lastName:"Johnson",categoryId:1,categoryName:"100M",result:"13.1s",score:13.1},
  ]});
  expect(participants).toHaveLength(2);expect(participants[0].attempts).toHaveLength(2);
  expect(participants[0].resultId).toBe(11);expect(participants[0].note).toBe("Strong start");
  expect(participants[1].participationId).toBe(6);expect(participants[1].resultId).toBeNull();
});
