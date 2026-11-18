import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "./authContext";

// Gate protected routes until identity is known and redirect users to their permitted area.
export default function RequireSession({ permission, athleteOnly = false }: { permission?: string; athleteOnly?: boolean }) {
  const auth = useAuth();
  const location = useLocation();
  if (auth.loading) return <main className="auth-status" role="status">Checking your session…</main>;
  if (auth.error) return <main className="auth-status"><p role="alert">{auth.error}</p><button onClick={/* Perform the selected button action. */ () => void auth.refresh()}>Try again</button></main>;
  if (!auth.session?.user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (athleteOnly && auth.session.user.role !== "ATHLETE") return <Navigate to="/" replace />;
  if (!athleteOnly && auth.session.user.role === "ATHLETE") return <Navigate to="/portal" replace />;
  if (permission && !auth.can(permission)) return <Navigate to="/" replace />;
  return <Outlet />;
}
