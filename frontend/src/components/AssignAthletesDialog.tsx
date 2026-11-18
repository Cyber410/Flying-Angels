// Selects active athletes and persists shared assignments before results are recorded.
import { useEffect, useState } from "react";
import { getAthletes } from "../services/athleteService";
import { assignEventAthletes } from "../services/eventService";
import type { Athlete } from "../types/athlete";
import type { EventParticipant, MeetEvent } from "../types/event";
import MeetDialog from "./MeetDialog";

type Props = {
  event: MeetEvent;
  assignedIds: number[];
  onAssigned: (results: EventParticipant[]) => void;
  onClose: () => void;
};

// Show eligible athletes and save the selected event assignments.
export default function AssignAthletesDialog({ event, assignedIds, onAssigned, onClose }: Props) {
  const [athletes, setAthletes] = useState<Athlete[]>([]);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<number[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [revision, setRevision] = useState(0);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  // Only active athletes are offered for new assignments.
  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    let ignore = false;
    getAthletes().then(/* Apply the successful request result to the current operation. */ (data) => {
      if (!ignore) setAthletes(data);
    }).catch(/* Report the request failure without discarding the current view. */ (error: unknown) => {
      if (!ignore) setLoadError(error instanceof Error ? error.message : "Unable to load athletes.");
    }).finally(/* Clear the busy state after the request completes. */ () => {
      if (!ignore) setIsLoading(false);
    });
    return /* Stop pending work and release this effect when it is cleaned up. */ () => { ignore = true; };
  }, [revision]);

  const available = athletes.filter(/* Offer active athletes who are not already assigned to this event. */ (athlete) => !athlete.archived && !assignedIds.includes(athlete.id));
  const visible = available.filter(/* Keep entries matching the current search text. */ (athlete) =>
    `${athlete.firstName} ${athlete.lastName}`.toLowerCase().includes(search.trim().toLowerCase())
  );
  const selectedAthletes = available.filter(/* Keep the records included in the current selection. */ (athlete) => selected.includes(athlete.id));

  // Persist the entire selection atomically before closing the dialog.
  async function saveSelected() {
    if (isSaving || selectedAthletes.length === 0) return;
    setIsSaving(true); setSaveError("");
    try {
      const assigned = await assignEventAthletes(event.id, selectedAthletes.map(/* Extract each athlete ID for lookup or selection. */ athlete => athlete.id));
      onAssigned(assigned.map(/* Convert each participant into the required display or request data. */ participant => ({ ...participant, resultId: null, result: null, score: null, attempts: [] })));
      onClose();
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "Unable to assign athletes.");
    } finally { setIsSaving(false); }
  }

  return (
    <MeetDialog title={`Assign Athletes to ${event.name}`} busy={isSaving} onClose={onClose}>
      <div className="meet-form">
        <p className="meet-dialog-message">Select athletes to add. Athletes already assigned to this event are excluded.</p>
        <p className="meet-form-help">Assignments are saved to the academy database immediately. Results can be added later.</p>
        <label className="meet-form-field">
          Search athletes
          <input type="search" placeholder="Search athletes..." value={search} disabled={isSaving} onChange={/* Update the search value from this control. */ (change) => setSearch(change.target.value)} />
        </label>
        {isLoading ? <p role="status">Loading athletes...</p> : loadError ? (
          <div>
            <p className="meet-dialog-error" role="alert">{loadError}</p>
            <button className="meets-button" type="button" onClick={/* Retry loading the data for this view. */ () => { setLoadError(""); setIsLoading(true); setRevision(/* Derive the next revision value from its current state. */ (value) => value + 1); }}>Try again</button>
          </div>
        ) : (
          <fieldset className="meet-choice-list" disabled={isSaving}>
            <legend className="meets-sr-only">Available athletes</legend>
            {visible.map(/* Render each athlete in the list. */ (athlete) => (
              <label className="meet-choice" key={athlete.id}>
                <input
                  type="checkbox"
                  checked={selected.includes(athlete.id)}
                  onChange={/* Update the selected value from this control. */ (change) => setSelected(/* Derive the next selected value from its current state. */ (current) => change.target.checked ? [...current, athlete.id] : current.filter(/* Exclude entries that do not belong in this result or selection. */ (id) => id !== athlete.id))}
                />
                <span>
                  {athlete.firstName} {athlete.lastName}
                  <small>Athlete #{athlete.id}{athlete.gender ? ` · ${athlete.gender}` : ""}</small>
                </span>
              </label>
            ))}
            {visible.length === 0 && <p className="meet-choice-empty">{search ? "No athletes match your search." : "No unassigned athletes are available. Add athletes in the Athlete Directory."}</p>}
          </fieldset>
        )}
        {saveError && <p className="meet-dialog-error" role="alert">{saveError}</p>}
        <div className="meet-dialog-actions">
          <button className="meets-button" type="button" disabled={isSaving} onClick={onClose}>Cancel</button>
          <button className="meets-button meets-button-primary" type="button" disabled={isSaving || isLoading || Boolean(loadError) || selectedAthletes.length === 0} onClick={saveSelected}>
            {isSaving ? "Assigning..." : `Assign Selected (${selectedAthletes.length})`}
          </button>
        </div>
      </div>
    </MeetDialog>
  );
}
