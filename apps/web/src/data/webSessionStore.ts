import type { AuthSession } from "./webTypes";

const SESSION_STORAGE_KEY = "statusflow.web.session";

export function readStoredSession() {
  const raw = window.localStorage.getItem(SESSION_STORAGE_KEY);

  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
    return null;
  }
}

export function persistSession(session: AuthSession | null) {
  if (session) {
    window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    return;
  }

  window.localStorage.removeItem(SESSION_STORAGE_KEY);
}
