import { useEffect, useState } from "react";
import { getAthletes } from "../services/athleteService";
import { saveBrowserAssignment } from "../services/eventAssignmentStore";
import type { Athlete } from "../types/athlete";
import type { BrowserEventAssignment, EventParticipant, MeetEvent } from "../types/event";
import MeetDialog from "./MeetDialog";

type Props = {
  event: MeetEvent;
  assignedIds: number[];
  onAssigned: (results: EventParticipant[]) => void;
  onClose: () => void;
};

export default function AssignAthletesDialog({ event, assignedIds, onAssigned, onClose }: Props) {
  const [athletes, setAthletes] = useState<Athlete[]>([]);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<number[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [revision, setRevision] = useState(0);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  useEffect(() => {
    let ignore = false;
    getAthletes().then((data) => {
      if (!ignore) setAthletes(data);
    }).catch((error: unknown) => {
      if (!ignore) setLoadError(error instanceof Error ? error.message : "Unable to load athletes.");
    }).finally(() => {
      if (!ignore) setIsLoading(false);
    });
    return () => { ignore = true; };
  }, [revision]);

  const available = athletes.filter((athlete) => !assignedIds.includes(athlete.id));
  const visible = available.filter((athlete) =>
    `${athlete.firstName} ${athlete.lastName}`.toLowerCase().includes(search.trim().toLowerCase())
  );
  const selectedAthletes = available.filter((athlete) => selected.includes(athlete.id));

  function saveSelected() {
    if (isSaving || selectedAthletes.length === 0) return;
    setIsSaving(true);
    setSaveError("");
    const assigned: BrowserEventAssignment[] = [];
    const failures: string[] = [];
    for (const athlete of selectedAthletes) {
      try {
        assigned.push(saveBrowserAssignment(event, athlete));
      } catch (error) {
        failures.push(`${athlete.firstName} ${athlete.lastName}: ${error instanceof Error ? error.message : "Could not assign athlete."}`);
      }
    }
    if (assigned.length) onAssigned(assigned);
    setSelected((current) => current.filter((id) => !assigned.some((result) => result.athleteId === id)));
    setIsSaving(false);
    if (failures.length) {
      setSaveError(`${assigned.length} of ${selectedAthletes.length} athletes saved in this browser. ${failures.join(" ")}`);
    } else {
      onClose();
    }
  }

  return (
    <MeetDialog title={`Assign Athletes to ${event.name}`} busy={isSaving} onClose={onClose}>
      <div className="meet-form">
        <p className="meet-dialog-message">Select athletes to add. Athletes already assigned to this event are excluded.</p>
        <p className="meet-notes-notice">Assignments without results are saved only in this browser. Record an athlete's first result to save them to the shared meet. Teammates cannot see browser-only assignments, and clearing browser data removes them.</p>
        <label className="meet-form-field">
          Search athletes
          <input type="search" placeholder="Search athletes..." value={search} disabled={isSaving} onChange={(change) => setSearch(change.target.value)} />
        </label>
        {isLoading ? <p role="status">Loading athletes...</p> : loadError ? (
          <div>
            <p className="meet-dialog-error" role="alert">{loadError}</p>
            <button className="meets-button" type="button" onClick={() => { setLoadError(""); setIsLoading(true); setRevision((value) => value + 1); }}>Try again</button>
          </div>
        ) : (
          <fieldset className="meet-choice-list" disabled={isSaving}>
            <legend className="meets-sr-only">Available athletes</legend>
            {visible.map((athlete) => (
              <label className="meet-choice" key={athlete.id}>
                <input
                  type="checkbox"
                  checked={selected.includes(athlete.id)}
                  onChange={(change) => setSelected((current) => change.target.checked ? [...current, athlete.id] : current.filter((id) => id !== athlete.id))}
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
