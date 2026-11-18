import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { completeEnrollment, getEnrollment, resendEnrollmentCode, verifyEnrollment } from "../services/authService";
import type { EnrollmentInfo } from "../types/auth";
import "./AuthPages.css";

// Require an emailed code before accepting a password and creating the invited account.
export default function ActivateAccountPage() {
  const [token] = useState(/* Compute the initial state when this component mounts. */ () => new URLSearchParams(window.location.hash.slice(1)).get("token") ?? "");
  const [info, setInfo] = useState<EnrollmentInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [complete, setComplete] = useState(false);
  const [revision, setRevision] = useState(0);

  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    let ignore = false;
    // Load only the invitation identified by the secret in the email link.
    async function load() {
      try { const result = await getEnrollment(token); if (!ignore) setInfo(result); }
      catch (failure) { if (!ignore) setError(failure instanceof Error ? failure.message : "Unable to read this invitation."); }
      finally { if (!ignore) setLoading(false); }
    }
    void load(); return /* Stop pending work and release this effect when it is cleaned up. */ () => { ignore = true; };
  }, [token, revision]);

  // Verify ownership of the email before exposing the account-creation form.
  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError(""); setMessage("");
    try { setInfo(await verifyEnrollment(token, code)); setCode(""); }
    catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to verify this code."); }
    finally { setBusy(false); }
  }

  // Send a replacement code through the configured mail delivery service.
  async function resend() {
    if (busy) return; setBusy(true); setError(""); setMessage("");
    try { setInfo(await resendEnrollmentCode(token)); setCode(""); setMessage("A new verification email has been queued. Use the latest code in your inbox."); }
    catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to request another code."); }
    finally { setBusy(false); }
  }

  // Create the account after verification and matching password confirmation.
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    if (password !== confirmation) { setError("The passwords do not match."); return; }
    if (password.length < 12 || new TextEncoder().encode(password).length > 72) { setError("Use at least 12 characters and no more than 72 UTF-8 bytes."); return; }
    setBusy(true); setError("");
    try {
      await completeEnrollment(token, password); setPassword(""); setConfirmation(""); setComplete(true);
      window.history.replaceState(null, "", window.location.pathname);
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to create your account."); }
    finally { setBusy(false); }
  }

  return <main className="auth-page"><section className="auth-card" aria-labelledby="activation-title">
    <p className="auth-brand">FLYING ANGELS</p><p className="auth-eyebrow">Account invitation</p>
    <h1 id="activation-title">{complete ? "Your account is ready" : info?.verified ? "Set your password" : "Verify your email"}</h1>
    {loading ? <p role="status">Checking your invitation…</p> : complete ? <><p className="auth-intro">Your email is verified and your account has been created. You can now sign in.</p><Link className="auth-button primary" to="/login">Go to sign in</Link></> : <>
      {error && <p className="auth-error" role="alert">{error}</p>}{message && <p className="auth-success" role="status">{message}</p>}
      {!info ? <><p className="auth-intro">Open the link in your latest invitation email. If it has expired, ask your administrator for another invitation.</p><button className="auth-button" onClick={/* Retry loading the data for this view. */ () => { setLoading(true); setError(""); setRevision(/* Derive the next revision value from its current state. */ value => value + 1); }}>Try again</button><Link className="auth-text-link" to="/login">Back to sign in</Link></> : <>
        <p className="auth-intro">Hello {info.name}. {info.verified ? "Email verified:" : "Enter the six-digit code sent to"} <strong>{info.email}</strong>.</p>
        <ol className="auth-progress" aria-label="Account setup"><li aria-current={!info.verified ? "step" : undefined}>1. Verify email</li><li aria-current={info.verified ? "step" : undefined}>2. Set password</li></ol>
        {!info.verified ? <form className="auth-form" onSubmit={verify} aria-busy={busy}>
          <label>Verification code<input className="auth-code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" minLength={6} maxLength={6} required value={code} disabled={busy} onChange={/* Update the code value from this control. */ (event) => setCode(event.target.value.replace(/\D/g, ""))} /></label>
          <p className="auth-footnote">Codes expire after 10 minutes. If delivery takes longer, request a new code.</p>
          <button className="auth-button primary" disabled={busy || code.length !== 6}>{busy ? "Verifying…" : "Verify email"}</button>
        </form> : <form className="auth-form" onSubmit={create} aria-busy={busy}>
          <label>New password<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} disabled={busy} value={password} onChange={/* Update the password value from this control. */ (event) => setPassword(event.target.value)} /></label>
          <label>Confirm password<input type="password" autoComplete="new-password" required minLength={12} maxLength={72} disabled={busy} value={confirmation} onChange={/* Update the confirmation value from this control. */ (event) => setConfirmation(event.target.value)} /></label>
          <p className="auth-footnote">Use at least 12 characters. A longer, memorable passphrase works well.</p>
          <button className="auth-button primary" disabled={busy}>{busy ? "Creating account…" : "Create my account"}</button>
        </form>}
        <button className="auth-text-link" disabled={busy} onClick={resend}>Send a new verification code</button>
      </>}
    </>}
  </section></main>;
}
