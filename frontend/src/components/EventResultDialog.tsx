import { useId, useRef, useState } from "react";
import type { FormEvent } from "react";
import { createParticipantResult, saveParticipantResult } from "../services/eventService";
import { commitResultAttempts, discardStagedResultAttempts, stageResultAttempts } from "../services/eventAttemptStore";
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
  const sharedParticipant = useRef(participant);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState("");
  const preview = parseResultEntry(entry, event);
  const clockEntry = entry.unit === "min-sec" || entry.unit === "h-min-sec";
  const help = entry.unit === "s" ? "Enter seconds, such as 12.45. You can also paste a clock time like 1:30."
    : entry.unit === "min" ? "Use decimal minutes: 1.5 means 1 minute 30 seconds. Choose Minutes + seconds to enter the two parts separately."
    : clockEntry ? "Enter the time in separate fields. Blank fields count as zero; seconds must be less than 60."
    : entry.unit === "cm" ? "Enter centimetres, such as 520 for a 5.20-metre jump."
    : "Enter the distance or height in metres, such as 5.20.";

  function updateValue(field: "value" | "hours" | "minutes" | "seconds", value: string) {
    setEntry((current) => ({ ...current, [field]: value }));
    setError("");
  }

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
    const attempt: ResultAttempt = { id: attemptId, ...parsed };
    const nextAttempts = attempts.some((item) => item.id === attemptId)
      ? attempts.map((item) => item.id === attemptId ? attempt : item)
      : [...attempts, attempt];
    const best = bestResultAttempt(nextAttempts, event.rankingOrder)!;
    setIsSaving(true);
    setError("");
    let saved: EventResult | undefined;
    try {
      stageResultAttempts(event.id, participant.athleteId, nextAttempts);
      const current = sharedParticipant.current;
      if (current.resultId === null) {
        saved = await createParticipantResult(event.id, current.athleteId, best.result, best.score);
      } else if (current.result !== best.result || current.score !== best.score) {
        saved = await saveParticipantResult(event.id, current.athleteId, best.result, best.score);
      } else {
        saved = current;
      }
      // Keep the server ID if local storage fails after a successful first POST.
      sharedParticipant.current = saved;
      commitResultAttempts(event.id, participant.athleteId, nextAttempts);
      onSaved(saved, nextAttempts);
      onClose();
    } catch (error) {
      if (saved) {
        const warning = "The best result is saved to the shared meet, but the full attempt history could not be saved in this browser. Retry before closing this dialog.";
        onSaved(saved, nextAttempts, warning);
        setError(warning);
      } else {
        discardStagedResultAttempts(event.id, participant.athleteId);
        setError(error instanceof Error ? error.message : "Unable to save result.");
      }
    } finally {
      setIsSaving(false);
    }
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
        <p className="meet-notes-notice">All attempts are saved in this browser. Only the best result is shared with teammates. {event.rankingOrder === "ASC" ? "The lowest value is best." : "The highest value is best."}</p>
        {participant.resultId === null && <p className="meet-form-help">Saving this result also saves the athlete's assignment to the shared meet.</p>}
        {error && <p className="meet-dialog-error" role="alert">{error}</p>}
        <div className="meet-dialog-actions">
          <button className="meets-button" type="button" disabled={isSaving} onClick={onClose}>Cancel</button>
          <button className="meets-button meets-button-primary" type="submit" disabled={isSaving}>{isSaving ? "Saving..." : "Save Result"}</button>
        </div>
      </form>
    </MeetDialog>
  );
}
