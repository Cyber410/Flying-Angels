// Read the retired browser stores only for explicit, retry-safe migration.
import type { EventParticipant, MeetEvent, ResultAttempt } from "../types/event";
import { getEvents, readEventNote, migrateEventNote } from "./eventService";
import { getAthletes } from "./athleteService";
import { getBrowserAssignments } from "./eventAssignmentStore";
import { getResultAttempts } from "./eventAttemptStore";
import { checkResponse } from "./apiResponse";

export type LegacyEntry = {
  id: string; eventId: number; athleteId?: number; label: string; note?: string;
  attempts: ResultAttempt[]; pending: boolean; error?: string; snapshots: Record<string,string>;
};
const legacyPattern = /^flying-angels:(pending-event-assignments:|event-attempts:|event-note:live:)/;

export function legacySnapshot(): Record<string,string> {
  const data: Record<string,string> = {};
  for (let index=0; index<localStorage.length; index++) {
    const key=localStorage.key(index);
    if (key && legacyPattern.test(key)) data[key]=localStorage.getItem(key) ?? "";
  }
  return data;
}

export function hasLegacyBrowserData(): boolean {
  try { return Object.keys(legacySnapshot()).length > 0; } catch { return false; }
}

export async function reviewLegacyBrowserData(): Promise<LegacyEntry[]> {
  const snapshots=legacySnapshot();
  const [events,athletes]=await Promise.all([getEvents(),getAthletes("all")]);
  const eventById=new Map(events.map(event=>[event.id,event]));
  const athleteById=new Map(athletes.map(athlete=>[athlete.id,athlete]));
  const entries=new Map<string,LegacyEntry>();
  function entryFor(eventId:number,athleteId?:number) {
    const id=`${eventId}:${athleteId ?? "event"}`;
    let entry=entries.get(id);
    if (!entry) {
      const event=eventById.get(eventId), athlete=athleteId === undefined ? undefined : athleteById.get(athleteId);
      entry={id,eventId,athleteId,label:`${event?.meetName ?? "Missing meet"} · ${event?.name ?? `Event #${eventId}`}${athleteId === undefined ? " · Event note" : ` · ${athlete ? `${athlete.firstName} ${athlete.lastName}` : `Missing athlete #${athleteId}`}`}`,attempts:[],pending:false,snapshots:{}};
      if (!event || (athleteId !== undefined && !athlete)) entry.error="The original event or athlete no longer exists. The saved record is retained; download it for review.";
      entries.set(id,entry);
    }
    return entry;
  }
  for (const [key,raw] of Object.entries(snapshots)) {
    const pending=key.match(/^flying-angels:pending-event-assignments:(\d+)$/);
    const attempt=key.match(/^flying-angels:event-attempts:(\d+):(\d+)(?::staged)?$/);
    const note=key.match(/^flying-angels:event-note:live:(\d+):(\d+|event)$/);
    if (pending) {
      const eventId=Number(pending[1]);
      try {
        const event=eventById.get(eventId) ?? {id:eventId,category:{id:0,name:"",rankingOrder:"ASC"}} as MeetEvent;
        for (const participant of getBrowserAssignments(event)) {
          const entry=entryFor(eventId,participant.athleteId);entry.pending=true;entry.snapshots[key]=raw;
        }
      } catch { const entry=entryFor(eventId);entry.error="The saved assignments could not be read. Download the records for review.";entry.snapshots[key]=raw; }
    } else if (attempt) {
      const entry=entryFor(Number(attempt[1]),Number(attempt[2]));entry.snapshots[key]=raw;
    } else if (note) {
      const entry=entryFor(Number(note[1]),note[2] === "event" ? undefined : Number(note[2]));
      entry.note=raw;entry.snapshots[key]=raw;
      if (raw.length>2000) entry.error="This note exceeds 2,000 characters. Export it and shorten it before saving manually.";
    }
  }
  for (const entry of entries.values()) if (entry.athleteId !== undefined) {
    const participant: EventParticipant={athleteId:entry.athleteId,firstName:"",lastName:"",categoryId:0,categoryName:"",resultId:null,result:null,score:null};
    try { entry.attempts=getResultAttempts(entry.eventId,participant); }
    catch { entry.error="The saved attempts could not be read. Download the records for review."; }
  }
  return [...entries.values()];
}

export async function migrateLegacyEntry(entry:LegacyEntry):Promise<void> {
  if (entry.error) throw new Error(entry.error);
  if (entry.athleteId === undefined) {
    const existing=await readEventNote(entry.eventId);
    if (existing && existing !== entry.note?.trim()) throw new Error("A different shared event note already exists. The saved note is preserved for manual review.");
    if (entry.note?.trim()) await migrateEventNote(entry.eventId,entry.note.trim());
  } else {
    const attempts=await Promise.all(entry.attempts.map(async attempt=>{
      const bytes=new TextEncoder().encode(`legacy:${entry.eventId}:${entry.athleteId}:${attempt.id}`);
      const digest=await crypto.subtle.digest("SHA-256",bytes);
      const clientId="legacy:"+[...new Uint8Array(digest)].map(value=>value.toString(16).padStart(2,"0")).join("");
      return {clientId,result:attempt.result,score:attempt.score};
    }));
    const response=await fetch(`/api/events/${entry.eventId}/participants/${entry.athleteId}/migrate-browser-data`,{
      method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({note:entry.note?.trim() || null,attempts}),
    });
    await checkResponse(response,"The saved records could not be added. They have been retained for retry.");
  }
  // Shared pending-assignment keys can contain multiple athletes; clear only this one.
  for (const [key,raw] of Object.entries(entry.snapshots)) {
    if (key.includes("pending-event-assignments:")) {
      const currentRaw=localStorage.getItem(key);
      if (!currentRaw) continue;
      const current: {version:number;athletes:{id:number}[]}=JSON.parse(currentRaw);
      current.athletes=current.athletes.filter(athlete=>athlete.id!==entry.athleteId);
      if (current.athletes.length) localStorage.setItem(key,JSON.stringify(current));else localStorage.removeItem(key);
    } else if (localStorage.getItem(key) === raw) localStorage.removeItem(key);
  }
}
