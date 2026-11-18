import { useOptionalAuth } from "../auth/authContext";
// Searches meet date ranges and opens create, edit, and delete dialogs.
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Header from "../components/header";
import MeetDialog from "../components/MeetDialog";
import MeetFormDialog from "../components/MeetFormDialog";
import { createMeet, deleteMeet, getMeets, updateMeet } from "../services/meetService";
import type { Meet, MeetRequest, MeetView } from "../types/meet";
import "./MeetsListPage.css";
import { formatMeetDates } from "../utils/meetDate";

type ActiveDialog =
  | { type: "create" }
  | { type: "edit"; meet: Meet }
  | { type: "delete"; meet: Meet }
  | null;

// Display searchable meets and coordinate creation, editing, and deletion.
export default function MeetsListPage() {
  const auth = useOptionalAuth();
  const canManage = auth ? auth.can("MANAGE_MEETS") : true;
  const [meets, setMeets] = useState<Meet[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [loadVersion, setLoadVersion] = useState(0);
  const [view, setView] = useState<MeetView>("UPCOMING");
  const [search, setSearch] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [activeDialog, setActiveDialog] = useState<ActiveDialog>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [feedback, setFeedback] = useState("");

  // Load meet summaries and ignore results after navigation.
  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    let ignore = false;
    getMeets(undefined, view).then(/* Apply the successful request result to the current operation. */ (data) => {
      if (!ignore) {
        setMeets(data);
        setIsLoading(false);
      }
    }).catch(/* Report the request failure without discarding the current view. */ (error: unknown) => {
      if (!ignore) {
        setLoadError(error instanceof Error ? error.message : "Unable to load meets.");
        setIsLoading(false);
      }
    });
    return /* Stop pending work and release this effect when it is cleaned up. */ () => { ignore = true; };
  }, [loadVersion, view]);

  // Include meets that overlap the requested date window.
  const invalidDateRange = Boolean(fromDate && toDate && fromDate > toDate);
  const query = search.trim().toLowerCase();
  const filteredMeets = meets.filter(/* Keep entries matching the current search text. */ (meet) =>
    !invalidDateRange &&
    (!query || `${meet.name} ${meet.location ?? ""}`.toLowerCase().includes(query)) &&
    (!fromDate || (meet.endDate || meet.meetDate) >= fromDate) &&
    (!toDate || meet.meetDate <= toDate)
  ).sort(/* Show the nearest upcoming meet first and the most recent historical meet first. */ (a, b) =>
    (view === "UPCOMING" ? a.meetDate.localeCompare(b.meetDate) : b.meetDate.localeCompare(a.meetDate)) || a.name.localeCompare(b.name));
  const totalPages = Math.max(1, Math.ceil(filteredMeets.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const startIndex = (currentPage - 1) * pageSize;
  const visibleMeets = filteredMeets.slice(startIndex, startIndex + pageSize);
  const hasFilters = Boolean(search || fromDate || toDate);

  // Resets filter fields and returns pagination to its first page.
  function clearFilters() {
    setSearch("");
    setFromDate("");
    setToDate("");
    setPage(1);
  }

  // Refresh availability counts and schedule membership from the backend.
  function refresh() { setLoadError(""); setIsLoading(true); setLoadVersion(version => version + 1); }

  // Switch calendar scope and reset paging while retaining search and date filters.
  function changeView(next: MeetView) { if (next === view) return; setView(next); setPage(1); setLoadError(""); setIsLoading(true); }

  // Create or update the selected meet and refresh the meet list.
  async function handleSave(request: MeetRequest) {
    if (activeDialog?.type === "edit") {
      const updated = await updateMeet(activeDialog.meet.id, request);
      setFeedback(`${updated.name} was updated.`);
    } else {
      const created = await createMeet(request);
      clearFilters();
      setView("ALL");
      setFeedback(`${created.name} was created.`);
    }
    setActiveDialog(null);
    refresh();
  }

  // Delete the confirmed meet and keep pagination within range.
  async function handleDelete() {
    if (activeDialog?.type !== "delete" || isDeleting) return;
    const meet = activeDialog.meet;
    setIsDeleting(true);
    setDeleteError("");
    try {
      await deleteMeet(meet.id);
      setMeets(/* Derive the next meets value from its current state. */ (current) => current.filter(/* Exclude entries that do not belong in this result or selection. */ (item) => item.id !== meet.id));
      setFeedback(`${meet.name} was deleted.`);
      setActiveDialog(null);
    } catch (error) {
      setDeleteError(error instanceof Error ? error.message : "Unable to delete meet.");
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="meets-page">
      <Header />
      <main className="meets-container">
        <div className="meets-heading meets-scheduling-heading">
          <div><h1>Meets</h1><p>Plan your schedule and track athlete availability at a glance.</p></div>
          <div className="meets-heading-actions"><button className="meets-button" type="button" onClick={refresh} disabled={isLoading}>Refresh</button><button hidden={!canManage} className="meets-button meets-button-primary" type="button" disabled={isLoading} onClick={/* Open meet creation without changing the current filters. */ () => { setFeedback(""); setActiveDialog({ type: "create" }); }}>+ New Meet</button></div>
        </div>

        <p className="meets-feedback" role="status">{feedback}</p>

        <section className="meets-panel" aria-label="Meets list">
          <div className="meets-toolbar">
            <div className="meets-schedule-tabs" role="group" aria-label="Meet schedule">{(["UPCOMING", "PAST", "ALL"] as const).map(/* Apply the backend's schedule scopes through accessible toggle buttons. */ schedule => <button key={schedule} type="button" aria-pressed={view === schedule} onClick={/* Switch the meet calendar scope. */ () => changeView(schedule)}>{schedule === "UPCOMING" ? "Upcoming" : schedule === "PAST" ? "Past" : "All"}</button>)}</div>
          </div>
          <div className="meets-toolbar meets-search-toolbar">
            <input
              className="meets-search"
              type="search"
              aria-label="Search meets"
              placeholder="Search by meet name or location..."
              value={search}
              onChange={/* Update the search value from this control. */ (event) => { setSearch(event.target.value); setPage(1); }}
            />
            <span className="meets-overview-hint">One response per athlete, per meet</span>
          </div>

          <div className="meets-filters">
            <label>
              From date
              <input
                type="date"
                value={fromDate}
                max={toDate || undefined}
                aria-invalid={invalidDateRange}
                aria-describedby={invalidDateRange ? "meets-date-error" : undefined}
                onChange={/* Update the from date value from this control. */ (event) => { setFromDate(event.target.value); setPage(1); }}
              />
            </label>
            <label>
              To date
              <input
                type="date"
                value={toDate}
                min={fromDate || undefined}
                aria-invalid={invalidDateRange}
                aria-describedby={invalidDateRange ? "meets-date-error" : undefined}
                onChange={/* Update the to date value from this control. */ (event) => { setToDate(event.target.value); setPage(1); }}
              />
            </label>
            {hasFilters && (
              <button className="meets-clear" type="button" onClick={clearFilters}>Clear filters</button>
            )}
            {invalidDateRange && (
              <p className="meets-date-error" id="meets-date-error" role="alert">
                From date must be on or before To date.
              </p>
            )}
          </div>

          {isLoading ? (
            <p className="meets-state" role="status">Loading meets...</p>
          ) : loadError ? (
            <div className="meets-state">
              <p role="alert">{loadError}</p>
              <button
                className="meets-button"
                type="button"
                onClick={/* Update load error for the selected action. */ () => {
                  setLoadError("");
                  setIsLoading(true);
                  setLoadVersion(/* Derive the next load version value from its current state. */ (version) => version + 1);
                }}
              >
                Try again
              </button>
            </div>
          ) : (
            <>
              <div className="meets-table-scroll" role="region" aria-label="Meets table" tabIndex={0}>
                <table className="meets-table">
                  <caption className="meets-sr-only">{view === "UPCOMING" ? "Upcoming meets, earliest first" : "Flying Angels meets, most recent first"}</caption>
                  <thead>
                    <tr>
                      <th scope="col">Meet Name</th>
                      <th scope="col">Date</th>
                      <th scope="col">Location</th>
                      <th scope="col">Events</th>
                      <th scope="col">Athlete availability</th>
                      <th scope="col">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleMeets.map(/* Render each meet in the list. */ (meet) => (
                      <tr key={meet.id}>
                        <th scope="row"><Link className="meets-name-link" to={`/meets/${meet.id}`}>{meet.name}</Link></th>
                        <td><time dateTime={meet.meetDate}>{formatMeetDates(meet.meetDate, meet.endDate)}</time></td>
                        <td>{meet.location || "—"}</td>
                        <td>{meet.eventCount}</td>
                        <td><MeetAvailability meet={meet} /></td>
                        <td>
                          <div className="meets-row-actions">
                            <Link className="meets-action meets-view-link" to={`/meets/${meet.id}`} aria-label={`View ${meet.name}`}>View</Link>
                            <button hidden={!canManage}
                              className="meets-action"
                              type="button"
                              aria-label={`Edit ${meet.name}`}
                              onClick={/* Update feedback for the selected action. */ () => { setFeedback(""); setActiveDialog({ type: "edit", meet }); }}
                            >
                              Edit
                            </button>
                            <button hidden={!canManage}
                              className="meets-action meets-action-delete"
                              type="button"
                              aria-label={`Delete ${meet.name}`}
                              onClick={/* Update feedback for the selected action. */ () => {
                                setFeedback("");
                                setDeleteError("");
                                setActiveDialog({ type: "delete", meet });
                              }}
                            >
                              Delete
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                    {visibleMeets.length === 0 && (
                      <tr>
                        <td colSpan={6} className="meets-empty">
                          <p>{hasFilters ? "No meets match your search or date range." : view === "ALL" ? "No meets yet. Create your first meet to get started." : `No ${view === "UPCOMING" ? "upcoming" : "past"} meets to display.`}</p>
                          {!hasFilters && (view === "ALL" ? <Link className="meets-sample-link" to="/meets/demo">Preview a sample meet</Link> : <button type="button" className="meets-clear" onClick={/* Reveal meets outside the current calendar scope. */ () => changeView("ALL")}>View all meets</button>)}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              <div className="meets-footer">
                <label className="meets-page-size">
                  Show
                  <select
                    aria-label="Meets per page"
                    value={pageSize}
                    onChange={/* Update the page size value from this control. */ (event) => { setPageSize(Number(event.target.value)); setPage(1); }}
                  >
                    <option value={10}>10</option>
                    <option value={25}>25</option>
                    <option value={50}>50</option>
                  </select>
                </label>
                <p aria-live="polite">
                  {filteredMeets.length ? startIndex + 1 : 0}–{Math.min(startIndex + pageSize, filteredMeets.length)} of {filteredMeets.length} meets
                </p>
                <nav className="meets-pagination" aria-label="Meets pagination">
                  <button
                    className="meets-button"
                    type="button"
                    aria-label="Previous page"
                    disabled={currentPage === 1}
                    onClick={/* Show the previous page of matching records. */ () => setPage(currentPage - 1)}
                  >
                    &lsaquo;
                  </button>
                  <span>Page {currentPage} of {totalPages}</span>
                  <button
                    className="meets-button"
                    type="button"
                    aria-label="Next page"
                    disabled={currentPage === totalPages}
                    onClick={/* Show the next page of matching records. */ () => setPage(currentPage + 1)}
                  >
                    &rsaquo;
                  </button>
                </nav>
              </div>
            </>
          )}
        </section>
      </main>

      {(activeDialog?.type === "create" || activeDialog?.type === "edit") && (
        <MeetFormDialog
          meet={activeDialog.type === "edit" ? activeDialog.meet : undefined}
          onSave={handleSave}
          onClose={/* Close the dialog and clear its visible state. */ () => setActiveDialog(null)}
        />
      )}
      {activeDialog?.type === "delete" && (
        <MeetDialog title="Delete Meet" busy={isDeleting} onClose={/* Close the dialog and clear its visible state. */ () => setActiveDialog(null)}>
          <p className="meet-dialog-message">
            Delete <strong>{activeDialog.meet.name}</strong>? This cannot be undone.
            {activeDialog.meet.eventCount > 0 && " Its events and their results will also be deleted."}
          </p>
          {deleteError && <p className="meet-dialog-error" role="alert">{deleteError}</p>}
          <div className="meet-dialog-actions">
            <button className="meets-button" type="button" autoFocus disabled={isDeleting} onClick={/* Update active dialog for the selected action. */ () => setActiveDialog(null)}>
              Cancel
            </button>
            <button className="meets-button meets-button-danger" type="button" disabled={isDeleting} onClick={handleDelete}>
              {isDeleting ? "Deleting..." : "Delete Meet"}
            </button>
          </div>
        </MeetDialog>
      )}
    </div>
  );
}

// Display real meet-level nomination counts with a direct route to the response overview.
function MeetAvailability({ meet }: { meet: Meet }) {
  const summary = meet.availabilitySummary;
  if (!summary) return <span className="meets-availability-muted">Availability not loaded</span>;
  return <div className="meets-availability"><div className="meets-response-badges" aria-label={`Availability for ${meet.name}`}>
    <span className="available"><strong>{summary.available}</strong> Available</span>
    <span className="pending"><strong>{summary.awaitingResponse}</strong> Awaiting</span>
    <span className="unavailable"><strong>{summary.unavailable}</strong> Unavailable</span>
  </div><div className="meets-availability-footer"><span>{summary.total} {summary.total === 1 ? "athlete" : "athletes"} nominated</span><Link to={`/availability?meetId=${meet.id}`} aria-label={`Manage availability for ${meet.name}`}>View responses →</Link></div></div>;
}
