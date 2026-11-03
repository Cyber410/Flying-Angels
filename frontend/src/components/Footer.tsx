// Shared academy, team, and capstone credits.
import "./Footer.css";

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="site-footer-content">
        <p className="site-footer-credit"><span className="site-footer-intro">Designed and developed for</span> <strong>Flying Angels Academy</strong> by <span className="site-footer-team">Solutions and Co.</span></p>
        <span className="site-footer-divider" aria-hidden="true">|</span>
        <div className="site-footer-project-details">
          <p className="site-footer-project">Sheridan 2026 Capstone Project</p>
          <p className="site-footer-copyright">© 2026 Solutions and Co.</p>
        </div>
      </div>
    </footer>
  );
}
