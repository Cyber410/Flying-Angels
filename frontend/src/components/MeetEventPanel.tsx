// Loads shared event participants and coordinates attempts, notes, and removal.
import { useEffect, useId, useState } from "react";
import { Link } from "react-router-dom";
import { getEventProfile, removeEventParticipant } from "../services/eventService";
import { eventParticipants, participantAttempts } from "../utils/eventParticipants";
import { bestResultAttempt, parseEventResult } from "../utils/eventResult";
import { sampleEventResults } from "../data/sampleMeet";
import type { EventParticipant, MeetEvent, ResultAttempt } from "../types/event";
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

// Display event participants and coordinate result and note actions.
export default function MeetEventPanel({ event, sample, defaultOpen, onCountChanged }: Props) {
  const contentId = useId();
  const [isOpen, setIsOpen] = useState(defaultOpen);
  const [results, setResults] = useState<EventParticipant[] | null>(/* Compute the initial state when this component mounts. */ () => sample ? sampleEventResults[event.id] ?? [] : null);
  const [attemptsByAthlete, setAttemptsByAthlete] = useState<Record<number, ResultAttempt[]>>({});
  const [isLoading, setIsLoading] = useState(!sample);
  const [loadError, setLoadError] = useState("");
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<PanelDialog>(null);
  const [isRemoving, setIsRemoving] = useState(false);
  const [removeError, setRemoveError] = useState("");
  const [feedback, setFeedback] = useState("");

  // Load all saved participants, including assignments without results.
  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    if (sample || !isOpen) return;
    let ignore = false;
    getEventProfile(event.id).then(/* Apply the successful request result to the current operation. */ (data) => {
      if (ignore) return;
      const participants = eventParticipants(data);
      setResults(participants);
      setAttemptsByAthlete(Object.fromEntries(participants.map(/* Convert each participant into the required display or request data. */ participant => [participant.athleteId, participantAttempts(participant)])));
    }).catch(/* Report the request failure without discarding the current view. */ (error: unknown) => {
      if (!ignore) setLoadError(error instanceof Error ? error.message : "Unable to load participants.");
    }).finally(/* Clear the busy state after the request completes. */ () => {
      if (!ignore) setIsLoading(false);
    });
    return /* Stop pending work and release this effect when it is cleaned up. */ () => { ignore = true; };
  }, [event.id, isOpen, revision, sample]);

  // Merges newly assigned athletes without duplicating existing participants.
  function addParticipants(assigned: EventParticipant[]) {
    const existingIds = new Set((results ?? []).map(/* Build existing ids from each result. */ (result) => result.athleteId));
    const next = [...(results ?? []), ...assigned.filter(/* Apply the search and selection criteria for next. */ (assignment) => !existingIds.has(assignment.athleteId))];
    setResults(next);
    onCountChanged(event.id, next.length);
    setFeedback(`${assigned.length} athlete${assigned.length === 1 ? "" : "s"} assigned to this event. Results can be recorded later.`);
  }

  // Remove the participation and its attempts through one backend transaction.
  async function confirmRemoval() {
    if (sample || dialog?.type !== "remove" || isRemoving) return;
    const participant = dialog.participant;
    setIsRemoving(true);
    setRemoveError("");
    try {
      await removeEventParticipant(event.id, participant.athleteId);
      const next = (results ?? []).filter(/* Exclude entries that do not belong in this result or selection. */ result => result.athleteId !== participant.athleteId);
      setResults(next);
      setAttemptsByAthlete(/* Derive the next attempts by athlete value from its current state. */ current => { const remaining = { ...current }; delete remaining[participant.athleteId]; return remaining; });
      onCountChanged(event.id, next.length);
      setFeedback(`${participant.firstName} ${participant.lastName} was removed from this event.`);
      setDialog(null);
    } catch (error) {
      setRemoveError(error instanceof Error ? error.message : "Unable to remove athlete.");
    } finally {
      setIsRemoving(false);
    }
  }

  const orderedResults = (results ?? []).toSorted(/* Order entries using the displayed date, name, or performance values. */ (a, b) => {
    if (a.score === null && b.score !== null) return 1;
    if (b.score === null && a.score !== null) return -1;
    const scoreDifference = (a.score ?? 0) - (b.score ?? 0);
    return (event.rankingOrder === "ASC" ? scoreDifference : -scoreDifference) ||
      a.lastName.localeCompare(b.lastName) || a.firstName.localeCompare(b.firstName);
  });
  const pendingCount = (results ?? []).filter(/* Identify assignments that still need a result. */ result => result.score === null).length;
  const count = results?.length ?? event.participantCount;

  return (
    <section className="meet-event-panel" aria-label={event.name}>
      <button
        className="meet-event-toggle"
        type="button"
        aria-expanded={isOpen}
        aria-controls={contentId}
        onClick={/* Update is loading for the selected action. */ () => {
          if (!isOpen && !sample) { setIsLoading(true); setLoadError(""); }
          setIsOpen(/* Derive the next is open value from its current state. */ (open) => !open);
        }}
      >
        <span className="meet-event-chevron" aria-hidden="true">{isOpen ? "⌄" : "›"}</span>
        <span>{event.name}</span>
        <span className="meet-event-count">{count} athlete{count === 1 ? "" : "s"}{pendingCount > 0 && ` (${pendingCount} awaiting results)`}</span>
      </button>
      <div id={contentId} hidden={!isOpen}>
        {isOpen && (
          <>
            <div className="meet-event-toolbar">
              <p>{event.rankingOrder === "ASC" ? "Lowest result ranks first" : "Highest result ranks first"}</p>
              <button className="meets-button" type="button" onClick={/* Open or close the requested event action dialog. */ () => setDialog({ type: "notes" })}>Event Notes</button>
              <button className="meets-button meets-button-primary" type="button" disabled={sample || isLoading || Boolean(loadError)} onClick={/* Open or close the requested event action dialog. */ () => setDialog({ type: "assign" })}>+ Assign Athletes</button>
            </div>
            {!sample && <p className="meet-attempts-notice">Assignments, notes, and every result attempt are saved to the academy database. Click a result to edit it, or use + Result to add another.</p>}
            <p className="meet-event-feedback" role="status">{feedback}</p>
            {isLoading ? <p className="meets-state" role="status">Loading participants...</p> : loadError ? (
              <div className="meets-state">
                <p role="alert">{loadError}</p>
                <button className="meets-button" type="button" onClick={/* Retry loading the data for this view. */ () => { setLoadError(""); setIsLoading(true); setRevision(/* Derive the next revision value from its current state. */ (value) => value + 1); }}>Try again</button>
              </div>
            ) : (
              <div className="meets-table-scroll" role="region" aria-label={`${event.name} participants`} tabIndex={0}>
                <table className="meets-table meet-participants-table">
                  <caption className="meets-sr-only">Athletes and results for {event.name}</caption>
                  <thead><tr><th scope="col">Athlete</th><th scope="col">Results</th><th scope="col">Actions</th></tr></thead>
                  <tbody>
                    {orderedResults.map(/* Render each participant in the list. */ (participant) => {
                      const fullName = `${participant.firstName} ${participant.lastName}`;
                      const attempts = attemptsByAthlete[participant.athleteId] ?? participantAttempts(participant);
                      const bestAttempt = bestResultAttempt(attempts, event.rankingOrder);
                      return (
                        <tr key={participant.athleteId}>
                          <th scope="row">
                            {sample ? fullName : <Link to={`/athletes/${participant.athleteId}`}>{fullName}</Link>}
                            {participant.resultId === null && <small className="meet-pending-label">Awaiting result</small>}
                          </th>
                          <td>
                            <div className="meet-result-list">
                              {attempts.map(/* Render each attempt in the list. */ (attempt, index) => {
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
                                    onClick={/* Open or close the requested event action dialog. */ () => setDialog({ type: "result", participant, attempt })}
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
                              <button className="meets-action" type="button" disabled={sample} aria-label={`Add result for ${fullName}`} onClick={/* Open or close the requested event action dialog. */ () => setDialog({ type: "result", participant })}>+ Result</button>
                              <button className="meets-action" type="button" aria-label={`Notes for ${fullName}`} onClick={/* Open or close the requested event action dialog. */ () => setDialog({ type: "notes", participant })}>Notes</button>
                              <button className="meets-action meets-action-delete" type="button" disabled={sample} aria-label={`Remove ${fullName} from ${event.name}`} onClick={/* Open or close the requested event action dialog. */ () => { setRemoveError(""); setDialog({ type: "remove", participant }); }}>Remove</button>
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
        <AssignAthletesDialog event={event} assignedIds={(results ?? []).map(/* Convert each result into the required display or request data. */ (result) => result.athleteId)} onAssigned={addParticipants} onClose={/* Close the dialog and clear its visible state. */ () => setDialog(null)} />
      )}
      {dialog?.type === "result" && !sample && (
        <EventResultDialog
          event={event}
          participant={dialog.participant}
          attempts={attemptsByAthlete[dialog.participant.athleteId] ?? participantAttempts(dialog.participant)}
          editingAttempt={dialog.attempt}
          onSaved={/* Merge the saved data into the parent view. */ (saved, attempts) => {
            const next = (results ?? []).map(/* Build next from each result. */ (result) => result.athleteId === saved.athleteId ? { ...result, ...saved, attempts } : result);
            setResults(next);
            setAttemptsByAthlete(/* Derive the next attempts by athlete value from its current state. */ (current) => ({ ...current, [saved.athleteId]: attempts }));
            onCountChanged(event.id, next.filter(/* Exclude entries that do not belong in this result or selection. */ (result) => result.resultId !== null).length);
            setFeedback(`Result saved for ${saved.firstName} ${saved.lastName}. All attempts are saved and shared with your team.`);
          }}
          onClose={/* Close the dialog and clear its visible state. */ () => setDialog(null)}
        />
      )}
      {dialog?.type === "notes" && (
        <EventNotesDialog event={event} participant={dialog.participant} sample={sample} onSaved={/* Merge the saved data into the parent view. */ () => setFeedback("Note saved to the academy database.")} onClose={/* Close the dialog and clear its visible state. */ () => setDialog(null)} />
      )}
      {dialog?.type === "remove" && (
        <MeetDialog title="Remove Athlete from Event" busy={isRemoving} onClose={/* Close the dialog and clear its visible state. */ () => setDialog(null)}>
          <p className="meet-dialog-message">
            Remove <strong>{dialog.participant.firstName} {dialog.participant.lastName}</strong> from {event.name}?
            {" Their assignment, attempts, and performance note for this event will be deleted."}
            {" Their athlete profile will remain available."}
          </p>
          {removeError && <p className="meet-dialog-error" role="alert">{removeError}</p>}
          <div className="meet-dialog-actions">
            <button className="meets-button" type="button" autoFocus disabled={isRemoving} onClick={/* Open or close the requested event action dialog. */ () => setDialog(null)}>Cancel</button>
            <button className="meets-button meets-button-danger" type="button" disabled={isRemoving} onClick={confirmRemoval}>{isRemoving ? "Removing..." : "Remove Athlete"}</button>
          </div>
        </MeetDialog>
      )}
    </section>
  );
}
