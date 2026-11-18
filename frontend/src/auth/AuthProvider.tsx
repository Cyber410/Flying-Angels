import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import type { Session } from "../types/auth";
import { getSession, signIn, signOut } from "../services/authService";
import { AuthContext } from "./authContext";

// Maintain the server session and clear protected content when authentication expires.
export default function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Reload session identity after startup or a recoverable connection failure.
  const refresh = useCallback(/* Reload the server session when this callback is requested. */ async () => {
    setLoading(true); setError("");
    try { setSession(await getSession()); }
    catch (failure) { setSession(null); setError(failure instanceof Error ? failure.message : "Unable to check your session."); }
    finally { setLoading(false); }
  }, []);

  useEffect(/* Synchronize this view with its dependencies and clean up pending work. */ () => {
    let ignore = false;
    getSession().then(/* Apply the successful request result to the current operation. */ next => { if (!ignore) setSession(next); })
      .catch(/* Report the request failure without discarding the current view. */ failure => { if (!ignore) setError(failure instanceof Error ? failure.message : "Unable to check your session."); })
      .finally(/* Clear the busy state after the request completes. */ () => { if (!ignore) setLoading(false); });
    // Immediately discard authenticated UI when an API call reports an expired session.
    const expired = () => { setSession({ authenticationRequired: true, user: null, permissions: [] }); };
    window.addEventListener("academy:session-expired", expired);
    return /* Stop pending work and release this effect when it is cleaned up. */ () => { ignore = true; window.removeEventListener("academy:session-expired", expired); };
  }, []);

  // Replace identity only after the server has accepted the supplied credentials.
  async function login(email: string, password: string) {
    const next = await signIn(email, password); setError(""); setSession(next); return next;
  }

  // Clear cached identity after the server invalidates its session.
  async function logout() {
    await signOut(); setError(""); setSession({ authenticationRequired: true, user: null, permissions: [] });
  }

  // Keep permission checks aligned with the capabilities returned by the backend.
  function can(permission: string) { return session?.permissions.includes(permission) ?? false; }

  return <AuthContext.Provider value={{ session, loading, error, refresh, login, logout, can }}>{children}</AuthContext.Provider>;
}
