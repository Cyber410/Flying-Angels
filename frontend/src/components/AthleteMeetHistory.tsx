import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { getAthleteMeetHistory } from "../services/athleteHistoryService";
import { getEventNote } from "../services/eventNotesStore";
import { bestResultAttempt, parseEventResult } from "../utils/eventResult";
import type { Athlete } from "../types/athlete";
import type { AthleteHistoryEvent, AthleteMeetHistory as History } from "../types/athleteHistory";
import EventNotesDialog from "./EventNotesDialog";
import "./AthleteMeetHistory.css";

type Props = {
  athlete: Athlete;
  readOnly?: boolean;
  view: "history" | "notes" | "hidden";
  generalNote: ReactNode;
  onShowHistory: () => void;
};

const dateFormatter = new Intl.DateTimeFormat("en-CA", { month: "short", day: "numeric", year: "numeric" });
function displayDate(date: string) {
  return dateFormatter.format(new Date(`${date}T00:00:00`));
}

export default function AthleteMeetHistory({ athlete, readOnly = false, view, generalNote, onShowHistory }: Props) {
  const [history, setHistory] = useState<History | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [activeNote, setActiveNote] = useState<AthleteHistoryEvent | null>(null);
  const [feedback, setFeedback] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    let ignore = false;
    getAthleteMeetHistory(athlete.id, controller.signal).then((data) => {
      if (!ignore) setHistory(data);
    }).catch((error: unknown) => {
      if (!ignore) setError(error instanceof Error ? error.message : "Unable to load meet history.");
    }).finally(() => {
      if (!ignore) setIsLoading(false);
    });
    return () => { ignore = true; controller.abort(); };
  }, [athlete.id, revision]);

  const notes = (history?.meets ?? []).flatMap((meet) =>
    meet.events.filter((item) => item.note.trim()).map((item) => ({ meet, item }))
  );
  const eventCount = (history?.meets ?? []).reduce((count, meet) => count + meet.events.length, 0);
  const linkedResultIds = new Set((history?.meets ?? []).flatMap((meet) =>
    meet.events.map((item) => item.participant.resultId).filter((id): id is number => id !== null)
  ));
  const otherResults = (athlete.events ?? []).filter((result) => result.resultId == null || !linkedResultIds.has(result.resultId));

  function refresh() {
    setError("");
    setIsLoading(true);
    setRevision((value) => value + 1);
  }

  function updateNotePreview() {
    if (!activeNote) return;
    try {
      const note = getEventNote(activeNote.event.id, athlete.id);
      setHistory((current) => current ? {
        ...current,
        meets: current.meets.map((meet) => ({
          ...meet,
          events: meet.events.map((item) => item.event.id === activeNote.event.id ? { ...item, note } : item),
        })),
      } : current);
      setFeedback("Performance note saved in this browser.");
    } catch {
      setFeedback("The note was saved, but its preview could not be refreshed. Refresh the history to try again.");
    }
  }

  return (
    <>
      <section id="profile-panel-history" role="tabpanel" className="profile-tab-content ath-history-section" aria-labelledby="profile-tab-history" aria-busy={isLoading} hidden={view !== "history"} tabIndex={0}>
        <div className="ath-history-heading">
          <div className="ath-history-title">
            <h2>Meet history</h2>
            {!isLoading && history && <span className="ath-history-count">{history.meets.length} meet{history.meets.length === 1 ? "" : "s"} · {eventCount} event{eventCount === 1 ? "" : "s"}</span>}
          </div>
          <button className="ath-history-button" type="button" disabled={isLoading} onClick={refresh}>Refresh</button>
        </div>
        <p className="ath-history-description">Expand a meet to view results and add a performance note.</p>
        {isLoading ? <p className="ath-history-state" role="status">Loading meet history...</p> : error ? (
          <div className="ath-history-state">
            <p role="alert">{error}</p>
            <button className="ath-history-button" type="button" onClick={refresh}>Try again</button>
          </div>
        ) : history && (
          <>
            {(history.warnings.length > 0 || history.failedEvents.length > 0) && (
              <div className="ath-history-warning" role="status">
                {history.warnings.map((warning) => <p key={warning}>{warning}</p>)}
                {history.failedEvents.length > 0 && (
                  <details>
                    <summary>{history.failedEvents.length} event{history.failedEvents.length === 1 ? " could" : "s could"} not be checked. This history may be incomplete.</summary>
                    <ul>{history.failedEvents.map((event) => <li key={event.eventId}>{event.eventName} · {event.meetName}: {event.message}</li>)}</ul>
                  </details>
                )}
              </div>
            )}
            {history.meets.map((meet, index) => (
              <details className="ath-history-meet" key={meet.id} open={index === 0}>
                <summary>
                  <span className="ath-history-chevron" aria-hidden="true">›</span>
                  <span className="ath-history-meet-name">{meet.name}</span>
                  <span className="ath-history-meet-date">{meet.dateIsFromEvent && "Latest event: "}<time dateTime={meet.date}>{displayDate(meet.date)}</time></span>
                  <span className="ath-history-meet-count">{meet.events.length} event{meet.events.length === 1 ? "" : "s"}</span>
                </summary>
                <div className="ath-history-meet-content">
                  <div className="ath-history-meet-context">
                    {meet.location && <span>{meet.location}</span>}
                    <Link to={`/meets/${meet.id}`}>Open meet →</Link>
                  </div>
                  <div className="ath-history-event-labels" aria-hidden="true"><span>Event</span><span>Results</span><span>Notes</span></div>
                  {meet.events.map((item) => {
                    const best = bestResultAttempt(item.attempts, item.event.rankingOrder);
                    return (
                      <div className="ath-history-event" key={item.event.id}>
                        <div className="ath-history-event-info">
                          <h3>{item.event.name}</h3>
                          {item.event.eventDate !== meet.date && <time dateTime={item.event.eventDate}>{displayDate(item.event.eventDate)}</time>}
                          {item.participant.resultId === null && <span className="ath-history-browser-label">Assignment: browser only</span>}
                        </div>
                        <div className="ath-history-results" aria-label={`${item.event.name} attempts`}>
                          {item.attempts.map((attempt) => (
                            <span className={`ath-history-result${item.attempts.length > 1 && best?.id === attempt.id ? " ath-history-best" : ""}`} key={attempt.id}>
                              {parseEventResult(attempt.result, item.event)?.result ?? attempt.result}
                              {item.attempts.length > 1 && best?.id === attempt.id && <small>Best</small>}
                            </span>
                          ))}
                          {item.attempts.length === 0 && <span className="ath-history-not-recorded">Result not recorded</span>}
                        </div>
                        <button className="ath-history-button" type="button" disabled={readOnly} aria-label={`Notes for ${athlete.firstName} ${athlete.lastName} in ${item.event.name} at ${meet.name}`} onClick={() => setActiveNote(item)}>{item.note.trim() ? "Edit Note" : "Add Note"}</button>
                        {item.note.trim() && <p className="ath-history-note-preview">{item.note}</p>}
                      </div>
                    );
                  })}
                </div>
              </details>
            ))}
            {history.meets.length === 0 && (
              <div className="ath-history-state">
                <p>{history.failedEvents.length ? "Meet participation could not be fully determined. Refresh to retry." : "No meet participation found for this athlete."}</p>
                {otherResults.length > 0 && <p>See Other recorded results below for performances without a confirmed meet.</p>}
                <Link to="/meets">Go to Meets</Link>
              </div>
            )}
          </>
        )}
        {!isLoading && otherResults.length > 0 && (
          <details className="profile-legacy-results">
            <summary>Other recorded results <span>{otherResults.length}</span></summary>
            <p>These records do not have a confirmed meet in this view.</p>
            <div className="ath-history-older-results" role="region" aria-label="Other athlete results" tabIndex={0}>
              <table>
                <thead><tr><th scope="col">Event</th><th scope="col">Result</th></tr></thead>
                <tbody>{otherResults.map((result, index) => <tr key={`${result.resultId ?? "legacy"}-${index}`}><th scope="row">{result.categoryName?.trim() || "Not provided"}</th><td>{result.result?.trim() || (result.score ?? "Not provided")}</td></tr>)}</tbody>
              </table>
            </div>
          </details>
        )}
        {view === "history" && <p className="ath-history-feedback" role="status">{feedback}</p>}
      </section>

      <section id="profile-panel-notes" role="tabpanel" className="profile-tab-content ath-history-notes-section" aria-labelledby="profile-tab-notes" hidden={view !== "notes"} tabIndex={0}>
        {generalNote}
        <section className="ath-performance-notes" aria-labelledby="ath-performance-notes-heading">
          <div className="ath-history-heading">
            <h2 id="ath-performance-notes-heading">Performance notes</h2>
            <span className="ath-history-count">{isLoading ? "Loading…" : `${notes.length} note${notes.length === 1 ? "" : "s"}`}</span>
          </div>
          <div className="ath-performance-notes-content">
            <p className="ath-history-description">Event-specific notes saved in this browser.</p>
            {isLoading ? <p className="ath-history-state">Loading performance notes...</p> : error ? (
              <p className="ath-history-state">Load the meet history to view performance notes.</p>
            ) : notes.length ? notes.map(({ meet, item }) => (
              <article className="ath-performance-note" key={item.event.id}>
                <div><h3>{item.event.name}</h3><Link to={`/meets/${meet.id}`}>{meet.name}</Link></div>
                <p>{item.note}</p>
                <button className="ath-history-button" type="button" disabled={readOnly} aria-label={`Edit note for ${item.event.name} at ${meet.name}`} onClick={() => setActiveNote(item)}>Edit Note</button>
              </article>
            )) : <div className="ath-history-state"><p>No performance notes yet.</p><button className="ath-history-button" type="button" onClick={onShowHistory}>Find an event to add a note</button></div>}
          </div>
        </section>
        {view === "notes" && <p className="ath-history-feedback" role="status">{feedback}</p>}
      </section>

      {activeNote && <EventNotesDialog event={activeNote.event} participant={activeNote.participant} sample={false} onSaved={updateNotePreview} onClose={() => setActiveNote(null)} />}
    </>
  );
}
