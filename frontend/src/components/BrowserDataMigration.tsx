// Offer migration without treating local data as the current source of truth.
import { useState } from "react";
import { hasLegacyBrowserData, legacySnapshot, migrateLegacyEntry, reviewLegacyBrowserData } from "../services/browserDataMigration";
import type { LegacyEntry } from "../services/browserDataMigration";
import MeetDialog from "./MeetDialog";
import "./BrowserDataMigration.css";

export default function BrowserDataMigration() {
  const [available,setAvailable]=useState(hasLegacyBrowserData);
  const [open,setOpen]=useState(false);
  const [loading,setLoading]=useState(false);
  const [entries,setEntries]=useState<LegacyEntry[]>([]);
  const [selected,setSelected]=useState<string[]>([]);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  const [messages,setMessages]=useState<string[]>([]);
  const [moved,setMoved]=useState(0);
  if (!available) return null;

  async function review() {
    setOpen(true);setLoading(true);setError("");setMessages([]);
    try { const rows=await reviewLegacyBrowserData();setEntries(rows);setSelected(rows.filter(row=>!row.error).map(row=>row.id)); }
    catch(failure){setError(failure instanceof Error?failure.message:"Unable to read saved records.");}
    finally{setLoading(false);}
  }
  async function move() {
    if(busy)return;setBusy(true);setError("");const failures:string[]=[];const successes:string[]=[];
    for(const entry of entries.filter(row=>selected.includes(row.id))) {
      try{await migrateLegacyEntry(entry);successes.push(entry.id);}catch(failure){failures.push(`${entry.label}: ${failure instanceof Error?failure.message:"Migration failed."}`);}
    }
    setEntries(current=>current.filter(row=>!successes.includes(row.id)));setSelected(current=>current.filter(id=>!successes.includes(id)));
    setMoved(current=>current+successes.length);setMessages(failures);setBusy(false);
  }
  function exportData() {
    try {
      const url=URL.createObjectURL(new Blob([JSON.stringify(legacySnapshot(),null,2)],{type:"application/json"}));
      const link=document.createElement("a");link.href=url;link.download="flying-angels-saved-records.json";link.click();URL.revokeObjectURL(url);
    } catch {setError("Saved records could not be exported.");}
  }
  return <>
    <aside className="browser-migration-banner"><p>Saved records from an earlier version are available for review.</p><button type="button" onClick={review}>Review saved records</button></aside>
    {open && <MeetDialog title="Recover saved records" busy={busy} onClose={()=>{setOpen(false);setAvailable(hasLegacyBrowserData());}}>
      <div className="meet-form"><p className="meet-dialog-message">Select the saved records to add to the academy. Existing notes are protected, and any records that cannot be added remain available for review.</p>
        <button className="meets-button" type="button" onClick={exportData} disabled={busy}>Download saved records</button>
        {loading?<p role="status">Reviewing saved records…</p>:<fieldset className="meet-choice-list" disabled={busy}><legend className="meets-sr-only">Saved records</legend>{entries.map(entry=><label className="meet-choice" key={entry.id}><input type="checkbox" disabled={Boolean(entry.error)} checked={selected.includes(entry.id)} onChange={event=>setSelected(current=>event.target.checked?[...current,entry.id]:current.filter(id=>id!==entry.id))}/><span>{entry.label}<small>{entry.attempts.length} attempts{entry.pending?" · pending assignment":""}{entry.note?" · note":""}</small>{entry.error&&<small>{entry.error}</small>}</span></label>)}{!entries.length&&<p>No saved records remain to review.</p>}</fieldset>}
        {error&&<p className="meet-dialog-error" role="alert">{error}</p>}{messages.length>0&&<div className="meet-dialog-error" role="alert">{messages.map(message=><p key={message}>{message}</p>)}</div>}
        {moved>0&&<p role="status">{moved} records moved successfully. Refresh the page to load the shared data.</p>}
        <div className="meet-dialog-actions"><button className="meets-button" type="button" disabled={busy} onClick={()=>{setOpen(false);setAvailable(hasLegacyBrowserData());}}>Close</button>{moved>0&&<button className="meets-button" type="button" disabled={busy} onClick={()=>window.location.reload()}>Refresh page</button>}<button className="meets-button meets-button-primary" type="button" disabled={busy||loading||!selected.length} onClick={move}>{busy?"Moving…":`Move selected (${selected.length})`}</button></div>
      </div>
    </MeetDialog>}
  </>;
}
