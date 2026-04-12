import { Component, type ReactNode } from "react";

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div style={{
          display: "grid",
          placeItems: "center",
          minHeight: "100vh",
          padding: "2rem",
          textAlign: "center"
        }}>
          <div style={{ maxWidth: "480px" }}>
            <h1 style={{ color: "#ef4444", marginBottom: "1rem" }}>Something went wrong</h1>
            <p style={{ color: "#b8c5d6", marginBottom: "1.5rem" }}>
              {this.state.error?.message || "An unexpected error occurred."}
            </p>
            <button
              onClick={() => window.location.reload()}
              style={{
                padding: "12px 24px",
                borderRadius: "999px",
                border: "none",
                background: "linear-gradient(135deg, #4f7cff, #1fcaa5)",
                color: "#081120",
                fontWeight: 700,
                cursor: "pointer"
              }}
            >
              Reload page
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
