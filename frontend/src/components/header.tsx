import { Link, NavLink, useMatch } from "react-router-dom";
import "./header.css";

function Header() {
  const isAthleteDirectory = useMatch("/");
  const isAthleteProfile = useMatch("/athletes/:athleteId");
  const isAthletesActive = Boolean(isAthleteDirectory || isAthleteProfile);

  return (
    <header className="site-header">
      <div className="site-header-content">
        <Link className="site-brand-name" to="/">
          FLYING ANGELS
        </Link>

        <nav className="site-nav" aria-label="Main navigation">
          <Link
            to="/"
            className={`site-nav-link${isAthletesActive ? " active" : ""}`}
            aria-current={isAthletesActive ? "page" : undefined}
          >
            Athletes
          </Link>
          <NavLink to="/meets" className="site-nav-link">Meets</NavLink>
          <NavLink to="/results" className="site-nav-link">Results</NavLink>
          {/* Enable /import when the team's Import page is integrated. */}
          <span
            className="site-nav-link site-nav-disabled"
            role="link"
            aria-disabled="true"
            title="Import Athletes is coming soon"
          >
            Import
          </span>
        </nav>

        <div className="site-admin">
          <span className="site-admin-dot"></span>
          <span>Admin</span>
        </div>
      </div>
    </header>
  );
}

export default Header;
