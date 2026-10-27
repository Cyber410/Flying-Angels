import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Header from "../components/header";
import { getResultsOverview, getSampleResultsOverview } from "../services/resultsService";
import { AGE_GROUPS } from "../data/athleteOptions";
import type { ResultsOverview } from "../types/results";
import "./MeetsListPage.css";
import "./ResultsPage.css";

export default function ResultsPage() {
  const [params] = useSearchParams();
  const sample = params.get("sample") === "1";
  return <ResultsOverviewContent key={sample ? "sample" : "live"} sample={sample} />;
}

function ResultsOverviewContent({ sample }: { sample: boolean }) {
  const [data, setData] = useState<ResultsOverview | null>(() => sample ? getSampleResultsOverview() : null);
  const [isLoading, setIsLoading] = useState(!sample);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [search, setSearch] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [meetId, setMeetId] = useState("");
  const [ageGroup, setAgeGroup] = useState("");
  const [gender, setGender] = useState("");
  const [view, setView] = useState<"best" | "attempts">("best");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  useEffect(() => {
    if (sample) return;
    const controller = new AbortController();
    let ignore = false;
    getResultsOverview(controller.signal).then((overview) => {
      if (!ignore) setData(overview);
    }).catch((error: unknown) => {
      if (!ignore) setError(error instanceof Error ? error.message : "Unable to load results.");
    }).finally(() => {
      if (!ignore) setIsLoading(false);
    });
    return () => { ignore = true; controller.abort(); };
  }, [sample, revision]);

  const rows = (view === "best" ? data?.sharedResults : data?.allAttempts) ?? [];
  const query = search.trim().toLowerCase();
  const filtered = rows.filter((row) =>
    (!query || `${row.firstName} ${row.lastName}`.toLowerCase().includes(query)) &&
    (!categoryId || String(row.categoryId) === categoryId) &&
    (!meetId || String(row.meetId) === meetId) &&
    (!ageGroup || row.ageGroup === ageGroup) &&
    (!gender || row.gender === gender)
  );
  const categories = [...new Map((data?.events ?? []).map((event) =>
    [event.category.id, { id: event.category.id, name: event.category.name }] as const
  )).values()].sort((a, b) => a.name.localeCompare(b.name));
  const meets = [...new Map((data?.events ?? []).map((event) =>
    [event.meetId, { id: event.meetId, name: event.meetName }] as const
  )).values()].sort((a, b) => a.name.localeCompare(b.name));
  const genders = [...new Set((data?.sharedResults ?? []).map((row) => row.gender))].sort();
  const hasFilters = Boolean(search || categoryId || meetId || ageGroup || gender || view !== "best");
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const startIndex = (currentPage - 1) * pageSize;
  const visibleRows = filtered.slice(startIndex, startIndex + pageSize);
  const athleteCount = new Set(filtered.map((row) => row.athleteId)).size;
  const meetCount = new Set(filtered.map((row) => row.meetId)).size;

  function clearFilters() {
    setSearch("");
    setCategoryId("");
    setMeetId("");
    setAgeGroup("");
    setGender("");
    setView("best");
    setPage(1);
  }

  function refresh() {
    setError("");
    setIsLoading(true);
    setRevision((value) => value + 1);
  }

  return (
    <div className="results-page">
      <Header />
      <main className="results-container">
        <div className="results-heading">
          <h1>Results Overview</h1>
          <p>Search, filter, and compare athlete performances across meets.</p>
        </div>
        {sample && (
          <div className="results-sample-banner" role="note">
            <strong>Sample results preview</strong>
            <p>These fictional performances reuse the sample meet. Age and gender are not provided. <Link to="/results">Back to live results</Link></p>
          </div>
        )}
        <section className="results-panel" aria-label="Results overview" aria-busy={isLoading}>
          <div className="results-toolbar">
            <input
              className="meets-search"
              type="search"
              aria-label="Search athlete names"
              placeholder="Search athlete names..."
              value={search}
              onChange={(change) => { setSearch(change.target.value); setPage(1); }}
            />
            <label className="results-view-label">
              Result view
              <select
                value={view}
                onChange={(change) => { setView(change.target.value as "best" | "attempts"); setPage(1); }}
              >
                <option value="best">Shared best results</option>
                <option value="attempts">All attempts in this browser</option>
              </select>
            </label>
            {!sample && <button className="meets-button" type="button" disabled={isLoading} onClick={refresh}>Refresh</button>}
          </div>
          <div className="results-filters">
            <label>
              Event
              <select value={categoryId} onChange={(change) => { setCategoryId(change.target.value); setPage(1); }}>
                <option value="">All events</option>
                {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
              </select>
            </label>
            <label>
              Meet
              <select value={meetId} onChange={(change) => { setMeetId(change.target.value); setPage(1); }}>
                <option value="">All meets</option>
                {meets.map((meet) => <option key={meet.id} value={meet.id}>{meet.name}</option>)}
              </select>
            </label>
            <label>
              Age Group
              <select value={ageGroup} onChange={(change) => { setAgeGroup(change.target.value); setPage(1); }}>
                <option value="">All age groups</option>
                {AGE_GROUPS.map((group) => <option key={group} value={group}>{group}</option>)}
                <option value="Under 5">Under 5</option>
                <option value="Not provided">Not provided</option>
              </select>
            </label>
            <label>
              Gender
              <select value={gender} onChange={(change) => { setGender(change.target.value); setPage(1); }}>
                <option value="">All genders</option>
                {genders.map((item) => <option key={item} value={item}>{item}</option>)}
              </select>
            </label>
            <button className="meets-button results-clear-button" type="button" disabled={!hasFilters} onClick={clearFilters}>Clear Filters</button>
          </div>
          <p className="results-scope-note">
            {view === "best"
              ? "Showing each athlete's shared best result per event."
              : "Full attempt history is available only in the browser where it was recorded. Browser-only attempts are labeled below."}
            {" Age groups use age on the event date. Only recorded performances linked to a meet are shown."}
          </p>

          {isLoading ? <p className="meets-state" role="status">Loading results...</p> : error ? (
            <div className="meets-state">
              <p role="alert">{error}</p>
              <button className="meets-button" type="button" onClick={refresh}>Try again</button>
              <p><Link className="results-preview-link" to="/results?sample=1">Preview sample results</Link></p>
            </div>
          ) : (
            <>
              {data && (data.warnings.length > 0 || data.failedEvents.length > 0) && (
                <div className="results-warning" role="status">
                  {data.warnings.map((warning) => <p key={warning}>{warning}</p>)}
                  {data.failedEvents.length > 0 && (
                    <details>
                      <summary>Results unavailable for {data.failedEvents.length} event{data.failedEvents.length === 1 ? "" : "s"}. Refresh to retry.</summary>
                      <ul>{data.failedEvents.map((event) => <li key={event.eventId}>{event.eventName} · {event.meetName}: {event.message}</li>)}</ul>
                    </details>
                  )}
                </div>
              )}
              <div className="results-summary" aria-live="polite">
                {filtered.length} result{filtered.length === 1 ? "" : "s"} · {athleteCount} athlete{athleteCount === 1 ? "" : "s"} · {meetCount} meet{meetCount === 1 ? "" : "s"}
              </div>
              <div className="meets-table-scroll" role="region" aria-label="Athlete results table" tabIndex={0}>
                <table className="meets-table results-table">
                  <caption className="meets-sr-only">Athlete performances across meets, newest events first</caption>
                  <thead>
                    <tr>
                      <th scope="col">Athlete</th><th scope="col">Event</th><th scope="col">Meet</th>
                      <th scope="col">Age Group</th><th scope="col">Gender</th><th scope="col">Result</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleRows.map((row) => (
                      <tr key={row.id}>
                        <th scope="row">
                          {sample ? `${row.firstName} ${row.lastName}` : <Link to={`/athletes/${row.athleteId}`}>{row.firstName} {row.lastName}</Link>}
                        </th>
                        <td>{row.eventName}</td>
                        <td>
                          <Link to={sample ? "/meets/demo" : `/meets/${row.meetId}`}>{row.meetName}</Link>
                          <time className="results-meet-date" dateTime={row.eventDate}>
                            {new Date(`${row.eventDate}T00:00:00`).toLocaleDateString("en-CA", { month: "short", day: "numeric", year: "numeric" })}
                          </time>
                        </td>
                        <td>{row.ageGroup}</td><td>{row.gender}</td>
                        <td className="results-result-cell">
                          <span className="results-value">{row.result}</span>
                          {view === "attempts" && <small className={row.source === "browser" ? "results-browser-label" : "results-shared-label"}>{sample ? "Sample" : row.source === "browser" ? "Browser only" : "Shared best"}</small>}
                        </td>
                      </tr>
                    ))}
                    {visibleRows.length === 0 && (
                      <tr><td colSpan={6} className="meets-empty">
                        <p>{hasFilters ? "No results match your search or filters." : "No recorded meet results yet. Add results from a meet's event to see them here."}</p>
                        {hasFilters
                          ? <button className="meets-button results-empty-action" type="button" onClick={clearFilters}>Clear Filters</button>
                          : <Link className="results-empty-action" to="/meets">Go to Meets</Link>}
                        {!sample && <Link className="results-preview-link" to="/results?sample=1">Preview sample results</Link>}
                      </td></tr>
                    )}
                  </tbody>
                </table>
              </div>
              <div className="meets-footer">
                <label className="meets-page-size">
                  Show
                  <select aria-label="Results per page" value={pageSize} onChange={(change) => { setPageSize(Number(change.target.value)); setPage(1); }}>
                    <option value={10}>10</option><option value={25}>25</option><option value={50}>50</option>
                  </select>
                </label>
                <p aria-live="polite">{filtered.length ? startIndex + 1 : 0}–{Math.min(startIndex + pageSize, filtered.length)} of {filtered.length} results</p>
                <nav className="meets-pagination" aria-label="Results pagination">
                  <button className="meets-button" type="button" aria-label="Previous page" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}>‹</button>
                  <span>Page {currentPage} of {totalPages}</span>
                  <button className="meets-button" type="button" aria-label="Next page" disabled={currentPage === totalPages} onClick={() => setPage(currentPage + 1)}>›</button>
                </nav>
              </div>
            </>
          )}
        </section>
      </main>
    </div>
  );
}
