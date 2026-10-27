import { useEffect, useId, useState } from "react";
import { Link } from "react-router-dom";
import { getEventProfile, removeParticipant } from "../services/eventService";
import { getBrowserAssignments, removeBrowserAssignment } from "../services/eventAssignmentStore";
import { getResultAttempts, includeSharedResult, removeResultAttempts } from "../services/eventAttemptStore";
import { bestResultAttempt, parseEventResult } from "../utils/eventResult";
import { sampleEventResults } from "../data/sampleMeet";
import type { BrowserEventAssignment, EventParticipant, MeetEvent, ResultAttempt } from "../types/event";
import AssignAthletesDialog from "./AssignAthletesDialog";
import EventResultDialog from "./EventResultDialog";
import EventNotesDialog from "./EventNotesDialog";
import MeetDialog from "./MeetDialog";

type Props = {
  event: MeetEvent;
  sample: boolean;
  defaultOpen: boolean;
  onCountChanged: (eventId: number, count: number) => void;
};

type PanelDialog =
  | { type: "assign" }
  | { type: "result"; participant: EventParticipant; attempt?: ResultAttempt }
  | { type: "remove"; participant: EventParticipant }
  | { type: "notes"; participant?: EventParticipant }
  | null;

export default function MeetEventPanel({ event, sample, defaultOpen, onCountChanged }: Props) {
  const contentId = useId();
  const [isOpen, setIsOpen] = useState(defaultOpen);
  const [initialBrowserState] = useState<{ assignments: BrowserEventAssignment[]; error: string }>(() => {
    if (sample) return { assignments: [], error: "" };
    try {
      return { assignments: getBrowserAssignments(event), error: "" };
    } catch (error) {
      return { assignments: [], error: error instanceof Error ? error.message : "Browser-only assignments could not be loaded." };
    }
  });
  const [results, setResults] = useState<EventParticipant[] | null>(() => sample ? sampleEventResults[event.id] ?? [] : null);
  const [attemptsByAthlete, setAttemptsByAthlete] = useState<Record<number, ResultAttempt[]>>({});
  const [attemptStorageError, setAttemptStorageError] = useState("");
  const [browserError, setBrowserError] = useState(initialBrowserState.error);
  const [isLoading, setIsLoading] = useState(!sample);
  const [loadError, setLoadError] = useState("");
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<PanelDialog>(null);
  const [isRemoving, setIsRemoving] = useState(false);
  const [removeError, setRemoveError] = useState("");
  const [feedback, setFeedback] = useState("");

  useEffect(() => {
    if (sample || !isOpen) return;
    let ignore = false;
    getEventProfile(event.id).then((data) => {
      if (ignore) return;
      let pending: BrowserEventAssignment[] = [];
      let storageError = "";
      try {
        pending = getBrowserAssignments(data.event);
      } catch (error) {
        storageError = error instanceof Error ? error.message : "Browser-only assignments could not be loaded.";
      }
      // A saved result always takes precedence over a stale browser assignment.
      const sharedIds = new Set(data.results.map((result) => result.athleteId));
      const participants = [...data.results, ...pending.filter((assignment) => !sharedIds.has(assignment.athleteId))];
      const histories: Record<number, ResultAttempt[]> = {};
      let historyError = "";
      for (const participant of participants) {
        try {
          histories[participant.athleteId] = getResultAttempts(event.id, participant);
        } catch (error) {
          histories[participant.athleteId] = includeSharedResult([], participant);
          historyError = error instanceof Error ? error.message : "Attempt history could not be loaded.";
        }
      }
      setResults(participants);
      setAttemptsByAthlete(histories);
      setAttemptStorageError(historyError);
      setBrowserError(storageError);
    }).catch((error: unknown) => {
      if (!ignore) setLoadError(error instanceof Error ? error.message : "Unable to load participants.");
    }).finally(() => {
      if (!ignore) setIsLoading(false);
    });
    return () => { ignore = true; };
  }, [event.id, isOpen, revision, sample]);

  function addParticipants(assigned: EventParticipant[]) {
    const existingIds = new Set((results ?? []).map((result) => result.athleteId));
    const next = [...(results ?? []), ...assigned.filter((assignment) => !existingIds.has(assignment.athleteId))];
    setResults(next);
    onCountChanged(event.id, next.filter((result) => result.resultId !== null).length);
    setFeedback(`${assigned.length} athlete${assigned.length === 1 ? "" : "s"} assigned in this browser. Add a result to save to the shared meet.`);
  }

  async function confirmRemoval() {
    if (sample || dialog?.type !== "remove" || isRemoving) return;
    const participant = dialog.participant;
    setIsRemoving(true);
    setRemoveError("");
    try {
      let storageError = "";
      if (participant.resultId === null) {
        removeBrowserAssignment(event.id, participant.athleteId);
      } else {
        await removeParticipant(event.id, participant.resultId);
        try {
          removeBrowserAssignment(event.id, participant.athleteId);
        } catch {
          storageError = "The athlete was removed from the shared event, but browser-only assignment data could not be cleared.";
        }
      }
      const next = (results ?? []).filter((result) => result.athleteId !== participant.athleteId);
      try {
        removeResultAttempts(event.id, participant.athleteId);
        setAttemptStorageError("");
      } catch {
        setAttemptStorageError("The athlete was removed, but their old attempt history could not be cleared from this browser.");
      }
      setResults(next);
      setAttemptsByAthlete((current) => {
        const remaining = { ...current };
        delete remaining[participant.athleteId];
        return remaining;
      });
      setBrowserError(storageError);
      onCountChanged(event.id, next.filter((result) => result.resultId !== null).length);
      setFeedback(`${participant.firstName} ${participant.lastName} was removed from this event.`);
      setDialog(null);
    } catch (error) {
      setRemoveError(error instanceof Error ? error.message : "Unable to remove athlete.");
    } finally {
      setIsRemoving(false);
    }
  }

  const orderedResults = (results ?? []).toSorted((a, b) => {
    if (a.score === null && b.score !== null) return 1;
    if (b.score === null && a.score !== null) return -1;
    const scoreDifference = (a.score ?? 0) - (b.score ?? 0);
    return (event.rankingOrder === "ASC" ? scoreDifference : -scoreDifference) ||
      a.lastName.localeCompare(b.lastName) || a.firstName.localeCompare(b.firstName);
  });
  const pendingCount = results
    ? results.filter((result) => result.resultId === null).length
    : initialBrowserState.assignments.length;
  const count = results?.length ?? event.participantCount + pendingCount;

  return (
    <section className="meet-event-panel" aria-label={event.name}>
      <button
        className="meet-event-toggle"
        type="button"
        aria-expanded={isOpen}
        aria-controls={contentId}
        onClick={() => {
          if (!isOpen && !sample) { setIsLoading(true); setLoadError(""); }
          setIsOpen((open) => !open);
        }}
      >
        <span className="meet-event-chevron" aria-hidden="true">{isOpen ? "⌄" : "›"}</span>
        <span>{event.name}</span>
        <span className="meet-event-count">{count} athlete{count === 1 ? "" : "s"}{pendingCount > 0 && ` (${pendingCount} browser-only)`}</span>
      </button>
      <div id={contentId} hidden={!isOpen}>
        {isOpen && (
          <>
            <div className="meet-event-toolbar">
              <p>{event.rankingOrder === "ASC" ? "Lowest result ranks first" : "Highest result ranks first"}</p>
              <button className="meets-button" type="button" onClick={() => setDialog({ type: "notes" })}>Event Notes</button>
              <button className="meets-button meets-button-primary" type="button" disabled={sample || isLoading || Boolean(loadError)} onClick={() => setDialog({ type: "assign" })}>+ Assign Athletes</button>
            </div>
            {!sample && pendingCount > 0 && (
              <p className="meet-pending-notice">Browser-only assignments are visible on this device until a result is recorded. They are not shared with teammates. Clearing browser data removes them.</p>
            )}
            {browserError && <p className="meet-pending-notice" role="alert">{browserError}</p>}
            {attemptStorageError && <p className="meet-pending-notice" role="alert">{attemptStorageError}</p>}
            {!sample && <p className="meet-attempts-notice">Attempt history is saved in this browser. Only each athlete's best result is shared with teammates. Click a result to edit it, or use + Result to add another.</p>}
            <p className="meet-event-feedback" role="status">{feedback}</p>
            {isLoading ? <p className="meets-state" role="status">Loading participants...</p> : loadError ? (
              <div className="meets-state">
                <p role="alert">{loadError}</p>
                <button className="meets-button" type="button" onClick={() => { setLoadError(""); setIsLoading(true); setRevision((value) => value + 1); }}>Try again</button>
              </div>
            ) : (
              <div className="meets-table-scroll" role="region" aria-label={`${event.name} participants`} tabIndex={0}>
                <table className="meets-table meet-participants-table">
                  <caption className="meets-sr-only">Athletes and results for {event.name}</caption>
                  <thead><tr><th scope="col">Athlete</th><th scope="col">Results</th><th scope="col">Actions</th></tr></thead>
                  <tbody>
                    {orderedResults.map((participant) => {
                      const fullName = `${participant.firstName} ${participant.lastName}`;
                      const attempts = attemptsByAthlete[participant.athleteId] ?? includeSharedResult([], participant);
                      const bestAttempt = bestResultAttempt(attempts, event.rankingOrder);
                      return (
                        <tr key={participant.athleteId}>
                          <th scope="row">
                            {sample ? fullName : <Link to={`/athletes/${participant.athleteId}`}>{fullName}</Link>}
                            {participant.resultId === null && <small className="meet-pending-label">Browser only</small>}
                          </th>
                          <td>
                            <div className="meet-result-list">
                              {attempts.map((attempt, index) => {
                                const display = parseEventResult(attempt.result, event)?.result ?? attempt.result;
                                const isBest = attempts.length > 1 && attempt.id === bestAttempt?.id;
                                return sample ? (
                                  <span className="meet-result-value" key={attempt.id}>{display}</span>
                                ) : (
                                  <button
                                    className={`meet-result-value meet-result-chip${isBest ? " meet-result-best" : ""}`}
                                    key={attempt.id}
                                    type="button"
                                    aria-label={`Edit result ${index + 1} for ${fullName}: ${display}${isBest ? ", best result" : ""}`}
                                    onClick={() => setDialog({ type: "result", participant, attempt })}
                                  >
                                    {display}{isBest && <span className="meet-result-best-label">Best</span>}
                                  </button>
                                );
                              })}
                              {attempts.length === 0 && <span className="meet-no-result">Not recorded</span>}
                            </div>
                          </td>
                          <td>
                            <div className="meets-row-actions">
                              <button className="meets-action" type="button" disabled={sample} aria-label={`Add result for ${fullName}`} onClick={() => setDialog({ type: "result", participant })}>+ Result</button>
                              <button className="meets-action" type="button" aria-label={`Notes for ${fullName}`} onClick={() => setDialog({ type: "notes", participant })}>Notes</button>
                              <button className="meets-action meets-action-delete" type="button" disabled={sample} aria-label={`Remove ${fullName} from ${event.name}`} onClick={() => { setRemoveError(""); setDialog({ type: "remove", participant }); }}>Remove</button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                    {orderedResults.length === 0 && <tr><td colSpan={3} className="meets-empty">No athletes assigned yet. Use + Assign Athletes to get started.</td></tr>}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </div>

      {dialog?.type === "assign" && !sample && (
        <AssignAthletesDialog event={event} assignedIds={(results ?? []).map((result) => result.athleteId)} onAssigned={addParticipants} onClose={() => setDialog(null)} />
      )}
      {dialog?.type === "result" && !sample && (
        <EventResultDialog
          event={event}
          participant={dialog.participant}
          attempts={attemptsByAthlete[dialog.participant.athleteId] ?? includeSharedResult([], dialog.participant)}
          editingAttempt={dialog.attempt}
          onSaved={(saved, attempts, warning) => {
            const next = (results ?? []).map((result) => result.athleteId === saved.athleteId ? saved : result);
            setResults(next);
            setAttemptsByAthlete((current) => ({ ...current, [saved.athleteId]: attempts }));
            setAttemptStorageError(warning ?? "");
            onCountChanged(event.id, next.filter((result) => result.resultId !== null).length);
            try {
              removeBrowserAssignment(event.id, saved.athleteId);
              setBrowserError("");
            } catch {
              // The API save succeeded: keep its result even if local cleanup fails.
              setBrowserError("The result is saved to the shared meet, but the old browser-only assignment could not be cleared. The shared result takes priority.");
            }
            setFeedback(`Result saved for ${saved.firstName} ${saved.lastName}. Their best result is shared with teammates.`);
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.type === "notes" && (
        <EventNotesDialog event={event} participant={dialog.participant} sample={sample} onSaved={() => setFeedback("Note saved in this browser.")} onClose={() => setDialog(null)} />
      )}
      {dialog?.type === "remove" && (
        <MeetDialog title="Remove Athlete from Event" busy={isRemoving} onClose={() => setDialog(null)}>
          <p className="meet-dialog-message">
            Remove <strong>{dialog.participant.firstName} {dialog.participant.lastName}</strong> from {event.name}?
            {dialog.participant.resultId === null ? " This removes their browser-only assignment." : " Their shared result and this browser's attempt history for this event will also be deleted."}
            {" Their athlete profile will remain available."}
          </p>
          {removeError && <p className="meet-dialog-error" role="alert">{removeError}</p>}
          <div className="meet-dialog-actions">
            <button className="meets-button" type="button" autoFocus disabled={isRemoving} onClick={() => setDialog(null)}>Cancel</button>
            <button className="meets-button meets-button-danger" type="button" disabled={isRemoving} onClick={confirmRemoval}>{isRemoving ? "Removing..." : "Remove Athlete"}</button>
          </div>
        </MeetDialog>
      )}
    </section>
  );
}
