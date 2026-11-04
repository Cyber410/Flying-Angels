import { afterEach, expect, it, vi } from "vitest";
import { migrateLegacyEntry } from "./browserDataMigration";
import { readEventNote, migrateEventNote } from "./eventService";

vi.mock("./eventService");afterEach(()=>{localStorage.clear();vi.resetAllMocks();});
it("preserves local notes when a different shared note exists",async()=>{
  const key="flying-angels:event-note:live:1:event";localStorage.setItem(key,"Local feedback");
  vi.mocked(readEventNote).mockResolvedValue("Team feedback");
  await expect(migrateLegacyEntry({id:"1:event",eventId:1,label:"Event",note:"Local feedback",pending:false,attempts:[],snapshots:{[key]:"Local feedback"}})).rejects.toThrow("different shared event note");
  expect(localStorage.getItem(key)).toBe("Local feedback");expect(migrateEventNote).not.toHaveBeenCalled();
});
it("clears a migrated note only after the shared save succeeds",async()=>{
  const key="flying-angels:event-note:live:1:event";localStorage.setItem(key,"Local feedback");
  vi.mocked(readEventNote).mockResolvedValue("");vi.mocked(migrateEventNote).mockResolvedValue(undefined);
  await migrateLegacyEntry({id:"1:event",eventId:1,label:"Event",note:"Local feedback",pending:false,attempts:[],snapshots:{[key]:"Local feedback"}});
  expect(migrateEventNote).toHaveBeenCalledWith(1,"Local feedback");expect(localStorage.getItem(key)).toBeNull();
});
