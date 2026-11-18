import { useOptionalAuth } from "../auth/authContext";
// Displays meet information and its event panels, including the read-only demo.
import { formatMeetDates } from "../utils/meetDate";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import Header from "../components/header";
import MeetEventPanel from "../components/MeetEventPanel";
import MeetFormDialog from "../components/MeetFormDialog";
import EventSelectionDialog from "../components/EventSelectionDialog";
import MeetExportDialog from "../components/MeetExportDialog";
import { getMeetById, updateMeet } from "../services/meetService";
import { sampleMeetProfile } from "../data/sampleMeet";
import type { MeetProfile, MeetRequest } from "../types/meet";
import type { MeetEvent } from "../types/event";
import "./MeetsListPage.css";
import "./MeetDetailsPage.css";

// Validate the route and choose live meet details or the sample preview.
export default function MeetDetailsPage({ sample = false }: { sample?: boolean }) {
  const { meetId } = useParams();
  const id = sample ? -1 : Number(meetId);
  if (!sample && (!Number.isSafeInteger(id) || id < 1)) {
    return (
      <div className="meets-page">
        <Header />
        <main className="meets-container">
          <Link className="meet-back-link" to="/meets">‹ Back to Meets</Link>
          <h1>Meet not found</h1>
          <p>Choose a meet from the Meets List.</p>
        </main>
      </div>
    );
  }
  return <MeetDetailsContent key={id} meetId={id} sample={sample} />;
}

// Load a meet and coordinate its details, event panels, and dialogs.
function MeetDetailsContent({ meetId, sample }: { meetId: number; sample: boolean }) {
  const auth = useOptionalAuth();
  const canExport = auth ? auth.can("EXPORT") : true;
  const canManage = auth ? auth.can("MANAGE_MEETS") : true;
  const [profile, setProfile] = useState<MeetProfile | null>(/* Compute the initial state when this component mounts. */ () => sample ? sampleMeetProfile : null);
  const [isLoading, setIsLoading] = useState(!sample);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<"edit" | "add" | "copy" | "export" | null>(null);
  const [feedback, setFeedback] = useState("");

  // Fetch live meet details; the sample route stays read-only.
  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    if (sample) return;
    let ignore = false;
    getMeetById(meetId).then(/* Apply the successful request result to the current operation. */ (data) => {
      if (!ignore) setProfile(data);
    }).catch(/* Report the request failure without discarding the current view. */ (error: unknown) => {
      if (!ignore) setError(error instanceof Error ? error.message : "Unable to load meet details.");
    }).finally(/* Clear the busy state after the request completes. */ () => {
      if (!ignore) setIsLoading(false);
    });
    return /* Stop pending work and release this effect when it is cleaned up. */ () => { ignore = true; };
  }, [meetId, sample, revision]);

  // Persist the edited meet and update the details view.
  async function saveMeet(request: MeetRequest) {
    if (sample) return;
    const updated = await updateMeet(meetId, request);
    setProfile(/* Derive the next profile value from its current state. */ (current) => current ? { ...current, meet: updated } : current);
    setFeedback("Meet information updated.");
    setDialog(null);
  }

  // Merge newly created events into the meet without repeating existing IDs.
  function addEvents(created: MeetEvent[]) {
    setProfile(/* Derive the next profile value from its current state. */ (current) => {
      if (!current) return current;
      const events = [...current.events, ...created.filter(/* Apply the search and selection criteria for events. */ (event) => !current.events.some(/* Check whether any item matches the required condition. */ (item) => item.id === event.id))];
      return { meet: { ...current.meet, eventCount: events.length }, events };
    });
    setFeedback(`${created.length} event${created.length === 1 ? "" : "s"} added to this meet.`);
  }

  // Update the event count shown after participants change.
  function updateParticipantCount(eventId: number, count: number) {
    setProfile(/* Derive the next profile value from its current state. */ (current) => current ? {
      ...current,
      events: current.events.map(/* Convert each event into the required display or request data. */ (event) => event.id === eventId ? { ...event, participantCount: count } : event),
    } : current);
  }

  return (
    <div className="meets-page">
      <Header />
      <main className="meets-container">
        <Link className="meet-back-link" to="/meets">‹ Back to Meets</Link>
        {sample && (
          <div className="meet-sample-banner" role="note">
            <strong>Sample meet preview</strong>
            <p>These athletes and results are fictional. This preview is read-only. You can expand events to explore the layout. <Link to="/meets">Create a live meet</Link> to manage academy data.</p>
          </div>
        )}
        {isLoading ? <p className="meets-state" role="status">Loading meet details...</p> : error ? (
          <div className="meets-state">
            <p role="alert">{error}</p>
            <button className="meets-button" type="button" onClick={/* Retry loading the data for this view. */ () => { setError(""); setIsLoading(true); setRevision(/* Derive the next revision value from its current state. */ (value) => value + 1); }}>Try again</button>
            <p><Link className="meet-back-link" to="/meets/demo">Preview a sample meet</Link></p>
          </div>
        ) : profile && (
          <>
            <section className="meet-details-heading" aria-label="Meet information">
              <div>
                <p className="meet-details-eyebrow">MEET DETAILS</p>
                <h1>{profile.meet.name}</h1>
                <p className="meet-details-meta">
                  <time dateTime={profile.meet.meetDate}>
                    {formatMeetDates(profile.meet.meetDate, profile.meet.endDate)}
                  </time>
                  {profile.meet.location && <> · {profile.meet.location}</>}
                </p>
                {profile.meet.externalUrl && /^https?:\/\//i.test(profile.meet.externalUrl) && (
                  <a className="meet-website-link" href={profile.meet.externalUrl} target="_blank" rel="noopener noreferrer">Meet website ↗</a>
                )}
              </div>
              <div className="availability-row-actions meet-details-actions">{!sample && <Link className="meets-button" to={`/availability?meetId=${profile.meet.id}`}>View availability</Link>}<button hidden={!canExport} className="meets-button" type="button" disabled={sample} onClick={/* Open the server-backed CSV export choices. */ () => setDialog("export")}>Export</button><button hidden={!canManage} className="meets-button" type="button" disabled={sample} onClick={/* Open or close the requested event action dialog. */ () => setDialog("edit")}>Edit Meet</button></div>
            </section>
            <p className="meets-feedback" role="status">{feedback}</p>
            <section className="meet-events-section" aria-labelledby="meet-events-heading">
              <div className="meet-events-heading">
                <h2 id="meet-events-heading">Events ({profile.events.length})</h2>
                <div className="meet-events-actions">
                  <button className="meets-button" type="button" disabled={sample} onClick={/* Open or close the requested event action dialog. */ () => setDialog("copy")}>Copy Events</button>
                  <button className="meets-button meets-button-primary" type="button" disabled={sample} onClick={/* Open or close the requested event action dialog. */ () => setDialog("add")}>+ Add Events</button>
                </div>
              </div>
              {profile.events.length === 0 && <p className="meet-events-empty">No events yet. Add event types or copy them from another meet to get started.</p>}
              {profile.events.map(/* Render each event in the list. */ (event, index) => (
                <MeetEventPanel key={event.id} event={event} sample={sample} defaultOpen={index === 0} onCountChanged={updateParticipantCount} />
              ))}
            </section>

            {dialog === "export" && !sample && canExport && <MeetExportDialog meet={profile.meet} onExported={/* Confirm the download and return to meet details. */ message => { setFeedback(message); setDialog(null); }} onClose={/* Close export without changing meet data. */ () => setDialog(null)} />}
            {dialog === "edit" && !sample && <MeetFormDialog meet={profile.meet} onSave={saveMeet} onClose={/* Close the dialog and clear its visible state. */ () => setDialog(null)} />}
            {(dialog === "add" || dialog === "copy") && !sample && (
              <EventSelectionDialog mode={dialog} meet={profile.meet} existingEvents={profile.events} onCreated={addEvents} onClose={/* Close the dialog and clear its visible state. */ () => setDialog(null)} />
            )}
          </>
        )}
      </main>
    </div>
  );
}
