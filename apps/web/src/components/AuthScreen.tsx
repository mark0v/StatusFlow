import statusFlowLogo from "../assets/statusflow-logo.svg";

interface Props {
  email: string;
  password: string;
  isAuthenticating: boolean;
  authError: string | null;
  onEmailChange: (email: string) => void;
  onPasswordChange: (password: string) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
}

export function AuthScreen({
  email,
  password,
  isAuthenticating,
  authError,
  onEmailChange,
  onPasswordChange,
  onSubmit
}: Props) {
  return (
    <main className="shell shell-auth">
      <section className="auth-panel">
        <img
          alt="StatusFlow operator console"
          className="hero-logo auth-logo"
          src={statusFlowLogo}
        />
        <p className="eyebrow">Live sign in</p>
        <h1>Access the live workflow console</h1>
        <p className="lead">
          Sign in with a seeded operator or customer account to enter the
          shared workflow used across web and mobile.
        </p>

        <form className="auth-form" onSubmit={onSubmit}>
          <label className="field">
            <span>Email</span>
            <input
              autoComplete="username"
              value={email}
              onChange={(event) => onEmailChange(event.target.value)}
            />
          </label>
          <label className="field">
            <span>Password</span>
            <input
              autoComplete="current-password"
              type="password"
              value={password}
              onChange={(event) => onPasswordChange(event.target.value)}
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

        <div className="auth-hint">
          <strong>Seed operator</strong>
          <span>operator@example.com / operator123</span>
        </div>
        <div className="auth-hint">
          <strong>Seed customer</strong>
          <span>customer@example.com / customer123</span>
        </div>
      </section>
    </main>
  );
}
