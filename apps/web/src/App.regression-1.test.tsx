import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

type OrderStatus =
  | "new"
  | "in_review"
  | "approved"
  | "rejected"
  | "fulfilled"
  | "cancelled";

type User = {
  id: string;
  email: string;
  name: string;
  role: "customer" | "operator";
};

type AuthSession = {
  access_token: string;
  token_type: "bearer";
  expires_in_seconds: number;
  user: User;
};

const users: User[] = [
  {
    id: "user-customer",
    email: "customer@example.com",
    name: "Alex Morgan",
    role: "customer"
  },
  {
    id: "user-operator",
    email: "operator@example.com",
    name: "Riley Chen",
    role: "operator"
  }
];

const orders = [
  {
    id: "order-1",
    code: "SF-1001",
    title: "Replace display unit",
    customer_name: "Alex Morgan",
    status: "new" satisfies OrderStatus,
    updated_at: "2026-04-10T14:31:00Z"
  }
];

const detail = {
  ...orders[0],
  description: "Order detail for Replace display unit.",
  comments: [
    {
      id: "comment-1",
      body: "Initial operator note.",
      created_at: "2026-04-10T14:40:00Z",
      author: users[1]
    }
  ],
  history: [
    {
      id: "history-1",
      from_status: null,
      to_status: "new" satisfies OrderStatus,
      reason: "Created from the seeded queue.",
      changed_at: "2026-04-10T14:31:00Z",
      changed_by: users[1]
    }
  ]
};

const lifecycle = {
  statuses: ["new", "in_review", "approved", "rejected", "fulfilled", "cancelled"] satisfies OrderStatus[],
  allowed_transitions: {
    new: ["in_review", "cancelled"],
    in_review: ["approved", "rejected", "cancelled"],
    approved: ["fulfilled", "cancelled"],
    rejected: [],
    fulfilled: [],
    cancelled: []
  } satisfies Record<OrderStatus, OrderStatus[]>
};

function jsonResponse(payload: unknown, init?: ResponseInit) {
  return new Response(JSON.stringify(payload), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init
  });
}

function installFetchMock() {
  const sessionsByEmail = new Map<string, AuthSession>([
    [
      "operator@example.com",
      {
        access_token: "operator-token",
        token_type: "bearer",
        expires_in_seconds: 43200,
        user: users[1]
      }
    ],
    [
      "customer@example.com",
      {
        access_token: "customer-token",
        token_type: "bearer",
        expires_in_seconds: 43200,
        user: users[0]
      }
    ]
  ]);

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const requestUrl = typeof input === "string" ? input : input.toString();
    const path = requestUrl.replace("http://localhost:8000", "");
    const method = init?.method ?? "GET";
    const headers = new Headers(init?.headers);
    const isAuthed = Array.from(sessionsByEmail.values()).some(
      (session) => headers.get("Authorization") === `Bearer ${session.access_token}`
    );

    if (path === "/auth/login" && method === "POST") {
      const body = JSON.parse(String(init?.body ?? "{}")) as { email: string; password: string };
      const session = sessionsByEmail.get(body.email);
      if (session && body.password === `${session.user.role}123`) {
        return jsonResponse(session);
      }
      return jsonResponse({ detail: "Invalid email or password." }, { status: 401 });
    }

    if (!isAuthed) {
      return jsonResponse({ detail: "Authentication required." }, { status: 401 });
    }

    if (path === "/orders" && method === "GET") {
      return jsonResponse(orders);
    }

    if (path === "/users" && method === "GET") {
      return jsonResponse(users);
    }

    if (path === "/order-status-lifecycle" && method === "GET") {
      return jsonResponse(lifecycle);
    }

    if (path === `/orders/${orders[0].id}` && method === "GET") {
      return jsonResponse(detail);
    }

    return jsonResponse({ detail: `Unhandled request: ${method} ${path}` }, { status: 404 });
  });

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("App customer regression coverage", () => {
  beforeEach(() => {
    installFetchMock();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
  });

  it("shows customer-specific queue copy and hides operator notes", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.clear(screen.getByLabelText("Email"));
    await user.type(screen.getByLabelText("Email"), "customer@example.com");
    await user.clear(screen.getByLabelText("Password"));
    await user.type(screen.getByLabelText("Password"), "customer123");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    await screen.findByRole("heading", { name: "Track the live workflow" });
    await screen.findByRole("heading", { name: "Track your orders across the live workflow" });

    expect(screen.queryByRole("heading", { name: "Operate the live workflow" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Review and move orders forward" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Queue notes" })).toBeInTheDocument();
    expect(screen.getByText("Comments are available in operator mode.")).toBeInTheDocument();
    expect(screen.queryByText("Initial operator note.")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Post comment" })).not.toBeInTheDocument();
  });
});
