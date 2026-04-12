import statusFlowLogo from "../assets/statusflow-logo.svg";
import { API_BASE_URL } from "../data/webApi";
import type { AuthSession } from "../data/webTypes";

interface Props {
  session: AuthSession;
  orderCount: number;
  isLoading: boolean;
  userInitials: string;
  isUserMenuOpen: boolean;
  onUserMenuToggle: () => void;
  onLogout: () => void;
}

export function Hero({
  session,
  orderCount,
  isLoading,
  userInitials,
  isUserMenuOpen,
  onUserMenuToggle,
  onLogout
}: Props) {
  return (
    <section className="hero">
      <div className="hero-top">
        <div className="hero-brand">
          <img
            alt="StatusFlow operator console"
            className="hero-logo"
            src={statusFlowLogo}
          />
        </div>
        <div className="hero-actions">
          <span className="hero-order-count">
            {isLoading ? "…" : `${orderCount} orders`}
          </span>
          <div className="user-menu-wrap">
            <button
              className="user-avatar-btn"
              onClick={onUserMenuToggle}
              aria-label="User menu"
              aria-expanded={isUserMenuOpen}
              type="button"
            >
              <span className="user-avatar-initials">{userInitials}</span>
            </button>
            {isUserMenuOpen ? (
              <div className="user-dropdown">
                <div className="user-dropdown-info">
                  <span className="user-dropdown-name">{session.user.name}</span>
                  <span className="user-dropdown-role">{session.user.role}</span>
                </div>
                <hr className="user-dropdown-sep" />
                <a
                  className="user-dropdown-item"
                  href={`${API_BASE_URL}/docs`}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  API docs
                </a>
                <div className="user-dropdown-item user-dropdown-endpoint">
                  <span className="user-dropdown-endpoint-label">Endpoint</span>
                  <span className="user-dropdown-endpoint-value">{API_BASE_URL}</span>
                </div>
                <hr className="user-dropdown-sep" />
                <button
                  className="user-dropdown-item user-dropdown-item-danger"
                  onClick={() => {
                    onLogout();
                  }}
                  type="button"
                >
                  Sign out
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}
