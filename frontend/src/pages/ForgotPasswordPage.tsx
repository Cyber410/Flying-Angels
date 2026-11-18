import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { completePasswordReset, requestPasswordReset } from "../services/authService";
import "./AuthPages.css";

// Recover an existing account with an emailed code, without creating a login session.
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [token, setToken] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [complete, setComplete] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  // Space replacement-code requests and release the timer when leaving recovery.
  useEffect(() => {
    if (!token || complete) return;
    const timer = window.setInterval(() => setCooldown(value => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [token, complete]);

  // Keep recovery secrets in component memory and request a fresh challenge.
  async function requestCode(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault(); if (busy || cooldown > 0) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const result = await requestPasswordReset(email.trim());
      setToken(result.token); setMessage(result.message); setCode("");
      setPassword(""); setConfirmation(""); setCooldown(60);
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to request a code."); }
    finally { setBusy(false); }
  }

  // Validate matching passwords before atomically checking the code and saving the new password.
  async function resetPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    if (password !== confirmation) { setError("The passwords do not match."); return; }
    if (password.length < 12 || new TextEncoder().encode(password).length > 72) {
      setError("Use at least 12 characters and no more than 72 UTF-8 bytes."); return;
    }
    setBusy(true); setError("");
    try {
      await completePasswordReset(token, code, password);
      setToken(""); setCode(""); setPassword(""); setConfirmation(""); setMessage(""); setComplete(true);
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to reset your password."); }
    finally { setBusy(false); }
  }

  // Return to email entry without retaining an old recovery challenge or password.
  function changeEmail() {
    setToken(""); setCode(""); setPassword(""); setConfirmation(""); setCooldown(0); setError(""); setMessage("");
  }

  return <main className="auth-page"><section className="auth-card" aria-labelledby="recovery-title">
    <p className="auth-brand">FLYING ANGELS</p><p className="auth-eyebrow">Account recovery</p>
    <h1 id="recovery-title">{complete ? "Password updated" : token ? "Reset your password" : "Forgot your password?"}</h1>
    {complete ? <><p className="auth-success" role="status">Your password has been changed. Sign in with your new password. Previous sessions have been signed out.</p><Link className="auth-button primary" to="/login" reloadDocument>Back to sign in</Link></> : <>
      <p className="auth-intro">{token ? `Enter the code sent to ${email.trim()} and choose a new password. Keep this page open.` : "Enter your account email to request a password reset code."}</p>
      {error && <p className="auth-error" role="alert">{error}</p>}
      {message && <p className="auth-success" role="status">{message}</p>}
      {!token ? <form className="auth-form" onSubmit={requestCode} aria-busy={busy}>
        <label>Email address<input type="email" autoComplete="username" required maxLength={254} disabled={busy} value={email} onChange={/* Capture the recovery email. */ event => setEmail(event.target.value)} /></label>
        <button className="auth-button primary" disabled={busy}>{busy ? "Requesting code..." : "Send reset code"}</button>
      </form> : <>
        <form className="auth-form" onSubmit={resetPassword} aria-busy={busy}>
          <label>Reset code<input className="auth-code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" minLength={6} maxLength={6} required disabled={busy} value={code} onChange={/* Accept only the six emailed digits. */ event => setCode(event.target.value.replace(/\D/g, ""))} /></label>
          <label>New password<div className="auth-password"><input type={visible ? "text" : "password"} autoComplete="new-password" minLength={12} maxLength={72} required disabled={busy} value={password} onChange={/* Capture the replacement password. */ event => setPassword(event.target.value)} /><button type="button" onClick={/* Toggle password visibility without submitting the form. */ () => setVisible(!visible)} aria-label={visible ? "Hide password" : "Show password"}>{visible ? "Hide" : "Show"}</button></div></label>
          <label>Confirm password<input type={visible ? "text" : "password"} autoComplete="new-password" minLength={12} maxLength={72} required disabled={busy} value={confirmation} onChange={/* Confirm the replacement password. */ event => setConfirmation(event.target.value)} /></label>
          <p className="auth-footnote">Use at least 12 characters. Codes expire after 10 minutes and allow five incorrect attempts.</p>
          <button className="auth-button primary" disabled={busy || code.length !== 6}>{busy ? "Updating password..." : "Reset password"}</button>
        </form>
        <button className="auth-text-link" disabled={busy || cooldown > 0} onClick={/* Request another code after the cooldown. */ () => void requestCode()}>{cooldown ? `Request another code in ${cooldown}s` : "Send a new reset code"}</button>
        <button className="auth-text-link" disabled={busy} onClick={changeEmail}>Use a different email</button>
      </>}
      <p className="auth-footnote">If email recovery is unavailable, contact your academy administrator.</p>
      <Link className="auth-text-link" to="/login">Back to sign in</Link>
    </>}
  </section></main>;
}
