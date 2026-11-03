// Load and save event or athlete-performance notes through the shared API.
import { useEffect, useId, useState } from "react";
import type { FormEvent } from "react";
import type { EventParticipant, MeetEvent } from "../types/event";
import { readEventNote, writeEventNote } from "../services/eventService";
import MeetDialog from "./MeetDialog";

type Props = {
  event: MeetEvent; participant?: EventParticipant; sample: boolean;
  onSaved: (text: string) => void; onClose: () => void;
};

export default function EventNotesDialog({ event, participant, sample, onSaved, onClose }: Props) {
  const id = useId();
  const [note, setNote] = useState("");
  const [isLoading, setIsLoading] = useState(!sample);
  const [isSaving, setIsSaving] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [saveError, setSaveError] = useState("");
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    if (sample) return;
    let ignore = false;
    readEventNote(event.id, participant?.athleteId).then(text => { if (!ignore) setNote(text); })
      .catch(error => { if (!ignore) setLoadError(error instanceof Error ? error.message : "Unable to load note."); })
      .finally(() => { if (!ignore) setIsLoading(false); });
    return () => { ignore = true; };
  }, [event.id, participant?.athleteId, sample, revision]);

  async function saveNote(submit: FormEvent<HTMLFormElement>) {
    submit.preventDefault();
    if (isSaving || isLoading || loadError || sample) return;
    setIsSaving(true); setSaveError("");
    try { const text = await writeEventNote(event.id, note.trim(), participant?.athleteId); onSaved(text); onClose(); }
    catch (error) { setSaveError(error instanceof Error ? error.message : "Unable to save note."); }
    finally { setIsSaving(false); }
  }

  return <MeetDialog title={`${participant ? "Performance note" : "Event note"} — ${event.name}`} busy={isSaving} onClose={onClose}>
    <form className="meet-form" onSubmit={saveNote}>
      {participant && <p className="meet-dialog-message">{participant.firstName} {participant.lastName}</p>}
      <p className="meet-form-help">{sample ? "This is a read-only sample." : "Notes are saved to the academy database and available to your team."}</p>
      {isLoading && <p role="status">Loading note…</p>}
      {loadError && <div><p className="meet-dialog-error" role="alert">{loadError}</p><button className="meets-button" type="button" onClick={() => { setLoadError(""); setIsLoading(true); setRevision(value => value + 1); }}>Try again</button></div>}
      <label className="meet-form-field" htmlFor={id}>Note<textarea id={id} rows={6} maxLength={2000} value={note} disabled={sample || isLoading || isSaving || Boolean(loadError)} onChange={change => setNote(change.target.value)} /></label>
      <p className="meet-form-help">Clear the note and save to remove it.</p>
      {saveError && <p className="meet-dialog-error" role="alert">{saveError}</p>}
      <div className="meet-dialog-actions"><button className="meets-button" type="button" disabled={isSaving} onClick={onClose}>Cancel</button><button className="meets-button meets-button-primary" type="submit" disabled={sample || isLoading || isSaving || Boolean(loadError)}>{isSaving ? "Saving…" : "Save Note"}</button></div>
    </form>
  </MeetDialog>;
}
