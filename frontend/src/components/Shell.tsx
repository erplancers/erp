import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Button } from "primereact/button";
import { useAuth } from "../auth";

export function Shell() {
  const { credentials, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">E</div>
          <div><strong>Erplancers</strong><span>Enterprise ERP</span></div>
        </div>
        <nav className="nav">
          <NavLink to="/" end><i className="pi pi-home" />Dashboard</NavLink>
          <NavLink to="/resources"><i className="pi pi-database" />Data Explorer</NavLink>
        </nav>
        <div className="sidebar-footer">
          <div className="user-chip"><i className="pi pi-user" /><span>{credentials?.username}</span></div>
          <Button label="Sign out" icon="pi pi-sign-out" text onClick={logout} />
        </div>
      </aside>
      <main className="main">
        <header className="topbar">
          <div>
            <div className="eyebrow">ERP CONTROL CENTER</div>
            <div className="page-title">Operations & Intelligence</div>
          </div>
          <Button icon="pi pi-refresh" rounded text aria-label="Refresh" onClick={() => navigate(0)} />
        </header>
        <section className="content"><Outlet /></section>
      </main>
    </div>
  );
}
