// Converts result units and saves or edits each attempt through the shared API.
import { useId, useState } from "react";
import type { FormEvent } from "react";
import { createParticipantResult, updateEventAttempt } from "../services/eventService";
import { bestResultAttempt, convertResultEntry, eventResultUnit, parseResultEntry, resultEntryForAttempt, resultEntryUnits } from "../utils/eventResult";
import type { ResultEntryUnit } from "../utils/eventResult";
import type { EventParticipant, EventResult, MeetEvent, ResultAttempt } from "../types/event";
import MeetDialog from "./MeetDialog";

type Props = {
  event: MeetEvent;
  participant: EventParticipant;
  attempts: ResultAttempt[];
  editingAttempt?: ResultAttempt;
  onSaved: (result: EventResult, attempts: ResultAttempt[], warning?: string) => void;
  onClose: () => void;
};

export default function EventResultDialog({ event, participant, attempts, editingAttempt, onSaved, onClose }: Props) {
  const id = useId();
  const baseUnit = eventResultUnit(event);
  const units = resultEntryUnits(event);
  const [entry, setEntry] = useState(() => resultEntryForAttempt(event, editingAttempt));
  const [attemptId] = useState(() => editingAttempt?.id ?? crypto.randomUUID());
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState("");
  const preview = parseResultEntry(entry, event);
  const clockEntry = entry.unit === "min-sec" || entry.unit === "h-min-sec";
  const help = entry.unit === "s" ? "Enter seconds, such as 12.45. You can also paste a clock time like 1:30."
    : entry.unit === "min" ? "Use decimal minutes: 1.5 means 1 minute 30 seconds. Choose Minutes + seconds to enter the two parts separately."
    : clockEntry ? "Enter the time in separate fields. Blank fields count as zero; seconds must be less than 60."
    : entry.unit === "cm" ? "Enter centimetres, such as 520 for a 5.20-metre jump."
    : "Enter the distance or height in metres, such as 5.20.";

  // Updates one result-entry field and clears the previous validation error.
  function updateValue(field: "value" | "hours" | "minutes" | "seconds", value: string) {
    setEntry((current) => ({ ...current, [field]: value }));
    setError("");
  }

  // Converts valid input before switching the result unit.
  function changeUnit(unit: ResultEntryUnit) {
    const hasValue = [entry.value, entry.hours, entry.minutes, entry.seconds].some((value) => value.trim());
    if (hasValue && !preview) {
      setError("Enter a valid result before changing units, or clear the fields to start again.");
      return;
    }
    setEntry(convertResultEntry(entry, unit, event));
    setError("");
  }

  async function handleSubmit(submit: FormEvent<HTMLFormElement>) {
    submit.preventDefault();
    if (isSaving) return;
    const parsed = parseResultEntry(entry, event);
    if (parsed === null) {
      setError(clockEntry
        ? entry.unit === "h-min-sec"
          ? "Enter a valid time. Hours must be whole numbers; minutes and seconds must each be below 60."
          : "Enter a valid time. Minutes must be whole numbers and seconds must be below 60."
        : "Enter a non-negative number in the selected unit, such as 12.45. Very large values cannot be saved.");
      return;
    }
    setIsSaving(true); setError("");
    try {
      const saved = editingAttempt
        ? await updateEventAttempt(event.id, Number(editingAttempt.id), participant.athleteId, parsed.result, parsed.score)
        : await createParticipantResult(event.id, participant.athleteId, parsed.result, parsed.score, attemptId);
      const attempt: ResultAttempt = { id: String(saved.resultId), result: saved.result!, score: saved.score! };
      const nextAttempts = editingAttempt
        ? attempts.map(item => item.id === editingAttempt.id ? attempt : item)
        : [...attempts.filter(item => item.id !== attempt.id), attempt];
      const best = bestResultAttempt(nextAttempts, event.rankingOrder)!;
      onSaved({ ...saved, resultId: Number(best.id), result: best.result, score: best.score }, nextAttempts);
      onClose();
    } catch (error) { setError(error instanceof Error ? error.message : "Unable to save result."); }
    finally { setIsSaving(false); }
  }

  return (
    <MeetDialog title={editingAttempt ? "Edit Result" : "Add Result"} busy={isSaving} onClose={onClose}>
      <form className="meet-form" onSubmit={handleSubmit}>
        <p className="meet-dialog-message">{participant.firstName} {participant.lastName} · {event.name}</p>
        <div className="meet-result-entry-row">
          {clockEntry ? (
            <fieldset className="meet-clock-entry" disabled={isSaving} aria-describedby={`${id}-help`}>
              <legend>Time</legend>
              <div className="meet-clock-values">
                {entry.unit === "h-min-sec" && (
                  <label className="meet-form-field" htmlFor={`${id}-hours`}>
                    Hours
                    <input id={`${id}-hours`} inputMode="numeric" placeholder="0" value={entry.hours} maxLength={8} autoFocus onChange={(change) => updateValue("hours", change.target.value)} />
                  </label>
                )}
                <label className="meet-form-field" htmlFor={`${id}-minutes`}>
                  Minutes
                  <input id={`${id}-minutes`} inputMode="numeric" placeholder="0" value={entry.minutes} maxLength={8} autoFocus={entry.unit === "min-sec"} onChange={(change) => updateValue("minutes", change.target.value)} />
                </label>
                <label className="meet-form-field" htmlFor={`${id}-seconds`}>
                  Seconds
                  <input id={`${id}-seconds`} inputMode="decimal" placeholder="00.00" value={entry.seconds} maxLength={14} onChange={(change) => updateValue("seconds", change.target.value)} />
                </label>
              </div>
            </fieldset>
          ) : (
            <label className="meet-form-field" htmlFor={id}>
              {baseUnit === "s" ? "Time" : "Distance / height"}
              <input id={id} value={entry.value} inputMode="decimal" placeholder={entry.unit === "min" ? "1.5" : entry.unit === "cm" ? "520" : baseUnit === "m" ? "5.20" : "12.45"} maxLength={48} required autoFocus disabled={isSaving} aria-describedby={`${id}-help`} onChange={(change) => updateValue("value", change.target.value)} />
            </label>
          )}
          <label className="meet-form-field" htmlFor={`${id}-unit`}>
            Unit
            <select id={`${id}-unit`} value={entry.unit} disabled={isSaving} onChange={(change) => changeUnit(change.target.value as ResultEntryUnit)}>
              {units.map((unit) => <option key={unit.value} value={unit.value}>{unit.label}</option>)}
            </select>
          </label>
        </div>
        <p className="meet-form-help" id={`${id}-help`}>{help} Switching units converts the value already entered.</p>
        {preview && (
          <p className="meet-result-preview" aria-live="polite">
            Preview: <strong>{preview.result}</strong>
            {(entry.unit === "min" || clockEntry || entry.unit === "cm") && <> · {preview.score.toLocaleString("en-CA", { maximumFractionDigits: 4 })} {baseUnit === "s" ? "seconds" : "metres"}</>}
          </p>
        )}
        <p className="meet-form-help">Every attempt is saved to the academy database. {event.rankingOrder === "ASC" ? "The lowest value is best." : "The highest value is best."}</p>
        {error && <p className="meet-dialog-error" role="alert">{error}</p>}
        <div className="meet-dialog-actions">
          <button className="meets-button" type="button" disabled={isSaving} onClick={onClose}>Cancel</button>
          <button className="meets-button meets-button-primary" type="submit" disabled={isSaving}>{isSaving ? "Saving..." : "Save Result"}</button>
        </div>
      </form>
    </MeetDialog>
  );
}
