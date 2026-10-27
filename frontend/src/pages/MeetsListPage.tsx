import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Header from "../components/header";
import MeetDialog from "../components/MeetDialog";
import MeetFormDialog from "../components/MeetFormDialog";
import { createMeet, deleteMeet, getMeets, updateMeet } from "../services/meetService";
import type { Meet, MeetRequest } from "../types/meet";
import "./MeetsListPage.css";

type ActiveDialog =
  | { type: "create" }
  | { type: "edit"; meet: Meet }
  | { type: "delete"; meet: Meet }
  | null;

const dateFormatter = new Intl.DateTimeFormat("en-CA", {
  month: "short", day: "numeric", year: "numeric",
});

function formatDate(date: string) {
  // LocalDate values must not shift to the previous day in Toronto.
  return dateFormatter.format(new Date(`${date}T00:00:00`));
}

export default function MeetsListPage() {
  const [meets, setMeets] = useState<Meet[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [loadVersion, setLoadVersion] = useState(0);
  const [search, setSearch] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [activeDialog, setActiveDialog] = useState<ActiveDialog>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [feedback, setFeedback] = useState("");

  useEffect(() => {
    let ignore = false;
    getMeets().then((data) => {
      if (!ignore) {
        setMeets(data);
        setIsLoading(false);
      }
    }).catch((error: unknown) => {
      if (!ignore) {
        setLoadError(error instanceof Error ? error.message : "Unable to load meets.");
        setIsLoading(false);
      }
    });
    return () => { ignore = true; };
  }, [loadVersion]);

  const invalidDateRange = Boolean(fromDate && toDate && fromDate > toDate);
  const query = search.trim().toLowerCase();
  const filteredMeets = meets.filter((meet) =>
    !invalidDateRange &&
    (!query || `${meet.name} ${meet.location ?? ""}`.toLowerCase().includes(query)) &&
    (!fromDate || meet.meetDate >= fromDate) &&
    (!toDate || meet.meetDate <= toDate)
  ).sort((a, b) => b.meetDate.localeCompare(a.meetDate) || a.name.localeCompare(b.name));
  const totalPages = Math.max(1, Math.ceil(filteredMeets.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const startIndex = (currentPage - 1) * pageSize;
  const visibleMeets = filteredMeets.slice(startIndex, startIndex + pageSize);
  const hasFilters = Boolean(search || fromDate || toDate);

  function clearFilters() {
    setSearch("");
    setFromDate("");
    setToDate("");
    setPage(1);
  }

  async function handleSave(request: MeetRequest) {
    if (activeDialog?.type === "edit") {
      const updated = await updateMeet(activeDialog.meet.id, request);
      setMeets((current) => current.map((meet) => meet.id === updated.id ? updated : meet));
      setFeedback(`${updated.name} was updated.`);
    } else {
      const created = await createMeet(request);
      setMeets((current) => [...current, created]);
      clearFilters();
      setFeedback(`${created.name} was created.`);
    }
    setActiveDialog(null);
  }

  async function handleDelete() {
    if (activeDialog?.type !== "delete" || isDeleting) return;
    const meet = activeDialog.meet;
    setIsDeleting(true);
    setDeleteError("");
    try {
      await deleteMeet(meet.id);
      setMeets((current) => current.filter((item) => item.id !== meet.id));
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
        <div className="meets-heading">
          <h1>Meets</h1>
          <p>View, create, and manage Flying Angels meets.</p>
        </div>

        <p className="meets-feedback" role="status">{feedback}</p>

        <section className="meets-panel" aria-label="Meets list">
          <div className="meets-toolbar">
            <input
              className="meets-search"
              type="search"
              aria-label="Search meets"
              placeholder="Search by meet name or location..."
              value={search}
              onChange={(event) => { setSearch(event.target.value); setPage(1); }}
            />
            <button
              className="meets-button meets-button-primary"
              type="button"
              disabled={isLoading}
              onClick={() => { setFeedback(""); setActiveDialog({ type: "create" }); }}
            >
              + New Meet
            </button>
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
                onChange={(event) => { setFromDate(event.target.value); setPage(1); }}
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
                onChange={(event) => { setToDate(event.target.value); setPage(1); }}
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
                onClick={() => {
                  setLoadError("");
                  setIsLoading(true);
                  setLoadVersion((version) => version + 1);
                }}
              >
                Try again
              </button>
            </div>
          ) : (
            <>
              <div className="meets-table-scroll" role="region" aria-label="Meets table" tabIndex={0}>
                <table className="meets-table">
                  <caption className="meets-sr-only">Flying Angels meets, most recent first</caption>
                  <thead>
                    <tr>
                      <th scope="col">Meet Name</th>
                      <th scope="col">Date</th>
                      <th scope="col">Location</th>
                      <th scope="col">Events</th>
                      <th scope="col">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleMeets.map((meet) => (
                      <tr key={meet.id}>
                        <th scope="row"><Link className="meets-name-link" to={`/meets/${meet.id}`}>{meet.name}</Link></th>
                        <td><time dateTime={meet.meetDate}>{formatDate(meet.meetDate)}</time></td>
                        <td>{meet.location || "—"}</td>
                        <td>{meet.eventCount}</td>
                        <td>
                          <div className="meets-row-actions">
                            <Link className="meets-action meets-view-link" to={`/meets/${meet.id}`} aria-label={`View ${meet.name}`}>View</Link>
                            <button
                              className="meets-action"
                              type="button"
                              aria-label={`Edit ${meet.name}`}
                              onClick={() => { setFeedback(""); setActiveDialog({ type: "edit", meet }); }}
                            >
                              Edit
                            </button>
                            <button
                              className="meets-action meets-action-delete"
                              type="button"
                              aria-label={`Delete ${meet.name}`}
                              onClick={() => {
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
                        <td colSpan={5} className="meets-empty">
                          <p>{hasFilters ? "No meets match your search or date range." : "No meets yet. Create your first meet to get started."}</p>
                          {!hasFilters && <Link className="meets-sample-link" to="/meets/demo">Preview a sample meet</Link>}
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
                    onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1); }}
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
                    onClick={() => setPage(currentPage - 1)}
                  >
                    &lsaquo;
                  </button>
                  <span>Page {currentPage} of {totalPages}</span>
                  <button
                    className="meets-button"
                    type="button"
                    aria-label="Next page"
                    disabled={currentPage === totalPages}
                    onClick={() => setPage(currentPage + 1)}
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
          onClose={() => setActiveDialog(null)}
        />
      )}
      {activeDialog?.type === "delete" && (
        <MeetDialog title="Delete Meet" busy={isDeleting} onClose={() => setActiveDialog(null)}>
          <p className="meet-dialog-message">
            Delete <strong>{activeDialog.meet.name}</strong>? This cannot be undone.
            {activeDialog.meet.eventCount > 0 && " Its events and their results will also be deleted."}
          </p>
          {deleteError && <p className="meet-dialog-error" role="alert">{deleteError}</p>}
          <div className="meet-dialog-actions">
            <button className="meets-button" type="button" autoFocus disabled={isDeleting} onClick={() => setActiveDialog(null)}>
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
