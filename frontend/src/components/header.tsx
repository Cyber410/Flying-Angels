import "./header.css";

function Header() {
  return (
    <header className="site-header">
      <div className="site-header-content">
        <div className="site-brand">
          <div className="site-brand-mark">
            FA
          </div>

          <span className="site-brand-name">
            Flying Angels Track and Field Academy
          </span>
        </div>

        <div className="site-admin">
          <span className="site-admin-dot"></span>
          <span>Admin</span>
        </div>
      </div>
    </header>
  );
}

export default Header;