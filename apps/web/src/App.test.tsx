import { render, screen, waitFor, within } from "@testing-library/react";
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

type Order = {
  id: string;
  code: string;
  title: string;
  customer_name: string;
  status: OrderStatus;
  updated_at: string;
};

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

type Lifecycle = {
  statuses: OrderStatus[];
  allowed_transitions: Record<OrderStatus, OrderStatus[]>;
};

const users: User[] = [
  {
    id: "user-customer",
    email: "alex@example.com",
    name: "Alex Morgan",
    role: "customer"
  },
  {
    id: "user-operator",
    email: "ops@example.com",
    name: "Taylor Shaw",
    role: "operator"
  }
];

const lifecycle: Lifecycle = {
  statuses: ["new", "in_review", "approved", "rejected", "fulfilled", "cancelled"],
  allowed_transitions: {
    new: ["in_review", "cancelled"],
    in_review: ["approved", "rejected", "cancelled"],
    approved: ["fulfilled", "cancelled"],
    rejected: [],
    fulfilled: [],
    cancelled: []
  }
};

const baseOrders = (): Order[] => [
  {
    id: "order-1",
    code: "SF-1001",
    title: "Replace display unit",
    customer_name: "Alex Morgan",
    status: "new",
    updated_at: "2026-04-10T14:31:00Z"
  },
  {
    id: "order-2",
    code: "SF-1002",
    title: "Verify warranty documents",
    customer_name: "Alex Morgan",
    status: "in_review",
    updated_at: "2026-04-10T15:31:00Z"
  },
  {
    id: "order-3",
    code: "SF-1003",
    title: "Prepare approved shipment",
    customer_name: "Alex Morgan",
    status: "approved",
    updated_at: "2026-04-10T16:31:00Z"
  }
];

function jsonResponse(payload: unknown, init?: ResponseInit) {
  return new Response(JSON.stringify(payload), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init
  });
}

function installFetchMock(initialOrders = baseOrders()) {
  let orders = [...initialOrders];
  let orderCounter = 1000 + orders.length;
  const session: AuthSession = {
    access_token: "test-token",
    token_type: "bearer",
    expires_in_seconds: 43200,
    user: users[1]
  };

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const requestUrl = typeof input === "string" ? input : input.toString();
    const path = requestUrl.replace("http://localhost:8000", "");
    const method = init?.method ?? "GET";
    const headers = new Headers(init?.headers);
    const isAuthed = headers.get("Authorization") === `Bearer ${session.access_token}`;

    if (path === "/auth/login" && method === "POST") {
      const body = JSON.parse(String(init?.body ?? "{}")) as {
        email: string;
        password: string;
      };

      if (body.email === "operator@example.com" && body.password === "operator123") {
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

    if (path === "/orders" && method === "POST") {
      const body = JSON.parse(String(init?.body ?? "{}")) as {
        title: string;
        description: string;
        customer_id: string;
      };

      orderCounter += 1;
      orders = [
        {
          id: `order-${orderCounter}`,
          code: `SF-${orderCounter}`,
          title: body.title,
          customer_name: users[0].name,
          status: "new",
          updated_at: "2026-04-10T17:45:00Z"
        },
        ...orders
      ];

      return jsonResponse({ ok: true }, { status: 201 });
    }

    const transitionMatch = path.match(/^\/orders\/([^/]+)\/status-transitions$/);
    if (transitionMatch && method === "POST") {
      const body = JSON.parse(String(init?.body ?? "{}")) as {
        to_status: OrderStatus;
      };

      orders = orders.map((order) =>
        order.id === transitionMatch[1]
          ? {
              ...order,
              status: body.to_status,
              updated_at: "2026-04-10T18:00:00Z"
            }
          : order
      );

      return jsonResponse({ ok: true }, { status: 201 });
    }

    return jsonResponse({ message: `Unhandled request: ${method} ${path}` }, { status: 404 });
  });

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function getStatusCard(label: string) {
  const summaryStrip = document.querySelector(".summary-strip");

  if (!summaryStrip) {
    throw new Error("Unable to find summary strip");
  }

  const statusLabel = within(summaryStrip as HTMLElement).getByText(label);
  const card = statusLabel.closest("article");

  if (!card) {
    throw new Error(`Unable to find status card for ${label}`);
  }

  return card;
}

describe("App", () => {
  beforeEach(() => {
    installFetchMock();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
  });

  async function signIn(user = userEvent.setup()) {
    render(<App />);

    await user.clear(screen.getByLabelText("Email"));
    await user.type(screen.getByLabelText("Email"), "operator@example.com");
    await user.clear(screen.getByLabelText("Password"));
    await user.type(screen.getByLabelText("Password"), "operator123");
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    await screen.findByText("Replace display unit");

    return user;
  }

  it("renders all status counters, including zero-value statuses", async () => {
    await signIn();

    expect(within(getStatusCard("New")).getByText("1")).toBeInTheDocument();
    expect(within(getStatusCard("In review")).getByText("1")).toBeInTheDocument();
    expect(within(getStatusCard("Approved")).getByText("1")).toBeInTheDocument();
    expect(within(getStatusCard("Rejected")).getByText("0")).toBeInTheDocument();
    expect(within(getStatusCard("Fulfilled")).getByText("0")).toBeInTheDocument();
    expect(within(getStatusCard("Cancelled")).getByText("0")).toBeInTheDocument();
  });

  it("opens and closes the status filter dropdown on outside click", async () => {
    const user = await signIn();

    await user.click(screen.getByRole("button", { name: "Filter" }));
    expect(screen.getByLabelText("Cancelled")).toBeInTheDocument();

    await user.click(document.body);

    await waitFor(() => {
      expect(screen.queryByLabelText("Cancelled")).not.toBeInTheDocument();
    });
  });

  it("creates a new order from the reveal form", async () => {
    const user = await signIn();

    await user.click(screen.getByRole("button", { name: "Create order" }));
    await user.type(screen.getByLabelText("Order title"), "Inspect return shipment");
    await user.type(
      screen.getByLabelText("Description"),
      "Validate customer notes before moving forward."
    );
    await user.click(screen.getByRole("button", { name: /^Create order$/ }));

    await screen.findByText("Inspect return shipment");
    expect(within(getStatusCard("New")).getByText("2")).toBeInTheDocument();
  });

  it("opens and closes row status actions, then updates the visible status", async () => {
    const user = await signIn();

    const rowTitle = await screen.findByText("Replace display unit");
    const row = rowTitle.closest("tr");

    if (!row) {
      throw new Error("Unable to find table row for Replace display unit");
    }

    await user.click(within(row).getByRole("button", { name: "Change status" }));
    expect(screen.getByRole("button", { name: "In review" })).toBeInTheDocument();

    await user.click(document.body);

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "In review" })).not.toBeInTheDocument();
    });

    await user.click(within(row).getByRole("button", { name: "Change status" }));
    await user.click(screen.getByRole("button", { name: "In review" }));

    await waitFor(() => {
      expect(within(row).getByText("In review")).toBeInTheDocument();
    });
  });
});
