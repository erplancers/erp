import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { Password } from "primereact/password";
import { Message } from "primereact/message";
import { useAuth } from "../auth";
import { api } from "../api";

export function LoginPage() {
  const { credentials, login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  if (credentials) return <Navigate to="/" replace />;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    const next = { username: username.trim(), password };
    if (!next.username || !next.password) { setError("Username and password are required."); return; }
    try {
      await api.resources(next);
      login(next);
      navigate("/", { replace: true });
    } catch {
      setError("Authentication failed or the ERP API is unavailable.");
    }
  }

  return (
    <div className="login-page">
      <div className="login-glow" />
      <form className="login-card" onSubmit={submit}>
        <div className="brand-mark large">E</div>
        <div className="eyebrow">ERPLANCERS ERP</div>
        <h1>Welcome back</h1>
        <p>Secure access to your ERP operations and BI workspace.</p>
        {error && <Message severity="error" text={error} />}
        <label>Username<InputText value={username} onChange={e => setUsername(e.target.value)} autoFocus /></label>
        <label>Password<Password value={password} onChange={e => setPassword(e.target.value)} feedback={false} toggleMask /></label>
        <Button label="Sign in" icon="pi pi-arrow-right" iconPos="right" type="submit" className="w-full" />
        <small>Use HTTPS in production. Credentials are retained only for the current browser session.</small>
      </form>
    </div>
  );
}
