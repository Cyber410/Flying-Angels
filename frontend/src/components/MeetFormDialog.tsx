// Creates or edits meet details and validates single-day or multi-day dates.
import { useId, useState } from "react";
import type { FormEvent } from "react";
import type { Meet, MeetRequest } from "../types/meet";
import MeetDialog from "./MeetDialog";

type Props = {
  meet?: Meet;
  onSave: (request: MeetRequest) => Promise<void>;
  onClose: () => void;
};

export default function MeetFormDialog({ meet, onSave, onClose }: Props) {
  const id = useId();
  const [name, setName] = useState(meet?.name ?? "");
  const [meetDate, setMeetDate] = useState(meet?.meetDate ?? "");
  const [endDate, setEndDate] = useState(meet?.endDate && meet.endDate !== meet.meetDate ? meet.endDate : "");
  const [location, setLocation] = useState(meet?.location ?? "");
  const [externalUrl, setExternalUrl] = useState(meet?.externalUrl ?? "");
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSaving) return;
    if (!name.trim()) {
      setError("Meet name is required.");
      return;
    }

    if (endDate && endDate < meetDate) {
      setError("End date must be on or after the start date.");
      return;
    }
    setIsSaving(true);
    setError("");
    try {
      await onSave({
        name: name.trim(),
        meetDate,
        endDate: endDate || null,
        location: location.trim() || null,
        // The backend's URL pattern rejects empty strings; optional values use null.
        externalUrl: externalUrl.trim() || null,
      });
    } catch (error) {
      setError(error instanceof Error ? error.message : "The meet could not be saved.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <MeetDialog title={meet ? "Edit Meet" : "New Meet"} busy={isSaving} onClose={onClose}>
      <form className="meet-form" onSubmit={handleSubmit}>
        <label className="meet-form-field" htmlFor={`${id}-name`}>
          Meet name
          <input
            id={`${id}-name`}
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={150}
            required
            autoFocus
            disabled={isSaving}
          />
        </label>
        <label className="meet-form-field" htmlFor={`${id}-date`}>
          Start date
          <input
            id={`${id}-date`}
            type="date"
            value={meetDate}
            onChange={(event) => setMeetDate(event.target.value)}
            required
            disabled={isSaving}
          />
        </label>
        <label className="meet-form-field" htmlFor={`${id}-end-date`}>
          End date (optional)
          <input id={`${id}-end-date`} type="date" min={meetDate || undefined} value={endDate} onChange={event => setEndDate(event.target.value)} disabled={isSaving} />
          <span className="meet-form-help">Leave blank for a single-day meet.</span>
        </label>
        <label className="meet-form-field" htmlFor={`${id}-location`}>
          Location (optional)
          <input
            id={`${id}-location`}
            value={location}
            onChange={(event) => setLocation(event.target.value)}
            maxLength={150}
            disabled={isSaving}
          />
        </label>
        <label className="meet-form-field" htmlFor={`${id}-url`}>
          Website (optional)
          <input
            id={`${id}-url`}
            type="url"
            placeholder="https://example.com"
            pattern="https?://.+"
            value={externalUrl}
            onChange={(event) => setExternalUrl(event.target.value)}
            maxLength={500}
            disabled={isSaving}
          />
        </label>
        {error && <p className="meet-dialog-error" role="alert">{error}</p>}
        <div className="meet-dialog-actions">
          <button className="meets-button" type="button" disabled={isSaving} onClick={onClose}>
            Cancel
          </button>
          <button className="meets-button meets-button-primary" type="submit" disabled={isSaving}>
            {isSaving ? "Saving..." : meet ? "Save Changes" : "Create Meet"}
          </button>
        </div>
      </form>
    </MeetDialog>
  );
}
