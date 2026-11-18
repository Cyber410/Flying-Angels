import { useEffect, useState } from "react";
import { getPortalRankingEvents, getPortalRankings } from "../services/portalRankingsService";
import type { PortalRanking, RankingEvent, PortalRankingFilters } from "../services/portalRankingsService";
import { displayPerformance, performanceDate } from "../utils/performanceDisplay";
import "./AthletePersonalBests.css";
import "./AthleteRankings.css";

// Offer rankings only for competitions returned by the athlete-scoped backend.
export default function AthleteRankings() {
  const [events, setEvents] = useState<RankingEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [meetId, setMeetId] = useState("");
  const [eventId, setEventId] = useState("");
  const [ageBand, setAgeBand] = useState("");
  const [gender, setGender] = useState("");
  const [minAge, maxAge] = ageBand ? ageBand.split(":").map(Number) : [];
  const filters: PortalRankingFilters = { gender, minAge, maxAge };
  useEffect(/* Cancel event discovery when this tab closes or is refreshed. */ () => {
    const controller = new AbortController();
    getPortalRankingEvents(controller.signal).then(/* Display the authorized event choices. */ data => {
      if (!controller.signal.aborted) { setEvents(data); setError(""); setLoading(false); }
    }).catch(/* Preserve a retryable error when event discovery fails. */ failure => {
      if (!controller.signal.aborted) { setError(failure instanceof Error ? failure.message : "Unable to load events."); setLoading(false); }
    });
    return /* Stop requests belonging to the previous view. */ () => controller.abort();
  }, [revision]);
  const meets = [...new Map(events.map(/* Deduplicate meet choices without exposing unrelated meets. */ event => [String(event.meetId), event.meetName])).entries()];
  const selectedMeet = meets.some(/* Retain a selection only while the meet remains accessible. */ ([id]) => id === meetId) ? meetId : meets[0]?.[0];
  const choices = events.filter(/* Limit event choices to the selected meet. */ event => String(event.meetId) === selectedMeet);
  const selected = choices.find(/* Resolve the requested event within the authorized choices. */ event => String(event.id) === eventId) ?? choices[0];

  // Retry event discovery with a fresh loading state.
  function refresh() { setLoading(true); setError(""); setRevision(value => value + 1); }

  return <section className="pb-content">
    <div className="pb-section-heading"><div><h2>My Rankings</h2><p>See where you stand in your meet events. Your row is highlighted when you have a ranked result.</p></div><button className="pb-refresh" onClick={refresh} disabled={loading}>Refresh</button></div>
    {loading ? <p role="status">Loading your events…</p> : error ? <div className="pb-error" role="alert">{error} <button className="pb-refresh" onClick={refresh}>Try again</button></div> : !events.length ? <p className="pb-empty">No meet events yet. Rankings will appear here once you are assigned to an event.</p> : <>
      <div className="ranking-filters"><label>Meet<select value={selectedMeet} onChange={/* Reset the event when the meet changes. */ event => { setMeetId(event.target.value); setEventId(""); }}>{meets.map(/* Render an accessible meet choice. */ ([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label>
        <label>Event<select value={selected?.id ?? ""} onChange={/* Choose another authorized event. */ event => setEventId(event.target.value)}>{choices.map(/* Identify repeated event names by competition date. */ event => <option key={event.id} value={event.id}>{event.name} · {performanceDate(event.eventDate)}</option>)}</select></label>
        <label>Age group<select value={ageBand} onChange={/* Translate age bands into supported numeric API filters. */ event => setAgeBand(event.target.value)}><option value="">All ages</option><option value="0:4">Under 5</option><option value="5:14">5-14</option><option value="15:18">15-18</option><option value="19:150">19+</option></select></label>
        <label>Gender<select value={gender} onChange={/* Filter the entire ranked cohort on the server. */ event => setGender(event.target.value)}><option value="">All genders</option><option value="Female">Female</option><option value="Male">Male</option></select></label>
        <button className="pb-refresh" onClick={/* Restore the full event cohort. */ () => { setAgeBand(""); setGender(""); }}>Clear filters</button></div>
      <p className="pb-muted">Age groups use completed age on the event date. Ranks reflect the selected filters.</p>
      {selected && <RankingResults key={`${selected.id}:${revision}:${ageBand}:${gender}`} event={selected} filters={filters} />}
    </>}
  </section>;
}

// Keep paging and request state specific to the selected competition event.
function RankingResults({ event, filters }: { event: RankingEvent; filters: PortalRankingFilters }) {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  return <RankingPage key={`${page}:${revision}`} event={event} filters={filters} page={page} onPage={setPage} onRetry={/* Reload only the current rankings page. */ () => setRevision(value => value + 1)} />;
}

// Render server-calculated positions and ties without adding competitor profile links.
function RankingPage({ event, filters, page, onPage, onRetry }: { event: RankingEvent; filters: PortalRankingFilters; page: number; onPage: (page: number) => void; onRetry: () => void }) {
  const [data, setData] = useState<PortalRanking | null>(null);
  const [error, setError] = useState("");
  useEffect(/* Ignore stale results when switching pages or competition events. */ () => {
    const controller = new AbortController();
    getPortalRankings(event, page, controller.signal, filters).then(/* Apply rankings only to the active request. */ result => { if (!controller.signal.aborted) setData(result); })
      .catch(/* Report ranking failures without displaying stale rows. */ failure => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Unable to load rankings."); });
    return /* Cancel this page's pending request. */ () => controller.abort();
  }, [event, filters, page]);
  if (error) return <div className="pb-error" role="alert">{error} <button className="pb-refresh" onClick={onRetry}>Try again</button></div>;
  if (!data) return <p role="status">Loading rankings…</p>;
  if (!data.totalElements) return <p className="pb-empty">{filters.gender || filters.minAge !== undefined ? "No ranked results match these filters. Try another age group or gender." : "No ranked results have been recorded for this event yet."}</p>;
  return <>
    <p className="pb-muted">Best valid result per athlete · {data.category.rankingOrder === "ASC" ? "Lowest result ranks first" : "Highest result ranks first"} · Equal results share a rank</p>
    <div className="pb-table-scroll"><table className="pb-table"><caption className="pb-sr-only">{event.name} rankings at {event.meetName}</caption><thead><tr><th scope="col">Rank</th><th scope="col">Athlete</th><th scope="col">Best result</th></tr></thead><tbody>{data.content.map(/* Mark the signed-in athlete using the server-provided identity flag. */ (row, index) => <tr key={`${page}:${index}`} className={row.profileAccessible ? "ranking-own-row" : undefined}><td>{row.position}</td><th scope="row">{row.firstName} {row.lastName}{row.profileAccessible && <span className="pb-badge">You</span>}</th><td className="pb-best">{displayPerformance({ result: row.result ?? "", score: row.score }, row.unit)}</td></tr>)}</tbody></table></div>
    <div className="ranking-pagination"><span>{data.totalElements} ranked {data.totalElements === 1 ? "athlete" : "athletes"} · Page {page + 1} of {data.totalPages}</span><div><button className="pb-refresh" disabled={page === 0} onClick={/* Load the previous ranked athletes. */ () => onPage(page - 1)}>Previous</button><button className="pb-refresh" disabled={page + 1 >= data.totalPages} onClick={/* Load the next ranked athletes. */ () => onPage(page + 1)}>Next</button></div></div>
    <p className="pb-muted">No rank yet? Your position appears after a valid result is recorded for this event.</p>
  </>;
}
