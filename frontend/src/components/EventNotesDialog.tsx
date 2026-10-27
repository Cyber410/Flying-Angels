import { useId, useState } from "react";
import type { FormEvent } from "react";
import type { EventParticipant, MeetEvent } from "../types/event";
import { getEventNote, saveEventNote } from "../services/eventNotesStore";
import MeetDialog from "./MeetDialog";

type Props = {
  event: MeetEvent;
  participant?: EventParticipant;
  sample: boolean;
  onSaved: () => void;
  onClose: () => void;
};

export default function EventNotesDialog({ event, participant, sample, onSaved, onClose }: Props) {
  const id = useId();
  const [initial] = useState(() => {
    try {
      return { text: getEventNote(event.id, participant?.athleteId, sample), error: "" };
    } catch {
      return { text: "", error: "This browser is blocking note storage. Existing notes could not be loaded." };
    }
  });
  const [note, setNote] = useState(initial.text);
  const [error, setError] = useState(initial.error);

  function saveNote(submit: FormEvent<HTMLFormElement>) {
    submit.preventDefault();
    try {
      saveEventNote(event.id, note, participant?.athleteId, sample);
      onSaved();
      onClose();
    } catch {
      setError("The note could not be saved. Check that browser storage is enabled and try again.");
    }
  }

  return (
    <MeetDialog title={`Notes — ${event.name}`} onClose={onClose}>
      <form className="meet-form" onSubmit={saveNote}>
        {participant && <p className="meet-dialog-message">{participant.firstName} {participant.lastName}</p>}
        <p className="meet-notes-notice">Saved only in this browser. Notes are not shared with teammates or saved to the academy database. Clearing browser data removes them.</p>
        <label className="meet-form-field" htmlFor={id}>
          Note
          <textarea id={id} rows={6} value={note} autoFocus onChange={(change) => setNote(change.target.value)} />
        </label>
        <p className="meet-form-help">Clear the note and save to remove it from this browser.</p>
        {error && <p className="meet-dialog-error" role="alert">{error}</p>}
        <div className="meet-dialog-actions">
          <button className="meets-button" type="button" onClick={onClose}>Cancel</button>
          <button className="meets-button meets-button-primary" type="submit">Save Note</button>
        </div>
      </form>
    </MeetDialog>
  );
}
