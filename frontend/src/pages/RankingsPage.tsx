import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import Header from "../components/header";
import { getRankingChoices, getStaffRankings } from "../services/rankingsService";
import type { RankingChoices, StaffRankingFilters, StaffRankings } from "../services/rankingsService";
import { displayPerformance, performanceDate } from "../utils/performanceDisplay";
import "./AuthPages.css";
import "./UsersPage.css";
import "../components/AthletePersonalBests.css";
import "./RankingsPage.css";

// Use the local calendar date as an explicit, editable age reference rather than inventing season rules.
function today(): string {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

// Load staff-only filter definitions before enabling ranking comparisons.
export default function RankingsPage() {
  const [choices, setChoices] = useState<RankingChoices | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(/* Refresh definitions without keeping requests from an abandoned view. */ () => {
    const controller = new AbortController();
    getRankingChoices(controller.signal).then(/* Keep ranking controls grounded in backend definitions. */ data => { if (!controller.signal.aborted) setChoices(data); })
      .catch(/* Show a retry when ranking choices cannot be read. */ failure => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Unable to load ranking filters."); });
    return /* Cancel stale definition requests. */ () => controller.abort();
  }, [revision]);
  return <div><Header /><main className="users-container"><div className="users-heading"><div><h1>Athlete Rankings</h1><p>Compare best competition performances by event, meet, age, and gender.</p></div></div>
    {error ? <div className="auth-error" role="alert">{error} <button className="auth-button" onClick={/* Retry loading the server's filter definitions. */ () => { setError(""); setRevision(value => value + 1); }}>Try again</button></div> : !choices ? <p role="status">Loading ranking filters…</p> : !choices.categories.length ? <p className="users-empty">No events are configured yet.</p> : <RankingsBrowser choices={choices} />}
  </main></div>;
}

// Apply related filters together, resetting event scope when a discipline or meet changes.
function RankingsBrowser({ choices }: { choices: RankingChoices }) {
  const initial: StaffRankingFilters = { categoryId: String(choices.categories.find(category => category.name.toLowerCase() === "100m")?.id ?? choices.categories[0].id),
    meetId: "", eventId: "", gender: "", ageGroup: "", minAge: "", maxAge: "", ageAsOf: today(), rankOrder: "ASC" };
  const [draft, setDraft] = useState(initial);
  const [filters, setFilters] = useState(initial);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const eventChoices = choices.events.filter(/* Offer only event instances belonging to the current discipline and meet. */ event => String(event.categoryId) === draft.categoryId && (!draft.meetId || String(event.meetId) === draft.meetId));

  // Validate numeric age ranges before asking the backend to apply the filters.
  function apply(event: FormEvent) {
    event.preventDefault();
    if (draft.minAge && draft.maxAge && Number(draft.minAge) > Number(draft.maxAge)) { setError("Minimum age must not exceed maximum age."); return; }
    setError(""); setFilters({ ...draft }); setPage(0); setRevision(value => value + 1);
  }

  return <><form className="staff-ranking-filters" onSubmit={apply}>
    <label>Event<select value={draft.categoryId} onChange={/* Clear an event instance that belongs to the previous discipline. */ event => setDraft({ ...draft, categoryId: event.target.value, eventId: "" })}>{choices.categories.map(/* Display the backend's discipline definitions. */ category => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
    <label>Meet<select value={draft.meetId} onChange={/* Clear the previous meet's event instance. */ event => setDraft({ ...draft, meetId: event.target.value, eventId: "" })}><option value="">All meets</option>{choices.meets.map(/* Keep historical meets available for comparison. */ meet => <option key={meet.id} value={meet.id}>{meet.name}</option>)}</select></label>
    <label>Competition event<select value={draft.eventId} onChange={/* Narrow rankings to one competition event and use its date for age comparisons. */ event => { const selected = choices.events.find(choice => String(choice.id) === event.target.value); setDraft({ ...draft, eventId: event.target.value, ageAsOf: selected?.eventDate ?? draft.ageAsOf }); }}><option value="">All matching events</option>{eventChoices.map(/* Distinguish event instances by meet and competition date. */ event => <option key={event.id} value={event.id}>{event.meetName} · {performanceDate(event.eventDate)}</option>)}</select></label>
    <label>Gender<select value={draft.gender} onChange={/* Apply an optional gender comparison filter. */ event => setDraft({ ...draft, gender: event.target.value })}><option value="">All genders</option><option value="Female">Female</option><option value="Male">Male</option></select></label>
    {choices.ages.configured ? <label>Age group<select value={draft.ageGroup} onChange={/* Use only the club's configured age bands. */ event => setDraft({ ...draft, ageGroup: event.target.value })}><option value="">All age groups</option>{choices.ages.bands.map(/* Preserve the club-supplied band names and bounds. */ band => <option key={band.label} value={band.label}>{band.label} ({band.minAge}–{band.maxAge})</option>)}</select></label> : <><label>Minimum age<input type="number" min={0} max={150} step={1} value={draft.minAge} placeholder="Any" onChange={/* Set an inclusive lower age bound. */ event => setDraft({ ...draft, minAge: event.target.value })} /></label><label>Maximum age<input type="number" min={0} max={150} step={1} value={draft.maxAge} placeholder="Any" onChange={/* Set an inclusive upper age bound. */ event => setDraft({ ...draft, maxAge: event.target.value })} /></label></>}
    <label>Age as of<input type="date" required min="0001-01-01" max="9999-12-31" value={draft.ageAsOf} onChange={/* Keep age comparisons anchored to an explicit date. */ event => setDraft({ ...draft, ageAsOf: event.target.value })} /></label>
    <label>Rank order<select value={draft.rankOrder} onChange={/* Choose the direction of rank numbers across the full result set. */ event => setDraft({ ...draft, rankOrder: event.target.value as "ASC" | "DESC" })}><option value="ASC">Ascending — rank 1 first</option><option value="DESC">Descending — highest rank number first</option></select></label>
    <div className="staff-ranking-actions"><button className="auth-button primary" type="submit">Apply filters</button><button className="auth-button" type="button" onClick={/* Restore the default discipline and clear comparison filters. */ () => { setDraft(initial); setFilters(initial); setError(""); setPage(0); setRevision(value => value + 1); }}>Clear</button></div>
  </form><p className="auth-footnote">Ages are completed years on the selected reference date. Only valid, meet-linked competition results are ranked; imported historical marks are excluded.</p>
    {error && <p className="auth-error" role="alert">{error}</p>}
    <StaffRankingResults key={`${JSON.stringify(filters)}:${page}:${revision}`} filters={filters} page={page} onPage={setPage} onRetry={/* Refresh only the active ranking query. */ () => setRevision(value => value + 1)} />
  </>;
}

// Render the server's global ranks and tied positions without recalculating them in the browser.
function StaffRankingResults({ filters, page, onPage, onRetry }: { filters: StaffRankingFilters; page: number;
  onPage: (page: number) => void; onRetry: () => void }) {
  const [data, setData] = useState<StaffRankings | null>(null);
  const [error, setError] = useState("");
  useEffect(/* Cancel ranking requests that no longer match the visible filters. */ () => {
    const controller = new AbortController();
    getStaffRankings(filters, page, controller.signal).then(/* Apply only the active response page. */ result => { if (!controller.signal.aborted) setData(result); })
      .catch(/* Offer recovery without retaining stale rows. */ failure => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Unable to load rankings."); });
    return /* Stop the previous ranking query. */ () => controller.abort();
  }, [filters, page]);
  if (error) return <div className="auth-error" role="alert">{error} <button className="auth-button" onClick={onRetry}>Try again</button></div>;
  if (!data) return <p role="status">Loading rankings…</p>;
  if (!data.totalElements) return <p className="users-empty">{data.status === "NO_MATCHING_ATHLETES" ? "No athletes match these filters. Try widening the age or gender selection." : "No valid competition results are available for this selection yet."}</p>;
  return <><div className="staff-ranking-heading"><h2>{data.category.name}</h2><span>{data.totalElements} ranked {data.totalElements === 1 ? "athlete" : "athletes"}</span></div><p className="auth-footnote">Best result per athlete · {data.category.rankingOrder === "DESC" ? "Highest result ranks first" : "Lowest result ranks first"} · Equal results share a rank</p>
    <div className="pb-table-scroll staff-ranking-table"><table className="pb-table"><caption className="pb-sr-only">Staff athlete rankings</caption><thead><tr><th scope="col">Rank</th><th scope="col">Athlete</th><th scope="col">Age / group</th><th scope="col">Gender</th><th scope="col">Best performance</th><th scope="col">Meet</th><th scope="col">Date</th></tr></thead><tbody>{data.content.map(/* Keep profile links restricted to the staff interface. */ row => <tr key={row.athleteId}><td>{row.position}</td><th scope="row">{row.profileAccessible ? <Link to={`/athletes/${row.athleteId}?tab=personal-bests`}>{row.firstName} {row.lastName}</Link> : `${row.firstName} ${row.lastName}`}{row.archived && <span className="pb-badge">Archived</span>}</th><td>{row.ageGroup || row.ageAtReference || (row.ageAtReference === 0 ? "0" : "—")}</td><td>{row.gender || "—"}</td><td className="pb-best">{displayPerformance({ result: row.result ?? "", score: row.score }, data.category.unit)}</td><td><Link to={`/meets/${row.meetId}`}>{row.meetName}</Link></td><td>{performanceDate(row.eventDate)}</td></tr>)}</tbody></table></div>
    <div className="staff-ranking-pagination"><span>Page {page + 1} of {data.totalPages}</span><div><button className="auth-button" disabled={page === 0} onClick={/* Display the preceding athletes without resetting rank numbers. */ () => onPage(page - 1)}>Previous</button><button className="auth-button" disabled={page + 1 >= data.totalPages} onClick={/* Display the next global ranking page. */ () => onPage(page + 1)}>Next</button></div></div>
  </>;
}
