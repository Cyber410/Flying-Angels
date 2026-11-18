import { useEffect, useState } from "react";
import Header from "../components/header";
import AvailabilityResponseDialog from "../components/AvailabilityResponseDialog";
import { availabilityDateTime, availabilityLabel, getMyAvailability } from "../services/availabilityService";
import type { AvailabilityRequest } from "../services/availabilityService";
import { formatMeetDates } from "../utils/meetDate";
import "./AuthPages.css";
import "./UsersPage.css";
import "./AvailabilityPage.css";

// Present the athlete's upcoming nominations with explicit attendance actions.
export default function PortalMeetsPage() {
  const [requests, setRequests] = useState<AvailabilityRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState("");
  const [selected, setSelected] = useState<number | null>(null);
  const [revision, setRevision] = useState(0);
  useEffect(/* Read only the session-owned upcoming nominations. */ () => {
    const controller = new AbortController();
    getMyAvailability(controller.signal).then(/* Replace cards with the latest server statuses. */ data => { if (!controller.signal.aborted) { setRequests(data); setLoading(false); } })
      .catch(/* Keep the meet list retryable on connection failures. */ failure => { if (!controller.signal.aborted) { setError(failure instanceof Error ? failure.message : "Unable to load your meets."); setLoading(false); } });
    return /* Cancel the previous list request on refresh or unmount. */ () => controller.abort();
  }, [revision]);

  // Refresh proposals so assignment changes and saved replies appear immediately.
  function refresh() { setError(""); setLoading(true); setRevision(value => value + 1); }

  return <div><Header /><main className="users-container"><div className="users-heading"><div><h1>My Meets</h1><p>Review your upcoming meets and let the academy know whether you can attend.</p></div><button className="auth-button" disabled={loading} onClick={refresh}>Refresh</button></div>
    {feedback && <p className="auth-success" role="status">{feedback}</p>}
    {loading ? <p role="status">Loading your meets…</p> : error ? <div className="auth-error" role="alert">{error} <button className="auth-button" onClick={refresh}>Try again</button></div> : <div className="availability-meet-grid">{requests.map(/* Render each nominated meet and the events covered by its response. */ request => <article key={request.id} className="availability-meet-card"><div className="availability-card-heading"><h2>{request.meetName}</h2><span className={`availability-status ${request.status.toLowerCase()}`}>{availabilityLabel(request.status)}</span></div><p>{formatMeetDates(request.startDate, request.endDate)}{request.location ? ` · ${request.location}` : ""}</p><p><strong>Events:</strong> {request.events.map(/* Name the athlete's assigned events without staff detail links. */ event => event.eventName).join(", ")}</p>
      {(request.status === "PENDING" || request.status === "AWAITING_RESPONSE") && request.lastAnswer && <p className="availability-notice">Your assigned events changed. Please confirm your availability again.</p>}
      <div className="availability-card-footer">{request.canRespond ? <><span className="auth-footnote">{request.responseDeadline && `Reply by ${availabilityDateTime(request.responseDeadline, request.timeZone)} (${request.timeZone})`}</span><button className="auth-button primary" onClick={/* Open the current proposal for review before replying. */ () => setSelected(request.id)}>{request.status === "AVAILABLE" || request.status === "UNAVAILABLE" ? "Update response" : "Respond"}</button></> : <span className="auth-footnote">Responses closed</span>}</div>
    </article>)}{!requests.length && <p className="users-empty">You have no upcoming nominated meets.</p>}</div>}
    {selected !== null && <AvailabilityResponseDialog id={selected} portal onClose={/* Close the response form without changing availability. */ () => setSelected(null)} onSaved={/* Confirm the saved response and reload the meet cards. */ request => { setSelected(null); setFeedback(`Response saved for ${request.meetName}: ${availabilityLabel(request.status)}.`); refresh(); }} />}
  </main></div>;
}
