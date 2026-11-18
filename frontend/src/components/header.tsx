// Shared branding and navigation, including the active section.
import { Link, NavLink, useMatch } from "react-router-dom";
import "./header.css";
import { useState } from "react";
import { useOptionalAuth } from "../auth/authContext";

// Render academy branding and links to each main section.
function Header() {
  const auth = useOptionalAuth();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const athlete = auth?.session?.user?.role === "ATHLETE";
  const isAthleteDirectory = useMatch("/");
  const isAthleteProfile = useMatch("/athletes/:athleteId");
  const isAthletesActive = Boolean(isAthleteDirectory || isAthleteProfile);

  return (
    <header className="site-header">
      <div className="site-header-content">
        <Link className="site-brand-name" to={athlete ? "/portal" : "/"}>
          FLYING ANGELS
        </Link>

        <nav className="site-nav" aria-label="Main navigation">
          {athlete ? <><NavLink to="/portal" end className="site-nav-link">My Meets</NavLink><NavLink to="/portal/profile" className="site-nav-link">My Profile</NavLink></> : <><Link
            to="/"
            className={`site-nav-link${isAthletesActive ? " active" : ""}`}
            aria-current={isAthletesActive ? "page" : undefined}
          >
            Athletes
          </Link>
          <NavLink to="/meets" className="site-nav-link">Meets</NavLink>
          <NavLink to="/results" className="site-nav-link">Results</NavLink>
          <NavLink to="/rankings" className="site-nav-link">Rankings</NavLink>
          <NavLink to="/availability" className="site-nav-link">Availability</NavLink>
          <NavLink to="/import" className="site-nav-link">Import</NavLink>
          {auth?.can("MANAGE_USERS") && <NavLink to="/users" className="site-nav-link">Users</NavLink>}
          </>}
        </nav>

        <div className="site-admin">
          <span className="site-admin-dot"></span>
          <span>{auth?.session?.user?.name ?? "Admin"}</span>
          {auth && <button className="site-signout" disabled={busy} onClick={/* Update busy for the selected action. */ async () => {
            setBusy(true); setError("");
            try { await auth.logout(); } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to sign out."); }
            finally { setBusy(false); }
          }}>{busy ? "Signing out…" : "Sign out"}</button>}
          {error && <span role="alert">{error}</span>}
        </div>
      </div>
    </header>
  );
}

export default Header;
