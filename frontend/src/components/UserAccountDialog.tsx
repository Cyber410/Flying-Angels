import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import MeetDialog from "./MeetDialog";
import { getAthletes } from "../services/athleteService";
import { inviteUser, updateUser } from "../services/authService";
import type { Athlete } from "../types/athlete";
import type { UserAccount, UserRole } from "../types/auth";

// Collect account details while keeping password setup with the invited recipient.
export default function UserAccountDialog({ user, linkedIds, onSaved, onClose }: {
  user?: UserAccount; linkedIds: number[]; onSaved: () => void; onClose: () => void;
}) {
  const [name, setName] = useState(user?.name ?? "");
  const [email, setEmail] = useState(user?.email ?? "");
  const [role, setRole] = useState<UserRole>(user?.role ?? "COACH");
  const [athleteId, setAthleteId] = useState(user?.athleteId ? String(user.athleteId) : "");
  const [active, setActive] = useState(user?.active ?? true);
  const [athletes, setAthletes] = useState<Athlete[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [loadError, setLoadError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    let ignore = false;
    getAthletes("all").then(/* Apply the successful request result to the current operation. */ data => { if (!ignore) setAthletes(data); }).catch(/* Report the request failure without discarding the current view. */ () => { if (!ignore) setLoadError("Athlete choices could not be loaded."); });
    return /* Stop pending work and release this effect when it is cleaned up. */ () => { ignore = true; };
  }, [revision]);

  // Save role changes or queue a verification invitation for a new account.
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError("");
    const request = { name: name.trim(), email: email.trim(), role, athleteId: role === "ATHLETE" ? Number(athleteId) : null };
    try {
      if (user) await updateUser(user.id, { ...request, active });
      else await inviteUser(request);
      onSaved(); onClose();
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to save the account details."); }
    finally { setBusy(false); }
  }

  return <MeetDialog title={user ? "Edit user" : "Invite a user"} busy={busy} onClose={onClose}>
    <form className="auth-form" onSubmit={submit}>
      {error && <p className="auth-error" role="alert">{error}</p>}
      <label>Full name<input required maxLength={100} value={name} disabled={busy} onChange={/* Update the name value from this control. */ event => setName(event.target.value)} /></label>
      <label>Email address<input required type="email" maxLength={254} value={email} disabled={busy} onChange={/* Update the email value from this control. */ event => setEmail(event.target.value)} /></label>
      <label>Role<select value={role} disabled={busy} onChange={/* Update the role value from this control. */ event => setRole(event.target.value as UserRole)}><option value="ADMIN">Admin</option><option value="COACH">Coach</option><option value="ATHLETE">Athlete / guardian</option></select></label>
      {role === "ATHLETE" && <label>Linked athlete<select required disabled={busy || !!loadError} value={athleteId} onChange={/* Update the athlete id value from this control. */ event => setAthleteId(event.target.value)}><option value="">Select an athlete</option>{athletes.filter(/* Keep entries that satisfy the current filter conditions. */ athlete => athlete.id === user?.athleteId || !linkedIds.includes(athlete.id)).map(/* Render each athlete in the list. */ athlete => <option key={athlete.id} value={athlete.id}>{athlete.firstName} {athlete.lastName} · #{athlete.id}{athlete.archived ? " (archived)" : ""}</option>)}</select></label>}
      {loadError && role === "ATHLETE" && <p className="auth-error" role="alert">{loadError} <button type="button" onClick={/* Retry loading the data for this view. */ () => { setLoadError(""); setRevision(/* Derive the next revision value from its current state. */ value => value + 1); }}>Try again</button></p>}
      {user ? <label>Account status<select value={active ? "active" : "inactive"} disabled={busy || user.accountStatus === "INVITED"} onChange={/* Update the active value from this control. */ event => setActive(event.target.value === "active")}><option value="active">Active</option><option value="inactive">Inactive</option></select></label> : <p className="auth-footnote">The recipient receives a verification code and setup link. Their account is created after email verification and password setup. Each athlete has one login, which a guardian may use.</p>}
      <div className="users-actions"><button className="auth-button" type="button" disabled={busy} onClick={onClose}>Cancel</button><button className="auth-button primary" disabled={busy || (role === "ATHLETE" && (!athleteId || !!loadError))}>{busy ? "Saving…" : user ? "Save changes" : "Send verification invitation"}</button></div>
    </form>
  </MeetDialog>;
}
