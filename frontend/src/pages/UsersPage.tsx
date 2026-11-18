import { useEffect, useState } from "react";
import { useAuth } from "../auth/authContext";
import Header from "../components/header";
import UserAccountDialog from "../components/UserAccountDialog";
import { cancelInvitation, deleteUser, getUsers, resendInvitation } from "../services/authService";
import MeetDialog from "../components/MeetDialog";
import type { PendingInvitation, UserAccount } from "../types/auth";
import "./AuthPages.css";
import "./UsersPage.css";

// Give administrators separate views of existing accounts and unverified invitations.
export default function UsersPage() {
  const { session } = useAuth();
  const [deleteTarget, setDeleteTarget] = useState<UserAccount | null>(null);
  const [data, setData] = useState<{ users: UserAccount[]; invitations: PendingInvitation[] }>({ users: [], invitations: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [feedback, setFeedback] = useState("");
  const [search, setSearch] = useState("");
  const [view, setView] = useState<"users" | "invitations">("users");
  const [dialog, setDialog] = useState<UserAccount | "new" | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [revision, setRevision] = useState(0);
  const [cancelTarget, setCancelTarget] = useState<PendingInvitation | null>(null);
  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    let ignore = false;
    getUsers().then(/* Apply the successful request result to the current operation. */ result => { if (!ignore) setData(result); }).catch(/* Report the request failure without discarding the current view. */ failure => { if (!ignore) setError(failure instanceof Error ? failure.message : "Unable to load users."); }).finally(/* Clear the busy state after the request completes. */ () => { if (!ignore) setLoading(false); });
    return /* Stop pending work and release this effect when it is cleaned up. */ () => { ignore = true; };
  }, [revision]);

  // Reload account data after a successful account or invitation action.
  function refresh() { setError(""); setLoading(true); setRevision(/* Derive the next revision value from its current state. */ value => value + 1); }

  // Queue a fresh verification message and invalidate the invitation's earlier link.
  async function resend(id: number) {
    if (busyId !== null) return; setBusyId(id); setError(""); setFeedback("");
    try { await resendInvitation(id); setFeedback("A fresh verification invitation has been queued for delivery."); refresh(); }
    catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to resend the invitation."); }
    finally { setBusyId(null); }
  }

  // Cancel only the invitation confirmed by the administrator.
  async function confirmCancellation() {
    if (!cancelTarget || busyId !== null) return;
    setBusyId(cancelTarget.id); setError("");
    try { await cancelInvitation(cancelTarget.id); setCancelTarget(null); setFeedback("Invitation cancelled. No account was created."); refresh(); }
    catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to cancel the invitation."); }
    finally { setBusyId(null); }
  }

  // Permanently remove the confirmed login account and refresh the account list.
  async function confirmDeletion() {
    if (!deleteTarget || busyId !== null) return;
    setBusyId(deleteTarget.id); setError(""); setFeedback("");
    try {
      await deleteUser(deleteTarget.id);
      setDeleteTarget(null);
      setFeedback("Account deleted. Login access and outstanding invitations have been revoked.");
      refresh();
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to delete this account."); }
    finally { setBusyId(null); }
  }

  const query = search.trim().toLowerCase();
  // Match account and invitation rows against the name or email search.
  const matches = (row: { name: string; email: string }) => `${row.name} ${row.email}`.toLowerCase().includes(query);
  const users = data.users.filter(matches);
  const invitations = data.invitations.filter(matches);
  const linkedIds = [...data.users.map(/* Build linked ids from each user. */ user => user.athleteId), ...data.invitations.filter(/* Keep rows with AWAITING_VERIFICATION status. */ invitation => invitation.status === "AWAITING_VERIFICATION").map(/* Build linked ids from each invitation. */ invitation => invitation.athleteId)].filter(/* Exclude entries that do not belong in this result or selection. */ (id): id is number => id !== null);
  return <div><Header /><main className="users-container">
    <div className="users-heading"><div><h1>Users</h1><p>Manage academy access, roles, and account invitations.</p></div><button className="auth-button primary" onClick={/* Open or close the requested event action dialog. */ () => setDialog("new")}>+ Invite user</button></div>
    {error && <div className="auth-error" role="alert">{error} <button onClick={refresh}>Try again</button></div>}{feedback && <p className="auth-success" role="status">{feedback}</p>}
    <section className="users-panel"><div className="users-toolbar"><div className="users-tabs"><button aria-pressed={view === "users"} onClick={/* Update view for the selected action. */ () => setView("users")}>Accounts ({data.users.length})</button><button aria-pressed={view === "invitations"} onClick={/* Update view for the selected action. */ () => setView("invitations")}>Pending invitations ({data.invitations.length})</button></div><label className="users-search">Search users<input type="search" value={search} onChange={/* Update the search value from this control. */ event => setSearch(event.target.value)} placeholder="Name or email" /></label></div>
      {loading ? <p className="users-empty" role="status">Loading users…</p> : <div className="users-table-scroll"><table className="users-table"><thead><tr><th scope="col">Name</th><th scope="col">Email</th><th scope="col">Role</th><th scope="col">Status</th><th scope="col">Actions</th></tr></thead><tbody>
        {view === "users" ? users.map(/* Render each user in the list. */ user => <tr key={user.id}><th scope="row">{user.name}</th><td>{user.email}</td><td>{user.role === "ATHLETE" ? "Athlete / guardian" : user.role === "ADMIN" ? "Admin" : "Coach"}</td><td><span className={`users-status ${user.active ? "active" : "inactive"}`}>{user.accountStatus === "INVITED" ? "Awaiting activation" : user.active ? "Active" : "Inactive"}</span></td><td><button className="auth-button" aria-label={`Edit ${user.name}`} onClick={/* Open or close the requested event action dialog. */ () => setDialog(user)}>Edit</button> <button className="auth-button users-delete" disabled={busyId !== null || user.id === session?.user?.id} aria-label={`Delete ${user.name}`} onClick={/* Ask for confirmation before deleting this account. */ () => setDeleteTarget(user)}>Delete</button></td></tr>) : invitations.map(/* Render each invitation in the list. */ invitation => <tr key={invitation.id}><th scope="row">{invitation.name}</th><td>{invitation.email}</td><td>{invitation.role === "ATHLETE" ? "Athlete / guardian" : invitation.role === "ADMIN" ? "Admin" : "Coach"}</td><td><span className="users-status">{invitation.status === "AWAITING_VERIFICATION" ? "Awaiting verification" : "Expired or locked"}</span></td><td><button className="auth-button" disabled={busyId !== null || invitation.status !== "AWAITING_VERIFICATION"} onClick={/* Perform the selected button action. */ () => void resend(invitation.id)}>{busyId === invitation.id ? "Queuing…" : "Resend email"}</button> <button className="auth-button" disabled={busyId !== null} onClick={/* Update cancel target for the selected action. */ () => setCancelTarget(invitation)}>Cancel</button></td></tr>)}
      </tbody></table>{!(view === "users" ? users.length : invitations.length) && <p className="users-empty">{query ? "No users match your search." : view === "users" ? "No accounts to display." : "No pending invitations."}</p>}</div>}
    </section><p className="auth-footnote">Pending invitations are not login accounts. Email verification and password setup must be completed first.</p>
    {dialog && <UserAccountDialog user={dialog === "new" ? undefined : dialog} linkedIds={linkedIds} onClose={/* Close the dialog and clear its visible state. */ () => setDialog(null)} onSaved={/* Merge the saved data into the parent view. */ () => { setFeedback(dialog === "new" ? "Verification invitation queued. No account has been created yet." : "Account updated."); if (dialog === "new") setView("invitations"); refresh(); }} />}
    {deleteTarget && <MeetDialog title="Delete user account?" busy={busyId !== null} onClose={/* Dismiss the account deletion confirmation. */ () => setDeleteTarget(null)}>
      <p>Delete the account for <strong>{deleteTarget.name}</strong> ({deleteTarget.email})?</p>
      <p>They will lose access immediately. Existing sessions and outstanding invitations will stop working. Athlete records, results, and notes will be kept.</p>
      <p>This cannot be undone. Access can be granted again through a new invitation.</p>
      <div className="users-actions"><button className="auth-button" disabled={busyId !== null} onClick={/* Keep this account and close the confirmation. */ () => setDeleteTarget(null)}>Keep account</button><button className="auth-button users-delete" disabled={busyId !== null} onClick={confirmDeletion}>{busyId !== null ? "Deleting…" : "Delete account"}</button></div>
    </MeetDialog>}
    {cancelTarget && <MeetDialog title="Cancel invitation?" busy={busyId !== null} onClose={/* Close the dialog and clear its visible state. */ () => setCancelTarget(null)}><p>Cancel the invitation for {cancelTarget.email}? The emailed link and code will no longer work.</p><div className="users-actions"><button className="auth-button" disabled={busyId !== null} onClick={/* Update cancel target for the selected action. */ () => setCancelTarget(null)}>Keep invitation</button><button className="auth-button primary" disabled={busyId !== null} onClick={confirmCancellation}>Cancel invitation</button></div></MeetDialog>}
  </main></div>;
}
