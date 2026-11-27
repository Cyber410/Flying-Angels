import { useId, useState } from "react";
import type { KeyboardEvent } from "react";
import AthletePersonalBests from "./AthletePersonalBests";
import AthleteRankings from "./AthleteRankings";
import "../pages/AthleteProfilePage.css";

type Profile = {
  firstName: string; lastName: string; dateOfBirth: string | null; gender: string | null;
  results: { eventName: string; meetName: string | null; result: string | null; resultLabel: string | null }[];
};
const tabs = [{ id: "overview", label: "Overview" }, { id: "history", label: "Meets & Results" }, { id: "bests", label: "Personal Bests" }, { id: "rankings", label: "Rankings" }] as const;
type Tab = typeof tabs[number]["id"];

// Provide read-only athlete profile sections without exposing staff notes or edit controls.
export default function PortalAthleteProfile({ profile }: { profile: Profile }) {
  const id = useId();
  const [tab, setTab] = useState<Tab>("overview");
  const [meetFilter, setMeetFilter] = useState("");
  const [eventFilter, setEventFilter] = useState("");
  const [recordType, setRecordType] = useState("");
  // Filter only this athlete's supplied results, keeping historical marks separate.
  const results = profile.results.filter(result => (!meetFilter || result.meetName === meetFilter) && (!eventFilter || result.eventName === eventFilter) && (!recordType || (recordType === "historical" ? !result.meetName : !!result.meetName)));
  const meets = [...new Set(profile.results.flatMap(result => result.meetName ? [result.meetName] : []))].sort();
  const events = [...new Set(profile.results.map(result => result.eventName))].sort();

  // Keep the portal tabs navigable with arrows, Home, and End.
  function navigateTabs(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let next: number;
    if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
    else if (event.key === "ArrowLeft") next = (index + tabs.length - 1) % tabs.length;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = tabs.length - 1;
    else return;
    event.preventDefault(); setTab(tabs[next].id); document.getElementById(`${id}-tab-${tabs[next].id}`)?.focus();
  }

  return <section className="profile-panel">
    <div className="profile-tabs" role="tablist" aria-label="My profile sections">{tabs.map(/* Render each item in the list. */ (item, index) => <button key={item.id} role="tab" id={`${id}-tab-${item.id}`} aria-selected={tab === item.id} aria-controls={`${id}-panel-${item.id}`} tabIndex={tab === item.id ? 0 : -1} className={`profile-tab${tab === item.id ? " active" : ""}`} onClick={/* Update tab for the selected action. */ () => setTab(item.id)} onKeyDown={/* Handle keyboard navigation for this control. */ event => navigateTabs(event, index)}>{item.label}</button>)}</div>
    <section role="tabpanel" id={`${id}-panel-overview`} aria-labelledby={`${id}-tab-overview`} hidden={tab !== "overview"} className="pb-content profile-tab-content" tabIndex={0}><h2>Athlete Information</h2><dl className="profile-details"><div className="profile-detail"><dt className="detail-label">Full name</dt><dd className="detail-value">{profile.firstName} {profile.lastName}</dd></div><div className="profile-detail"><dt className="detail-label">Date of birth</dt><dd className="detail-value">{profile.dateOfBirth ?? "Not provided"}</dd></div><div className="profile-detail"><dt className="detail-label">Gender</dt><dd className="detail-value">{profile.gender || "Not provided"}</dd></div></dl></section>
    <section role="tabpanel" id={`${id}-panel-history`} aria-labelledby={`${id}-tab-history`} hidden={tab !== "history"} className="pb-content" tabIndex={0}><h2>Meets &amp; Results</h2>
      <div className="ranking-filters">
        <label>Meet<select value={meetFilter} onChange={/* Select a meet and show competition records. */ event => { setMeetFilter(event.target.value); if (event.target.value) setRecordType("competition"); }}><option value="">All meets</option>{meets.map(name => <option key={name} value={name}>{name}</option>)}</select></label>
        <label>Event<select value={eventFilter} onChange={/* Narrow this athlete's recorded events. */ event => setEventFilter(event.target.value)}><option value="">All events</option>{events.map(name => <option key={name} value={name}>{name}</option>)}</select></label>
        <label>Record type<select value={recordType} onChange={/* Clear the meet when selecting unlinked historical marks. */ event => { setRecordType(event.target.value); if (event.target.value === "historical") setMeetFilter(""); }}><option value="">All records</option><option value="competition">Meet results</option><option value="historical">Historical marks</option></select></label>
        <button className="pb-refresh" onClick={/* Restore the full athlete history. */ () => { setMeetFilter(""); setEventFilter(""); setRecordType(""); }}>Clear filters</button>
      </div>
      <p className="pb-muted">{results.length} matching {results.length === 1 ? "result" : "results"}</p><div className="pb-table-scroll" tabIndex={0} role="region" aria-label="My recorded performances"><table className="pb-table"><caption className="pb-sr-only">My recorded performances</caption><thead><tr><th scope="col">Event</th><th scope="col">Meet</th><th scope="col">Performance</th></tr></thead><tbody>{results.map(/* Render each result in the list. */ (result, index) => <tr key={index}><th scope="row">{result.eventName}</th><td>{result.meetName ?? "Historical mark"}</td><td>{result.result ?? result.resultLabel ?? "Not recorded"}</td></tr>)}</tbody></table>{!results.length && <p className="pb-empty">{profile.results.length ? "No results match these filters." : "No performances have been recorded yet."}</p>}</div></section>
    <section role="tabpanel" id={`${id}-panel-bests`} aria-labelledby={`${id}-tab-bests`} hidden={tab !== "bests"} tabIndex={0}>{tab === "bests" && <AthletePersonalBests portal />}</section>
    <section role="tabpanel" id={`${id}-panel-rankings`} aria-labelledby={`${id}-tab-rankings`} hidden={tab !== "rankings"} tabIndex={0}>{tab === "rankings" && <AthleteRankings />}</section>
  </section>;
}
