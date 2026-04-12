import { useState } from "react";
import statusFlowLogo from "../assets/statusflow-logo.svg";

interface Props {
  email: string;
  password: string;
  isAuthenticating: boolean;
  authError: string | null;
  onEmailChange: (email: string) => void;
  onPasswordChange: (password: string) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onRoleSignIn: (role: "operator" | "customer") => void;
}

export function AuthScreen({
  email,
  password,
  isAuthenticating,
  authError,
  onEmailChange,
  onPasswordChange,
  onSubmit,
  onRoleSignIn
}: Props) {
  const [showManualLogin, setShowManualLogin] = useState(false);

  return (
    <main className="shell shell-auth">
      <section className="auth-panel">
        <img
          alt="StatusFlow"
          className="hero-logo auth-logo"
          src={statusFlowLogo}
        />

        <div className="auth-header">
          <p className="eyebrow">Welcome to StatusFlow</p>
          <h1>Choose how you'll be working today</h1>
        </div>

        <div className="role-cards">
          <button
            className="role-card role-card-operator"
            onClick={() => onRoleSignIn("operator")}
            disabled={isAuthenticating}
            type="button"
          >
            <div className="role-card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M22 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
            <div className="role-card-body">
              <h3>Operator</h3>
              <p>Review the queue, update statuses, and leave context for the team.</p>
            </div>
          </button>

          <button
            className="role-card role-card-customer"
            onClick={() => onRoleSignIn("customer")}
            disabled={isAuthenticating}
            type="button"
          >
            <div className="role-card-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
            </div>
            <div className="role-card-body">
              <h3>Customer</h3>
              <p>Track your requests and see status updates in real time.</p>
            </div>
          </button>
        </div>

        {!showManualLogin ? (
          <button
            className="auth-toggle-manual"
            onClick={() => setShowManualLogin(true)}
            type="button"
          >
            Sign in with email instead
          </button>
        ) : (
          <>
            <form className="auth-form" onSubmit={onSubmit}>
              <label className="field">
                <span>Email</span>
                <input
                  autoComplete="username"
                  value={email}
                  onChange={(event) => onEmailChange(event.target.value)}
                  placeholder="operator@example.com"
                />
              </label>
              <label className="field">
                <span>Password</span>
                <input
                  autoComplete="current-password"
                  type="password"
                  value={password}
                  onChange={(event) => onPasswordChange(event.target.value)}
                  placeholder="operator123"
                />
              </label>
              <button className="primary-action" disabled={isAuthenticating} type="submit">
                {isAuthenticating ? "Signing in..." : "Sign in"}
              </button>
            </form>

            {authError ? (
              <div className="feedback-card feedback-error compact">
                <span className="feedback-eyebrow">Auth</span>
                <strong>Unable to sign in.</strong>
                <p>{authError}</p>
              </div>
            ) : null}
          </>
        )}
      </section>
    </main>
  );
}
