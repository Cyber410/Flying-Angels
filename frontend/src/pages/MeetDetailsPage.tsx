import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import Header from "../components/header";
import MeetEventPanel from "../components/MeetEventPanel";
import MeetFormDialog from "../components/MeetFormDialog";
import EventSelectionDialog from "../components/EventSelectionDialog";
import { getMeetById, updateMeet } from "../services/meetService";
import { sampleMeetProfile } from "../data/sampleMeet";
import type { MeetProfile, MeetRequest } from "../types/meet";
import type { MeetEvent } from "../types/event";
import "./MeetsListPage.css";
import "./MeetDetailsPage.css";

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

function MeetDetailsContent({ meetId, sample }: { meetId: number; sample: boolean }) {
  const [profile, setProfile] = useState<MeetProfile | null>(() => sample ? sampleMeetProfile : null);
  const [isLoading, setIsLoading] = useState(!sample);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<"edit" | "add" | "copy" | null>(null);
  const [feedback, setFeedback] = useState("");

  useEffect(() => {
    if (sample) return;
    let ignore = false;
    getMeetById(meetId).then((data) => {
      if (!ignore) setProfile(data);
    }).catch((error: unknown) => {
      if (!ignore) setError(error instanceof Error ? error.message : "Unable to load meet details.");
    }).finally(() => {
      if (!ignore) setIsLoading(false);
    });
    return () => { ignore = true; };
  }, [meetId, sample, revision]);

  async function saveMeet(request: MeetRequest) {
    if (sample) return;
    const updated = await updateMeet(meetId, request);
    setProfile((current) => current ? { ...current, meet: updated } : current);
    setFeedback("Meet information updated.");
    setDialog(null);
  }

  function addEvents(created: MeetEvent[]) {
    setProfile((current) => {
      if (!current) return current;
      const events = [...current.events, ...created.filter((event) => !current.events.some((item) => item.id === event.id))];
      return { meet: { ...current.meet, eventCount: events.length }, events };
    });
    setFeedback(`${created.length} event${created.length === 1 ? "" : "s"} added to this meet.`);
  }

  function updateParticipantCount(eventId: number, count: number) {
    setProfile((current) => current ? {
      ...current,
      events: current.events.map((event) => event.id === eventId ? { ...event, participantCount: count } : event),
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
            <p>These athletes and results are fictional. Meet, event, and result changes are disabled. You can expand events and try browser-only notes. <Link to="/meets">Create a live meet</Link> to manage real data.</p>
          </div>
        )}
        {isLoading ? <p className="meets-state" role="status">Loading meet details...</p> : error ? (
          <div className="meets-state">
            <p role="alert">{error}</p>
            <button className="meets-button" type="button" onClick={() => { setError(""); setIsLoading(true); setRevision((value) => value + 1); }}>Try again</button>
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
                    {new Date(`${profile.meet.meetDate}T00:00:00`).toLocaleDateString("en-CA", { month: "short", day: "numeric", year: "numeric" })}
                  </time>
                  {profile.meet.location && <> · {profile.meet.location}</>}
                </p>
                {profile.meet.externalUrl && /^https?:\/\//i.test(profile.meet.externalUrl) && (
                  <a className="meet-website-link" href={profile.meet.externalUrl} target="_blank" rel="noopener noreferrer">Meet website ↗</a>
                )}
              </div>
              <button className="meets-button" type="button" disabled={sample} onClick={() => setDialog("edit")}>Edit Meet</button>
            </section>
            <p className="meets-feedback" role="status">{feedback}</p>
            <section className="meet-events-section" aria-labelledby="meet-events-heading">
              <div className="meet-events-heading">
                <h2 id="meet-events-heading">Events ({profile.events.length})</h2>
                <div className="meet-events-actions">
                  <button className="meets-button" type="button" disabled={sample} onClick={() => setDialog("copy")}>Copy Events</button>
                  <button className="meets-button meets-button-primary" type="button" disabled={sample} onClick={() => setDialog("add")}>+ Add Events</button>
                </div>
              </div>
              {profile.events.length === 0 && <p className="meet-events-empty">No events yet. Add event types or copy them from another meet to get started.</p>}
              {profile.events.map((event, index) => (
                <MeetEventPanel key={event.id} event={event} sample={sample} defaultOpen={index === 0} onCountChanged={updateParticipantCount} />
              ))}
            </section>

            {dialog === "edit" && !sample && <MeetFormDialog meet={profile.meet} onSave={saveMeet} onClose={() => setDialog(null)} />}
            {(dialog === "add" || dialog === "copy") && !sample && (
              <EventSelectionDialog mode={dialog} meet={profile.meet} existingEvents={profile.events} onCreated={addEvents} onClose={() => setDialog(null)} />
            )}
          </>
        )}
      </main>
    </div>
  );
}
