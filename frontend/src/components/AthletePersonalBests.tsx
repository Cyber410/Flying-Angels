import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import type { PerformanceHistory } from "../types/performance";
import { getPerformanceHistory } from "../services/performanceService";
import { displayPerformance, performanceDate, performanceSeriesKey } from "../utils/performanceDisplay";
import PerformanceProgression from "./PerformanceProgression";
import "./AthletePersonalBests.css";
import "./AthleteRankings.css";

// Load personal bests from the appropriate permission-scoped history endpoint.
export default function AthletePersonalBests({ athleteId, portal = false }: { athleteId?: number; portal?: boolean }) {
  const [data, setData] = useState<PerformanceHistory | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState("");
  const [eventFilter, setEventFilter] = useState("");
  const [unitFilter, setUnitFilter] = useState("");
  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    const controller = new AbortController();
    // Ignore abandoned responses when the athlete or profile tab changes.
    async function load() {
      try { const history = await getPerformanceHistory(athleteId, portal, controller.signal); if (!controller.signal.aborted) setData(history); }
      catch (failure) { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Unable to load personal bests."); }
      finally { if (!controller.signal.aborted) setLoading(false); }
    }
    void load(); return /* Stop pending work and release this effect when it is cleaned up. */ () => controller.abort();
  }, [athleteId, portal, revision]);

  // Refresh from saved results rather than maintaining manually entered bests.
  function refresh() { setError(""); setLoading(true); setRevision(/* Derive the next revision value from its current state. */ value => value + 1); }

  // Narrow event series without recalculating their all-time personal bests.
  const performances = data?.performances.filter(item => (!eventFilter || String(item.categoryId) === eventFilter) && (!unitFilter || item.unit === unitFilter)) ?? [];
  const eventChoices = [...new Map(data?.performances.map(item => [item.categoryId, item.categoryName])).entries()];
  const series = performances.find(/* Find the item matching the requested identifier or value. */ item => performanceSeriesKey(item) === selected) ?? performances[0];
  return <div className="pb-content"><div className="pb-section-heading"><div><h2>Personal Bests</h2><p>Best valid competition performances, calculated from recorded results.</p></div><button className="pb-refresh" onClick={refresh} disabled={loading}>{loading ? "Loading…" : "Refresh"}</button></div>
    {loading ? <p className="pb-empty" role="status">Loading personal bests…</p> : error ? <p className="pb-error" role="alert">{error}</p> : !data?.performances.length ? <p className="pb-empty">No competition results yet. Personal bests will appear when valid meet results are recorded. Historical marks remain in Meets &amp; Results.</p> : <>
      <div className="ranking-filters">
        <label>Event<select value={eventFilter} onChange={/* Narrow personal bests to one event. */ event => setEventFilter(event.target.value)}><option value="">All events</option>{eventChoices.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label>
        <label>Result type<select value={unitFilter} onChange={/* Group performances by their measurement type. */ event => setUnitFilter(event.target.value)}><option value="">All result types</option><option value="SECONDS">Time</option><option value="METRES">Distance / height</option><option value="POINTS">Points</option></select></label>
        <button className="pb-refresh" onClick={/* Restore all personal-best series. */ () => { setEventFilter(""); setUnitFilter(""); }}>Clear filters</button>
      </div>
      {!performances.length ? <p className="pb-empty">No personal best events match these filters.</p> : <>
      <div className="pb-table-scroll" tabIndex={0} role="region" aria-label="Personal bests by event"><table className="pb-table"><caption className="pb-sr-only">Personal bests by event</caption><thead><tr><th scope="col">Event</th><th scope="col">Best</th><th scope="col">Meet</th><th scope="col">Date</th></tr></thead><tbody>{performances.map(/* Render each item in the list. */ item => <tr key={performanceSeriesKey(item)}><th scope="row">{item.categoryName}</th>{item.personalBest ? <><td className="pb-best">{displayPerformance(item.personalBest, item.unit)}</td><td>{portal ? item.personalBest.meetName : <Link to={`/meets/${item.personalBest.meetId}`}>{item.personalBest.meetName}</Link>}</td><td>{performanceDate(item.personalBest.eventDate)}</td></> : <td colSpan={3} className="pb-muted">{item.recordedAttemptCount ? "No valid performance" : "No results recorded"}</td>}</tr>)}</tbody></table></div>
      <label className="pb-event-select">View progression for<select value={series ? performanceSeriesKey(series) : ""} onChange={/* Update the selected value from this control. */ event => setSelected(event.target.value)}>{performances.map(/* Render each item in the list. */ item => <option key={performanceSeriesKey(item)} value={performanceSeriesKey(item)}>{item.categoryName} · {item.unit.toLowerCase()} · {item.rankingOrder === "ASC" ? "lowest first" : "highest first"}</option>)}</select></label>
      {series && <PerformanceProgression series={series} portal={portal} />}
      </>}
    </>}
  </div>;
}
