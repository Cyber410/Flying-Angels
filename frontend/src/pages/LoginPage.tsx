import { useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/authContext";
import "./AuthPages.css";

// Authenticate users and send athletes and staff to their appropriate application area.
export default function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  // Establish the session and restore an internal destination when it is appropriate.
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError("");
    try {
      const session = await auth.login(email.trim(), password);
      const from: unknown = location.state?.from;
      const target = typeof from === "string" && from.startsWith("/") && !from.startsWith("//") && !from.startsWith("/login") && !from.startsWith("/activate") ? from : "/";
      navigate(session.user?.role === "ATHLETE" ? "/portal" : target, { replace: true });
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to sign in."); }
    finally { setBusy(false); }
  }

  if (!auth.loading && auth.session?.user) return <Navigate to={auth.session.user.role === "ATHLETE" ? "/portal" : "/"} replace />;
  return <main className="auth-page"><section className="auth-card" aria-labelledby="login-title">
    <p className="auth-brand">FLYING ANGELS</p><p className="auth-eyebrow">Academy management</p>
    <h1 id="login-title">Welcome back</h1><p className="auth-intro">Sign in to access your academy account.</p>
    {error && <p className="auth-error" role="alert">{error}</p>}
    <form className="auth-form" onSubmit={submit} aria-busy={busy}>
      <label>Email address<input type="email" autoComplete="username" required maxLength={254} value={email} disabled={busy} onChange={/* Update the email value from this control. */ (event) => setEmail(event.target.value)} /></label>
      <label>Password<div className="auth-password"><input type={visible ? "text" : "password"} autoComplete="current-password" required value={password} disabled={busy} onChange={/* Update the password value from this control. */ (event) => setPassword(event.target.value)} /><button type="button" onClick={/* Update visible for the selected action. */ () => setVisible(!visible)} aria-label={visible ? "Hide password" : "Show password"}>{visible ? "Hide" : "Show"}</button></div></label>
      <button className="auth-button primary" disabled={busy || auth.loading}>{busy ? "Signing in…" : auth.loading ? "Checking session…" : "Sign in"}</button>
    </form><Link className="auth-text-link" to="/forgot-password">Forgot password?</Link><p className="auth-footnote">New to Flying Angels? Ask your academy administrator for an invitation.</p>
  </section></main>;
}
