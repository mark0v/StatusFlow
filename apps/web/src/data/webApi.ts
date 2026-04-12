import type {
  AuthSession,
  DashboardData,
  OrderDetail,
  OrderStatus,
  UserSummary
} from "./webTypes";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000";
export const AUTH_REQUIRED_ERROR = "AUTH_REQUIRED";
export const FORBIDDEN_ERROR = "FORBIDDEN";
export const NETWORK_UNAVAILABLE_ERROR = "NETWORK_UNAVAILABLE";

export function isAuthFailureMessage(message: string) {
  return message === AUTH_REQUIRED_ERROR || message === FORBIDDEN_ERROR;
}

export function isOfflineQueueableError(message: string) {
  return message === NETWORK_UNAVAILABLE_ERROR;
}

async function readJson<T>(path: string, token?: string, init?: RequestInit) {
  const headers = new Headers(init?.headers ?? {});

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers
    });
  } catch {
    throw new Error(NETWORK_UNAVAILABLE_ERROR);
  }

  if (!response.ok) {
    const message =
      response.status === 401
        ? AUTH_REQUIRED_ERROR
        : response.status === 403
          ? FORBIDDEN_ERROR
          : `API returned ${response.status}`;
    throw new Error(message);
  }

  return (await response.json()) as T;
}

export async function login(authForm: { email: string; password: string }) {
  return readJson<AuthSession>("/auth/login", undefined, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(authForm)
  });
}

export async function loadDashboardData(accessToken: string, signal?: AbortSignal) {
  const [orders, users, lifecycle] = await Promise.all([
    readJson<DashboardData["orders"]>("/orders", accessToken, { signal }),
    readJson<DashboardData["users"]>("/users", accessToken, { signal }),
    readJson<DashboardData["lifecycle"]>("/order-status-lifecycle", accessToken, { signal })
  ]);

  return {
    orders,
    users,
    lifecycle
  };
}

export async function loadOrderDetail(orderId: string, accessToken: string, signal?: AbortSignal) {
  return readJson<OrderDetail>(`/orders/${orderId}`, accessToken, { signal });
}

export async function createOrder(
  accessToken: string,
  input: { title: string; description: string; customer_id: string }
) {
  return readJson("/orders", accessToken, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(input)
  });
}

export async function transitionOrderStatus(
  accessToken: string,
  orderId: string,
  input: { changed_by_id: string; to_status: OrderStatus; reason: string }
) {
  return readJson(`/orders/${orderId}/status-transitions`, accessToken, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(input)
  });
}

export async function addOrderComment(
  accessToken: string,
  orderId: string,
  input: { author_id: string; body: string }
) {
  return readJson(`/orders/${orderId}/comments`, accessToken, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(input)
  });
}

export type { UserSummary };
