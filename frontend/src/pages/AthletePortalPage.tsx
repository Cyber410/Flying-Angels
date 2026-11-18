import PortalAthleteProfile from "../components/PortalAthleteProfile";
import { useEffect, useState } from "react";
import Header from "../components/header";
import { apiFetch } from "../services/apiClient";
import { checkResponse } from "../services/apiResponse";
import "./AuthPages.css";
import "./UsersPage.css";

type Profile = { firstName: string; lastName: string; dateOfBirth: string | null; gender: string | null;
  results: { eventName: string; meetName: string | null; result: string | null; resultLabel: string | null }[] };

// Render only the athlete-owned profile returned by the restricted portal endpoint.
export default function AthletePortalPage() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    let ignore = false;
    // Load the authenticated athlete's information without accepting an arbitrary athlete ID.
    async function load() {
      try { const response = await apiFetch("/api/portal/profile"); await checkResponse(response, "Unable to load your athlete information."); const data: Profile = await response.json(); if (!ignore) setProfile(data); }
      catch (failure) { if (!ignore) setError(failure instanceof Error ? failure.message : "Unable to load your information."); }
      finally { if (!ignore) setLoading(false); }
    }
    void load(); return /* Stop pending work and release this effect when it is cleaned up. */ () => { ignore = true; };
  }, [revision]);

  return <div><Header /><main className="users-container"><div className="users-heading"><div><h1>My Profile</h1><p>{profile ? `${profile.firstName} ${profile.lastName}` : "Your Flying Angels athlete account"}</p></div></div>
    {loading ? <p role="status">Loading your information…</p> : error ? <div className="auth-error" role="alert">{error} <button onClick={/* Retry loading the data for this view. */ () => { setError(""); setLoading(true); setRevision(/* Derive the next revision value from its current state. */ value => value + 1); }}>Try again</button></div> : profile && <PortalAthleteProfile profile={profile} />}
  </main></div>;
}
