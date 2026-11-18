import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import MeetDialog from "./MeetDialog";
import { AvailabilityConflict, availabilityDateTime, availabilityLabel, getAvailability, saveAvailability } from "../services/availabilityService";
import type { AvailabilityAnswer, AvailabilityRequest, ResponseChannel } from "../services/availabilityService";
import { formatMeetDates } from "../utils/meetDate";
import "../pages/AvailabilityPage.css";

// Reload the selected proposal on opening and require fresh confirmation after conflicts.
export default function AvailabilityResponseDialog({ id, portal = false, onClose, onSaved }: {
  id: number; portal?: boolean; onClose: () => void; onSaved: (request: AvailabilityRequest) => void;
}) {
  const [revision, setRevision] = useState(0);
  const [request, setRequest] = useState<AvailabilityRequest | null>(null);
  const [answer, setAnswer] = useState<AvailabilityAnswer | "">("");
  const [channel, setChannel] = useState<ResponseChannel | "">("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [conflict, setConflict] = useState(false);
  useEffect(/* Load current event assignments and optimistic-lock values. */ () => {
    const controller = new AbortController();
    getAvailability(id, portal, controller.signal).then(/* Use only the current proposal and matching response. */ data => {
      if (!controller.signal.aborted) { setRequest(data); setAnswer(data.status === "AVAILABLE" || data.status === "UNAVAILABLE" ? data.status : ""); }
    }).catch(/* Surface a request that disappeared or cannot be loaded. */ failure => {
      if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Unable to load this request.");
    });
    return /* Cancel reads when the dialog closes or reloads. */ () => controller.abort();
  }, [id, portal, revision]);

  // Clear the stale choice so changed assignments must be reviewed and confirmed again.
  function reload() { setRequest(null); setAnswer(""); setError(""); setConflict(false); setRevision(value => value + 1); }

  // Save one explicit availability choice using the latest displayed request version.
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!request || !answer || busy || conflict || !request.canRespond || (!portal && !channel)) return;
    setBusy(true); setError("");
    try { const saved = await saveAvailability(request, answer, portal, !portal && channel ? { communicationMethod: channel, note: note.trim() } : undefined); onSaved(saved); }
    catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to save your response."); setConflict(failure instanceof AvailabilityConflict); }
    finally { setBusy(false); }
  }

  return <MeetDialog title={portal ? "Respond to meet invitation" : "Record external response"} busy={busy} onClose={onClose}>
    {error && <div className="auth-error" role="alert">{error} <button type="button" className="auth-button" disabled={busy} onClick={reload}>Reload request</button></div>}
    {!request ? !error && <p role="status">Loading assigned events…</p> : <form className="availability-response" onSubmit={submit}>
      <div><h3>{request.meetName}</h3><p>{formatMeetDates(request.startDate, request.endDate)}{request.location ? ` · ${request.location}` : ""}</p>{!portal && <p>Athlete: <strong>{request.firstName} {request.lastName}</strong></p>}</div>
      <div className="availability-event-list"><strong>Assigned events</strong><ul>{request.events.map(/* List the events covered by this one meet response. */ event => <li key={event.eventId}>{event.eventName}</li>)}</ul></div>
      {request.status !== "AVAILABLE" && request.status !== "UNAVAILABLE" && request.lastAnswer && <p className="availability-notice">Assignments changed since the previous response ({availabilityLabel(request.lastAnswer)}). Review the events and confirm again.</p>}
      {request.responseDeadline && <p className="auth-footnote">Responses close {availabilityDateTime(request.responseDeadline, request.timeZone)} ({request.timeZone}).</p>}
      {!request.canRespond ? <p className="availability-notice" role="status">Responses are closed for this meet.</p> : <>
        <fieldset disabled={busy || conflict}><legend>{portal ? "Can you attend this meet?" : "Athlete's response"}</legend>
          <label className={`availability-answer ${answer === "AVAILABLE" ? "selected" : ""}`}><input type="radio" name="availability" required value="AVAILABLE" checked={answer === "AVAILABLE"} onChange={/* Confirm attendance for all displayed assigned events. */ () => setAnswer("AVAILABLE")} />Available — {portal ? "I can attend" : "can attend"}</label>
          <label className={`availability-answer ${answer === "UNAVAILABLE" ? "selected" : ""}`}><input type="radio" name="availability" value="UNAVAILABLE" checked={answer === "UNAVAILABLE"} onChange={/* Record that the athlete cannot attend this meet. */ () => setAnswer("UNAVAILABLE")} />Unavailable — {portal ? "I cannot attend" : "cannot attend"}</label>
        </fieldset>
        {!portal && <div className="auth-form"><label>Response received via<select required value={channel} disabled={busy || conflict} onChange={/* Keep the external communication source with the audit record. */ event => setChannel(event.target.value as ResponseChannel)}><option value="">Choose a source</option><option value="PHONE">Phone</option><option value="WHATSAPP">WhatsApp</option><option value="EMAIL">Email</option><option value="TEXT">Text message</option><option value="IN_PERSON">In person</option><option value="OTHER">Other</option></select></label><label>Staff note (optional)<textarea maxLength={1000} rows={3} value={note} disabled={busy || conflict} onChange={/* Keep external context private to staff. */ event => setNote(event.target.value)} /></label><p className="auth-footnote">This note is visible to staff only.</p></div>}
      </>}
      <div className="users-actions"><button type="button" className="auth-button" onClick={onClose} disabled={busy}>Cancel</button>{request.canRespond && <button className="auth-button primary" type="submit" disabled={busy || conflict || !answer || (!portal && !channel)}>{busy ? "Saving…" : "Save response"}</button>}</div>
    </form>}
  </MeetDialog>;
}
