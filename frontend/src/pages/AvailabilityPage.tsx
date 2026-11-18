import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Header from "../components/header";
import MeetDialog from "../components/MeetDialog";
import AvailabilityResponseDialog from "../components/AvailabilityResponseDialog";
import { availabilityDateTime, availabilityLabel, getAvailabilityHistory, getAvailabilityOverview } from "../services/availabilityService";
import type { AvailabilityFilters, AvailabilityOverview, ResponseAudit } from "../services/availabilityService";
import { getMeets } from "../services/meetService";
import type { Meet } from "../types/meet";
import { formatMeetDates } from "../utils/meetDate";
import "./AuthPages.css";
import "./UsersPage.css";
import "./AvailabilityPage.css";

// Give staff a filterable response overview and a way to record replies received elsewhere.
export default function AvailabilityPage() {
  const [params] = useSearchParams();
  const initial: AvailabilityFilters = { meetId: params.get("meetId") ?? "", view: params.get("meetId") ? "ALL" : "UPCOMING", status: "", search: "" };
  const [draft, setDraft] = useState(initial);
  const [filters, setFilters] = useState(initial);
  const [meets, setMeets] = useState<Meet[]>([]);
  const [meetError, setMeetError] = useState("");
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [history, setHistory] = useState<{ id: number; name: string } | null>(null);
  const [feedback, setFeedback] = useState("");
  useEffect(/* Load real meet choices independently of response pagination. */ () => {
    const controller = new AbortController();
    getMeets(controller.signal).then(/* Supply the staff meet filter. */ data => { if (!controller.signal.aborted) { setMeets(data); setMeetError(""); } })
      .catch(/* Explain why meet choices are unavailable without hiding other filters. */ failure => { if (!controller.signal.aborted) setMeetError(failure instanceof Error ? failure.message : "Unable to load meets."); });
    return /* Cancel abandoned meet-choice requests. */ () => controller.abort();
  }, [revision]);

  // Apply the filter form together and restart pagination.
  function apply(event: FormEvent) { event.preventDefault(); setFilters({ ...draft, search: draft.search.trim() }); setPage(0); setRevision(value => value + 1); }

  return <div><Header /><main className="users-container"><div className="users-heading"><div><h1>Availability</h1><p>Track meet responses and record confirmations received from athletes or guardians.</p></div><button className="auth-button" onClick={/* Reload staff overview data and meet choices. */ () => setRevision(value => value + 1)}>Refresh</button></div>
    {feedback && <p className="auth-success" role="status">{feedback}</p>}{meetError && <p className="auth-error" role="alert">{meetError}</p>}
    <form className="availability-filters" onSubmit={apply}><label>Meet<select value={draft.meetId} onChange={/* Show a selected meet regardless of whether it is past or upcoming. */ event => setDraft({ ...draft, meetId: event.target.value, view: event.target.value ? "ALL" : draft.view })}><option value="">All meets</option>{meets.map(/* Keep every existing meet available for historical review. */ meet => <option key={meet.id} value={meet.id}>{meet.name}</option>)}</select></label>
      <label>Schedule<select value={draft.view} onChange={/* Choose upcoming, past, or all nominations. */ event => setDraft({ ...draft, view: event.target.value as AvailabilityFilters["view"] })}><option value="UPCOMING">Upcoming</option><option value="PAST">Past</option><option value="ALL">All</option></select></label>
      <label>Status<select value={draft.status} onChange={/* Filter the response table by availability status. */ event => setDraft({ ...draft, status: event.target.value })}><option value="">All statuses</option><option value="AVAILABLE">Available</option><option value="PENDING">Awaiting response</option><option value="UNAVAILABLE">Unavailable</option></select></label>
      <label>Search athletes<input type="search" maxLength={200} value={draft.search} placeholder="Athlete name" onChange={/* Keep search typing separate from submitted filters. */ event => setDraft({ ...draft, search: event.target.value })} /></label><button className="auth-button primary" type="submit">Apply</button><button className="auth-button" type="button" onClick={/* Clear all filters and return to upcoming meets. */ () => { const empty: AvailabilityFilters = { meetId: "", view: "UPCOMING", status: "", search: "" }; setDraft(empty); setFilters(empty); setPage(0); }}>Clear</button></form>
    <OverviewResults key={`${JSON.stringify(filters)}:${page}:${revision}`} filters={filters} page={page} onPage={setPage} onRetry={/* Retry the current overview request. */ () => setRevision(value => value + 1)} onRespond={setSelected} onHistory={setHistory} />
    {selected !== null && <AvailabilityResponseDialog id={selected} onClose={/* Dismiss external response entry. */ () => setSelected(null)} onSaved={/* Refresh summary counts after a saved external response. */ request => { setSelected(null); setFeedback(`Response saved for ${request.firstName} ${request.lastName}: ${availabilityLabel(request.status)}.`); setRevision(value => value + 1); }} />}
    {history && <ResponseHistory id={history.id} name={history.name} onClose={/* Close the staff-only response audit. */ () => setHistory(null)} />}
  </main></div>;
}

// Fetch one overview page while keeping stale rows out of newly selected filters.
function OverviewResults({ filters, page, onPage, onRetry, onRespond, onHistory }: {
  filters: AvailabilityFilters; page: number; onPage: (page: number) => void; onRetry: () => void;
  onRespond: (id: number) => void; onHistory: (target: { id: number; name: string }) => void;
}) {
  const [data, setData] = useState<AvailabilityOverview | null>(null);
  const [error, setError] = useState("");
  useEffect(/* Cancel overview queries when the filter or page changes. */ () => {
    const controller = new AbortController();
    getAvailabilityOverview(filters, page, controller.signal).then(/* Display server-calculated summaries and current rows. */ result => { if (!controller.signal.aborted) setData(result); })
      .catch(/* Offer recovery without showing rows from an older filter. */ failure => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Unable to load availability."); });
    return /* Stop the previous overview request. */ () => controller.abort();
  }, [filters, page]);
  if (error) return <div className="auth-error" role="alert">{error} <button className="auth-button" onClick={onRetry}>Try again</button></div>;
  if (!data) return <p role="status">Loading availability…</p>;
  return <><div className="availability-summary" aria-label="Meet response summary"><div className="available"><strong>{data.summary.available}</strong><span>Available</span></div><div className="pending"><strong>{data.summary.awaitingResponse}</strong><span>Awaiting response</span></div><div className="unavailable"><strong>{data.summary.unavailable}</strong><span>Unavailable</span></div></div><p className="auth-footnote">Counts reflect the current meet, schedule, status, and search filters across all result pages.</p>
    <section className="users-panel"><div className="users-table-scroll"><table className="users-table"><caption className="availability-caption">Athlete availability — {data.totalElements} matching nominations</caption><thead><tr><th scope="col">Athlete</th><th scope="col">Meet &amp; events</th><th scope="col">Availability</th><th scope="col">Latest response</th><th scope="col">Actions</th></tr></thead><tbody>{data.content.map(/* Render each athlete/meet nomination with its current proposal status. */ row => <tr key={row.id}><th scope="row"><Link to={`/athletes/${row.athleteId}`}>{row.firstName} {row.lastName}</Link></th><td><Link to={`/meets/${row.meetId}`}>{row.meetName}</Link><p className="auth-footnote">{formatMeetDates(row.startDate, row.endDate)}</p><span>{row.events.map(/* List event assignments without revealing unrelated athletes. */ event => event.eventName).join(", ")}</span></td><td><span className={`availability-status ${row.status.toLowerCase()}`}>{availabilityLabel(row.status)}</span>{(row.status === "PENDING" || row.status === "AWAITING_RESPONSE") && row.lastAnswer && <p className="auth-footnote">Assignments changed — reconfirmation needed</p>}</td><td>{row.latestResponse ? <><span>{channelLabel(row.latestResponse.communicationMethod)}</span><p className="auth-footnote">{availabilityDateTime(row.latestResponse.respondedAt)}</p>{row.latestResponse.note && <p className="availability-audit-note">{row.latestResponse.note}</p>}</> : "No response yet"}</td><td><div className="availability-row-actions">{row.canRespond ? <button className="auth-button" onClick={/* Open the current proposal before recording an external reply. */ () => onRespond(row.id)}>Record response</button> : <span className="auth-footnote">Responses closed</span>}<button className="auth-button" onClick={/* Open the staff-only response trail. */ () => onHistory({ id: row.id, name: `${row.firstName} ${row.lastName}` })}>History</button></div></td></tr>)}</tbody></table>{!data.content.length && <p className="users-empty">{filters.meetId && filters.view === "UPCOMING" ? "No upcoming nominations match these filters. For a past meet, choose Past or All under Schedule." : "No nominations match these filters."}</p>}</div></section>
    {data.totalPages > 1 && <div className="availability-pagination"><span>Page {page + 1} of {data.totalPages}</span><div><button className="auth-button" disabled={page === 0} onClick={/* Load the previous nomination page. */ () => onPage(page - 1)}>Previous</button><button className="auth-button" disabled={page + 1 >= data.totalPages} onClick={/* Load the next nomination page. */ () => onPage(page + 1)}>Next</button></div></div>}
  </>;
}

// Explain who supplied a response without exposing internal actor IDs.
function channelLabel(channel: ResponseAudit["communicationMethod"]): string {
  return ({ PORTAL: "Athlete / guardian portal", PHONE: "Phone", WHATSAPP: "WhatsApp", EMAIL: "Email", TEXT: "Text message", IN_PERSON: "In person", OTHER: "Other" })[channel];
}

// Show the append-only reply trail so staff can distinguish old and current confirmations.
function ResponseHistory({ id, name, onClose }: { id: number; name: string; onClose: () => void }) {
  const [data, setData] = useState<ResponseAudit[] | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(/* Read response history only while the audit dialog is open. */ () => {
    const controller = new AbortController();
    getAvailabilityHistory(id, controller.signal).then(/* Preserve the server's newest-first audit order. */ rows => { if (!controller.signal.aborted) setData(rows); })
      .catch(/* Keep audit read errors separate from response entry. */ failure => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Unable to load history."); });
    return /* Cancel history reads on dismissal. */ () => controller.abort();
  }, [id, revision]);
  return <MeetDialog title={`Response history — ${name}`} onClose={onClose}>{error ? <div className="auth-error" role="alert">{error} <button className="auth-button" onClick={/* Retry the history request. */ () => { setError(""); setRevision(value => value + 1); }}>Try again</button></div> : !data ? <p role="status">Loading response history…</p> : !data.length ? <p>No responses have been recorded yet.</p> : <ol className="availability-audit">{data.map(/* Explain each historical reply and the proposal revision it confirmed. */ reply => <li key={reply.id}><strong>{availabilityLabel(reply.status)}</strong><p>{channelLabel(reply.communicationMethod)} · {availabilityDateTime(reply.respondedAt)}</p><p className="auth-footnote">Assignment revision {reply.assignmentRevision}</p>{reply.note && <p>{reply.note}</p>}</li>)}</ol>}</MeetDialog>;
}
