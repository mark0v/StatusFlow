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

type OrderComment = {
  id: string;
  body: string;
  created_at: string;
  author: User;
};

type OrderHistoryEvent = {
  id: string;
  from_status: OrderStatus | null;
  to_status: OrderStatus;
  reason: string;
  changed_at: string;
  changed_by: User;
};

type OrderDetail = Order & {
  description: string;
  comments: OrderComment[];
  history: OrderHistoryEvent[];
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

function installFetchMock(
  initialOrders = baseOrders(),
  options?: {
    failingDetailIds?: string[];
    failingOrdersGetCount?: number;
    networkFailureRules?: Array<{
      method?: string;
      pathPattern: RegExp;
      times?: number;
    }>;
    transientFailingDetailIds?: string[];
  }
) {
  let orders = [...initialOrders];
  let orderCounter = 1000 + orders.length;
  const failingDetailIds = new Set(options?.failingDetailIds ?? []);
  let failingOrdersGetCount = options?.failingOrdersGetCount ?? 0;
  const networkFailureRules =
    options?.networkFailureRules?.map((rule) => ({
      ...rule,
      method: rule.method ?? "GET",
      remaining: rule.times ?? 1
    })) ?? [];
  const transientFailingDetailIds = new Map(
    (options?.transientFailingDetailIds ?? []).map((orderId) => [orderId, 1])
  );
  const details = new Map<string, OrderDetail>(
    orders.map((order, index) => [
      order.id,
      {
        ...order,
        description: `Order detail for ${order.title}.`,
        comments: index === 0
          ? [
              {
                id: "comment-1",
                body: "Initial operator note.",
                created_at: "2026-04-10T14:40:00Z",
                author: users[1]
              }
            ]
          : [],
        history: [
          {
            id: `history-${order.id}`,
            from_status: null,
            to_status: order.status,
            reason: "Created from the seeded queue.",
            changed_at: order.updated_at,
            changed_by: users[1]
          }
        ]
      }
    ])
  );
  const sessionsByEmail = new Map<string, AuthSession>([
    [
      "operator@example.com",
      {
        access_token: "operator-token",
        token_type: "bearer",
        expires_in_seconds: 43200,
        user: {
          ...users[1],
          email: "operator@example.com"
        }
      }
    ],
    [
      "customer@example.com",
      {
        access_token: "customer-token",
        token_type: "bearer",
        expires_in_seconds: 43200,
        user: {
          ...users[0],
          email: "customer@example.com"
        }
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
    const matchingFailureRule = networkFailureRules.find(
      (rule) => rule.method === method && rule.pathPattern.test(path) && rule.remaining > 0
    );

    if (matchingFailureRule) {
      matchingFailureRule.remaining -= 1;
      throw new Error("Failed to fetch");
    }

    if (path === "/auth/login" && method === "POST") {
      const body = JSON.parse(String(init?.body ?? "{}")) as {
        email: string;
        password: string;
      };

      if (
        (body.email === "operator@example.com" && body.password === "operator123") ||
        (body.email === "customer@example.com" && body.password === "customer123")
      ) {
        return jsonResponse(sessionsByEmail.get(body.email));
      }

      return jsonResponse({ detail: "Invalid email or password." }, { status: 401 });
    }

    if (!isAuthed) {
      return jsonResponse({ detail: "Authentication required." }, { status: 401 });
    }

    if (path === "/orders" && method === "GET") {
      if (failingOrdersGetCount > 0) {
        failingOrdersGetCount -= 1;
        return jsonResponse({ detail: "Dashboard snapshot is temporarily unavailable." }, { status: 500 });
      }

      return jsonResponse(orders);
    }

    const orderMatch = path.match(/^\/orders\/([^/]+)$/);
    if (orderMatch && method === "GET") {
      if (failingDetailIds.has(orderMatch[1])) {
        return jsonResponse({ detail: "Selected order detail is temporarily unavailable." }, { status: 500 });
      }

      const remainingTransientFailures = transientFailingDetailIds.get(orderMatch[1]);
      if ((remainingTransientFailures ?? 0) > 0) {
        transientFailingDetailIds.set(orderMatch[1], remainingTransientFailures! - 1);
        return jsonResponse({ detail: "Selected order detail is temporarily unavailable." }, { status: 500 });
      }

      const detail = details.get(orderMatch[1]);
      return detail
        ? jsonResponse(detail)
        : jsonResponse({ detail: "Not found." }, { status: 404 });
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

      details.set(`order-${orderCounter}`, {
        id: `order-${orderCounter}`,
        code: `SF-${orderCounter}`,
        title: body.title,
        description: body.description,
        customer_name: users[0].name,
        status: "new",
        updated_at: "2026-04-10T17:45:00Z",
        comments: [],
        history: [
          {
            id: `history-${orderCounter}`,
            from_status: null,
            to_status: "new",
            reason: "Created from the web console.",
            changed_at: "2026-04-10T17:45:00Z",
            changed_by: users[1]
          }
        ]
      });

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

      const detail = details.get(transitionMatch[1]);
      if (detail) {
        details.set(transitionMatch[1], {
          ...detail,
          status: body.to_status,
          updated_at: "2026-04-10T18:00:00Z",
          history: [
            ...detail.history,
            {
              id: `history-transition-${detail.id}`,
              from_status: detail.status,
              to_status: body.to_status,
              reason: `Operator moved order to ${body.to_status}.`,
              changed_at: "2026-04-10T18:00:00Z",
              changed_by: users[1]
            }
          ]
        });
      }

      return jsonResponse({ ok: true }, { status: 201 });
    }

    const commentMatch = path.match(/^\/orders\/([^/]+)\/comments$/);
    if (commentMatch && method === "POST") {
      const body = JSON.parse(String(init?.body ?? "{}")) as {
        body: string;
      };
      const detail = details.get(commentMatch[1]);

      if (!detail) {
        return jsonResponse({ detail: "Not found." }, { status: 404 });
      }

      details.set(commentMatch[1], {
        ...detail,
        comments: [
          ...detail.comments,
          {
            id: `comment-${detail.comments.length + 1}`,
            body: body.body,
            created_at: "2026-04-10T18:10:00Z",
            author: users[1]
          }
        ]
      });

      return jsonResponse({ ok: true }, { status: 201 });
    }

    return jsonResponse({ message: `Unhandled request: ${method} ${path}` }, { status: 404 });
  });

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function countRequests(fetchMock: ReturnType<typeof installFetchMock>, path: string, method = "GET") {
  return fetchMock.mock.calls.filter(([input, init]) => {
    const requestUrl = typeof input === "string" ? input : input.toString();
    const requestPath = requestUrl.replace("http://localhost:8000", "");
    return requestPath === path && (init?.method ?? "GET") === method;
  }).length;
}

function countRequestsMatching(
  fetchMock: ReturnType<typeof installFetchMock>,
  pattern: RegExp,
  method = "GET"
) {
  return fetchMock.mock.calls.filter(([input, init]) => {
    const requestUrl = typeof input === "string" ? input : input.toString();
    const requestPath = requestUrl.replace("http://localhost:8000", "");
    return pattern.test(requestPath) && (init?.method ?? "GET") === method;
  }).length;
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
  let fetchMock: ReturnType<typeof installFetchMock>;

  beforeEach(() => {
    fetchMock = installFetchMock();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
  });

  async function signIn(
    user = userEvent.setup(),
    credentials = {
      email: "operator@example.com",
      password: "operator123"
    }
  ) {
    render(<App />);

    await user.clear(screen.getByLabelText("Email"));
    await user.type(screen.getByLabelText("Email"), credentials.email);
    await user.clear(screen.getByLabelText("Password"));
    await user.type(screen.getByLabelText("Password"), credentials.password);
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    await screen.findByRole("heading", { name: "Operate the live workflow" });

    return user;
  }

  async function selectOrder(user: ReturnType<typeof userEvent.setup>, title: string) {
    const rowTitle = await screen.findByRole("cell", { name: title });
    const row = rowTitle.closest("tr");

    if (!row) {
      throw new Error(`Unable to find table row for ${title}`);
    }

    await user.click(row);
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

  it("filters the visible queue by code, title, and customer name", async () => {
    const user = await signIn();

    await user.type(screen.getByLabelText("Search queue"), "SF-1002");

    expect(screen.getByRole("cell", { name: "Verify warranty documents" })).toBeInTheDocument();
    expect(screen.queryByRole("cell", { name: "Replace display unit" })).not.toBeInTheDocument();
    expect(screen.getByText("Showing 1 of 3 orders")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Search queue"));
    await user.type(screen.getByLabelText("Search queue"), "shipment");

    expect(screen.getByRole("cell", { name: "Prepare approved shipment" })).toBeInTheDocument();
    expect(screen.queryByRole("cell", { name: "Replace display unit" })).not.toBeInTheDocument();
    expect(screen.getByText("Showing 1 of 3 orders")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Search queue"));
    await user.type(screen.getByLabelText("Search queue"), "alex morgan");

    expect(screen.getByRole("cell", { name: "Replace display unit" })).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Verify warranty documents" })).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Prepare approved shipment" })).toBeInTheDocument();
    expect(screen.getByText("Showing 3 of 3 orders")).toBeInTheDocument();
  });

  it("refreshes the queue on demand and surfaces snapshot metadata", async () => {
    const user = await signIn();

    expect(screen.getByText("Queue snapshot")).toBeInTheDocument();
    expect(screen.getByText(/Last sync /)).toBeInTheDocument();

    const ordersRequestsBeforeRefresh = countRequests(fetchMock, "/orders");

    await user.click(screen.getByRole("button", { name: "Refresh" }));

    await waitFor(() => {
      expect(countRequests(fetchMock, "/orders")).toBeGreaterThan(ordersRequestsBeforeRefresh);
    });

    expect(screen.getByText("Live queue")).toBeInTheDocument();
    expect(screen.getByText("Showing 3 of 3 orders")).toBeInTheDocument();
  });

  it("keeps the selected order in focus across a manual refresh", async () => {
    const user = await signIn();

    await selectOrder(user, "Verify warranty documents");
    await screen.findByRole("heading", { name: "Verify warranty documents" });

    const ordersRequestsBeforeRefresh = countRequests(fetchMock, "/orders");
    const detailRequestsBeforeRefresh = countRequestsMatching(fetchMock, /^\/orders\/order-2$/);

    await user.click(screen.getByRole("button", { name: "Refresh" }));

    await waitFor(() => {
      expect(countRequests(fetchMock, "/orders")).toBeGreaterThan(ordersRequestsBeforeRefresh);
      expect(countRequestsMatching(fetchMock, /^\/orders\/order-2$/)).toBeGreaterThan(detailRequestsBeforeRefresh);
    });

    expect(screen.getByRole("heading", { name: "Verify warranty documents" })).toBeInTheDocument();
    expect(screen.queryByText("Selected order is unavailable.")).not.toBeInTheDocument();
  });

  it("falls back to the cached snapshot when refresh loses the dashboard API", async () => {
    const user = await signIn();

    await selectOrder(user, "Verify warranty documents");
    await screen.findByRole("heading", { name: "Verify warranty documents" });

    fetchMock = installFetchMock(baseOrders(), { failingOrdersGetCount: 1 });

    await user.click(screen.getByRole("button", { name: "Refresh" }));

    await screen.findByText("Showing the last successful queue state.");
    expect(screen.getByText("Cached view")).toBeInTheDocument();
    expect(screen.getAllByText(/Live sync failed\. Showing the last successful snapshot\./)).toHaveLength(2);
    expect(screen.getByRole("heading", { name: "Verify warranty documents" })).toBeInTheDocument();
    expect(screen.getByText("Order detail for Verify warranty documents.")).toBeInTheDocument();
  });

  it("restores the cached snapshot on reload when the dashboard API is unavailable", async () => {
    const user = userEvent.setup();
    const firstRender = render(<App />);

    await user.clear(screen.getByLabelText("Email"));
    await user.type(screen.getByLabelText("Email"), "operator@example.com");
    await user.clear(screen.getByLabelText("Password"));
    await user.type(screen.getByLabelText("Password"), "operator123");
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    await screen.findByRole("heading", { name: "Operate the live workflow" });
    await selectOrder(user, "Verify warranty documents");
    await screen.findByRole("heading", { name: "Verify warranty documents" });

    firstRender.unmount();

    fetchMock = installFetchMock(baseOrders(), { failingOrdersGetCount: 1 });
    render(<App />);

    await screen.findByText("Showing the last successful queue state.");
    expect(screen.getByText("Cached view")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Verify warranty documents" })).toBeInTheDocument();
    expect(screen.getByText("Order detail for Verify warranty documents.")).toBeInTheDocument();
  });

  it("queues create-order mutations offline and replays them on the next refresh", async () => {
    const user = await signIn();

    fetchMock = installFetchMock(baseOrders(), {
      networkFailureRules: [{ method: "POST", pathPattern: /^\/orders$/, times: 1 }]
    });

    await user.click(screen.getByRole("button", { name: "Create order" }));
    await user.type(screen.getByLabelText("Order title"), "Queued offline replacement");
    await user.type(screen.getByLabelText("Description"), "Captured while the API was unavailable.");
    await user.click(screen.getByRole("button", { name: /^Create order$/ }));

    await screen.findByText("1 queued change waiting to sync.");
    expect(screen.getAllByText("Queued offline replacement")).toHaveLength(2);
    expect(screen.getByText(/Connection is unavailable/)).toBeInTheDocument();

    fetchMock = installFetchMock(baseOrders());
    await user.click(screen.getByRole("button", { name: "Refresh" }));

    await screen.findByText("Queued changes synced.");
    expect(screen.queryByText("1 queued change waiting to sync.")).not.toBeInTheDocument();
    expect(screen.getAllByText("Queued offline replacement")).toHaveLength(2);
  });

  it("queues comment mutations offline and replays them on refresh", async () => {
    const user = await signIn();

    await selectOrder(user, "Replace display unit");
    await screen.findByText("Initial operator note.");

    fetchMock = installFetchMock(baseOrders(), {
      networkFailureRules: [{ method: "POST", pathPattern: /^\/orders\/order-1\/comments$/, times: 1 }]
    });

    await user.type(screen.getByLabelText("Add comment"), "Queued comment from offline mode");
    await user.click(screen.getByRole("button", { name: "Post comment" }));

    await screen.findByText("Queued comment from offline mode");
    expect(screen.getByText("1 queued change waiting to sync.")).toBeInTheDocument();

    fetchMock = installFetchMock(baseOrders());
    await user.click(screen.getByRole("button", { name: "Refresh" }));

    await screen.findByText("Queued changes synced.");
    expect(screen.getByText("Queued comment from offline mode")).toBeInTheDocument();
  });

  it("queues status transitions offline and replays them on refresh", async () => {
    const user = await signIn();

    const rowTitle = await screen.findByRole("cell", { name: "Replace display unit" });
    const row = rowTitle.closest("tr");

    if (!row) {
      throw new Error("Unable to find table row for Replace display unit");
    }

    fetchMock = installFetchMock(baseOrders(), {
      networkFailureRules: [{ method: "POST", pathPattern: /^\/orders\/order-1\/status-transitions$/, times: 1 }]
    });

    await user.click(within(row).getByRole("button", { name: "Change status" }));
    await user.click(screen.getByRole("button", { name: "In review" }));

    await waitFor(() => {
      expect(within(row).getByText("In review")).toBeInTheDocument();
    });
    expect(screen.getByText("1 queued change waiting to sync.")).toBeInTheDocument();

    fetchMock = installFetchMock(baseOrders());
    await user.click(screen.getByRole("button", { name: "Refresh" }));

    await screen.findByText("Queued changes synced.");
    expect(within(row).getByText("In review")).toBeInTheDocument();
  });

  it("lets a customer sign in, create orders, and browse the queue in read-only mode", async () => {
    const user = await signIn(userEvent.setup(), {
      email: "customer@example.com",
      password: "customer123"
    });

    expect(screen.getByText("Alex Morgan | customer")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Operator access required" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create order" }));
    await user.type(screen.getByLabelText("Order title"), "Customer raised replacement request");
    await user.type(screen.getByLabelText("Description"), "Customer captured the issue from the portal.");
    await user.click(screen.getByRole("button", { name: /^Create order$/ }));

    await screen.findByRole("cell", { name: "Customer raised replacement request" });

    const rowTitle = await screen.findByRole("cell", { name: "Replace display unit" });
    const row = rowTitle.closest("tr");

    if (!row) {
      throw new Error("Unable to find table row for Replace display unit");
    }

    expect(within(row).getByText("Operator only")).toBeInTheDocument();

    await user.click(row);

    await screen.findByText("Recent status movement");
    expect(screen.getByText("Comments are available in operator mode.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Post comment" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Change status" })).not.toBeInTheDocument();
  });

  it("keeps the queue usable when selected order detail fails and offers recovery actions", async () => {
    fetchMock = installFetchMock(baseOrders(), { failingDetailIds: ["order-1"] });

    const user = await signIn();

    await screen.findByText("Selected order is unavailable.");
    expect(screen.getByText(/API returned 500/)).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Replace display unit" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Clear selection" }));
    await screen.findByText("Select an order to inspect.");

    const brokenRowTitle = await screen.findByRole("cell", { name: "Replace display unit" });
    const brokenRow = brokenRowTitle.closest("tr");

    if (!brokenRow) {
      throw new Error("Unable to find table row for Replace display unit");
    }

    await user.click(brokenRow);
    await screen.findByText("Selected order is unavailable.");

    await user.click(screen.getByRole("button", { name: "Open SF-1002" }));
    await screen.findByRole("heading", { name: "Verify warranty documents" });
    expect(screen.queryByText("Selected order is unavailable.")).not.toBeInTheDocument();
  });

  it("recovers a transient detail failure through the refresh action", async () => {
    fetchMock = installFetchMock(baseOrders(), { transientFailingDetailIds: ["order-1"] });

    const user = await signIn();

    await screen.findByText("Selected order is unavailable.");
    const recoveryCard = screen.getByText("Selected order is unavailable.").closest("article");

    if (!recoveryCard) {
      throw new Error("Unable to find detail recovery card");
    }

    await user.click(within(recoveryCard).getByRole("button", { name: "Refresh" }));

    await screen.findByRole("heading", { name: "Replace display unit" });
    expect(screen.queryByText("Selected order is unavailable.")).not.toBeInTheDocument();
    expect(screen.getByText("Initial operator note.")).toBeInTheDocument();
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

    await screen.findByRole("cell", { name: "Inspect return shipment" });
    expect(within(getStatusCard("New")).getByText("2")).toBeInTheDocument();
  });

  it("opens and closes row status actions, then updates the visible status", async () => {
    const user = await signIn();

    const rowTitle = await screen.findByRole("cell", { name: "Replace display unit" });
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

  it("shows order history and posts a new comment in the inspector", async () => {
    const user = await signIn();

    const rowTitle = await screen.findByRole("cell", { name: "Replace display unit" });
    const row = rowTitle.closest("tr");

    if (!row) {
      throw new Error("Unable to find table row for Replace display unit");
    }

    await user.click(row);

    await screen.findByText("Recent status movement");
    expect(screen.getByText("Initial operator note.")).toBeInTheDocument();
    expect(screen.getByText("Created in New")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Add comment"), "Follow-up note from the test");
    await user.click(screen.getByRole("button", { name: "Post comment" }));

    await screen.findByText("Follow-up note from the test");
  });
});
