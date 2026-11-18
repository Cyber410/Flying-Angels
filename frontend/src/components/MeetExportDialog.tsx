// Lets Admins and Coaches download a meet's registration list or results as CSV.
import { useEffect, useState } from "react";
import MeetDialog from "./MeetDialog";
import {
  downloadMeetExport, getMeetExportOptions,
  type MeetExportOption, type MeetExportType,
} from "../services/meetExportService";
import type { Meet } from "../types/meet";

type Props = {
  meet: Meet;
  onExported: (message: string) => void;
  onClose: () => void;
};

const OPTIONS: { value: MeetExportType; label: string; description: string; emptyMessage: string }[] = [
  {
    value: "REGISTRATION",
    label: "Registration List",
    description: "One row per athlete assigned to each event.",
    emptyMessage: "No registration records are available. No athletes are assigned to this meet's events yet.",
  },
  {
    value: "RESULTS",
    label: "Competition Results",
    description: "One row per recorded performance, including every attempt.",
    emptyMessage: "No results are available. No performances have been recorded for this meet yet.",
  },
];

// Present backend counts and download the selected CSV without changing meet records.
export default function MeetExportDialog({ meet, onExported, onClose }: Props) {
  const [type, setType] = useState<MeetExportType>("REGISTRATION");
  const [counts, setCounts] = useState<MeetExportOption[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  // Record counts are a helpful preview only; export still works if they fail to load.
  useEffect(() => {
    const controller = new AbortController();
    getMeetExportOptions(meet.id, controller.signal)
      .then(result => { if (!controller.signal.aborted) setCounts(result.options); })
      .catch(() => { if (!controller.signal.aborted) setCounts(null); });
    return () => controller.abort();
  }, [meet.id]);

  // Summarize the current backend record count for an export option.
  function countLabel(value: MeetExportType) {
    const option = counts?.find(item => item.type === value);
    if (!option) return "";
    return option.recordCount === 0 ? "No records" : `${option.recordCount} record${option.recordCount === 1 ? "" : "s"}`;
  }

  // Generate the selected export and report download errors inside the dialog.
  async function handleExport() {
    if (busy) return;
    const selected = OPTIONS.find(option => option.value === type)!;
    const known = counts?.find(item => item.type === type);
    setMessage("");
    setError("");
    if (known && !known.available) {
      setMessage(selected.emptyMessage);
      return;
    }
    setBusy(true);
    try {
      const fileName = await downloadMeetExport(meet.id, type, meet.meetDate);
      onExported(`${selected.label} exported as ${fileName}.`);
    } catch (exportError) {
      const text = exportError instanceof Error ? exportError.message : "";
      if (/no (registration|recorded result)/i.test(text)) setMessage(selected.emptyMessage);
      else setError(text || "The export could not be created. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <MeetDialog title="Export Meet Data" busy={busy} onClose={onClose}>
      <form className="meet-form" onSubmit={event => { event.preventDefault(); void handleExport(); }}>
        <fieldset className="meet-export-options" disabled={busy}>
          <legend>Choose what to export</legend>
          {OPTIONS.map(option => (
            <label key={option.value} className="meet-export-option">
              <input
                type="radio"
                name="meet-export-type"
                value={option.value}
                checked={type === option.value}
                onChange={() => { setType(option.value); setMessage(""); setError(""); }}
              />
              <span>
                <strong>{option.label}</strong>
                <small>{option.description}</small>
                {countLabel(option.value) && <small className="meet-export-count">{countLabel(option.value)}</small>}
              </span>
            </label>
          ))}
        </fieldset>
        {message && <p className="meet-dialog-message" role="status">{message}</p>}
        {error && <p className="meet-dialog-error" role="alert">{error}</p>}
        <div className="meet-dialog-actions">
          <button className="meets-button" type="button" disabled={busy} onClick={onClose}>Cancel</button>
          <button className="meets-button meets-button-primary" type="submit" disabled={busy}>
            {busy ? "Preparing..." : "Export CSV"}
          </button>
        </div>
      </form>
    </MeetDialog>
  );
}
