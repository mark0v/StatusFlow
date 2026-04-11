import { FormEvent, useEffect, useMemo, useState } from "react";
import statusFlowLogo from "./assets/statusflow-logo.svg";

type OrderStatus =
  | "new"
  | "in_review"
  | "approved"
  | "rejected"
  | "fulfilled"
  | "cancelled";

type OrderCard = {
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
  author: UserSummary;
};

type OrderHistoryEvent = {
  id: string;
  from_status: OrderStatus | null;
  to_status: OrderStatus;
  reason: string;
  changed_at: string;
  changed_by: UserSummary;
};

type OrderDetail = OrderCard & {
  description: string;
  comments: OrderComment[];
  history: OrderHistoryEvent[];
};

type UserSummary = {
  id: string;
  email: string;
  name: string;
  role: "customer" | "operator";
};

type AuthSession = {
  access_token: string;
  token_type: "bearer";
  expires_in_seconds: number;
  user: UserSummary;
};

type OrderStatusLifecycle = {
  statuses: OrderStatus[];
  allowed_transitions: Record<OrderStatus, OrderStatus[]>;
};

type CreateOrderFormState = {
  title: string;
  description: string;
};

type SortField = "updated_at" | "status" | "customer_name";
type SortDirection = "asc" | "desc";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000";
const SESSION_STORAGE_KEY = "statusflow.web.session";

const statusLabels: Record<OrderStatus, string> = {
  new: "New",
  in_review: "In review",
  approved: "Approved",
  rejected: "Rejected",
  fulfilled: "Fulfilled",
  cancelled: "Cancelled"
};

const orderedStatuses: OrderStatus[] = [
  "new",
  "in_review",
  "approved",
  "rejected",
  "fulfilled",
  "cancelled"
];

const statusTone = (status: OrderStatus) => {
  switch (status) {
    case "new":
      return "tone-new";
    case "in_review":
      return "tone-review";
    case "approved":
      return "tone-approved";
    case "rejected":
      return "tone-rejected";
    case "fulfilled":
      return "tone-fulfilled";
    case "cancelled":
      return "tone-cancelled";
    default:
      return "tone-default";
  }
};

const formatTimestamp = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));

const historySummary = (event: OrderHistoryEvent) =>
  event.from_status
    ? `${statusLabels[event.from_status]} -> ${statusLabels[event.to_status]}`
    : `Created in ${statusLabels[event.to_status]}`;

function readStoredSession() {
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

function persistSession(session: AuthSession | null) {
  if (session) {
    window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    return;
  }

  window.localStorage.removeItem(SESSION_STORAGE_KEY);
}

async function readJson<T>(path: string, token?: string, init?: RequestInit) {
  const headers = new Headers(init?.headers ?? {});

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers
  });

  if (!response.ok) {
    const message =
      response.status === 401
        ? "AUTH_REQUIRED"
        : response.status === 403
          ? "FORBIDDEN"
          : `API returned ${response.status}`;
    throw new Error(message);
  }

  return (await response.json()) as T;
}

export default function App() {
  const [session, setSession] = useState<AuthSession | null>(() => readStoredSession());
  const [orders, setOrders] = useState<OrderCard[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [lifecycle, setLifecycle] = useState<OrderStatusLifecycle | null>(null);
  const [isLoading, setIsLoading] = useState(Boolean(session));
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [authError, setAuthError] = useState<string | null>(null);
  const [authForm, setAuthForm] = useState({
    email: "operator@example.com",
    password: "operator123"
  });
  const [formState, setFormState] = useState<CreateOrderFormState>({
    title: "",
    description: ""
  });
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [sortField, setSortField] = useState<SortField>("updated_at");
  const [sortDirection, setSortDirection] = useState<SortDirection>("desc");
  const [statusFilter, setStatusFilter] = useState<OrderStatus[]>([]);
  const [isStatusFilterOpen, setIsStatusFilterOpen] = useState(false);
  const [openActionsOrderId, setOpenActionsOrderId] = useState<string | null>(null);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [selectedOrderDetail, setSelectedOrderDetail] = useState<OrderDetail | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [commentDraft, setCommentDraft] = useState("");

  async function loadDashboardData(accessToken: string, signal?: AbortSignal) {
    const [ordersJson, usersJson, lifecycleJson] = await Promise.all([
      readJson<OrderCard[]>("/orders", accessToken, { signal }),
      readJson<UserSummary[]>("/users", accessToken, { signal }),
      readJson<OrderStatusLifecycle>("/order-status-lifecycle", accessToken, { signal })
    ]);

    return {
      orders: ordersJson,
      users: usersJson,
      lifecycle: lifecycleJson
    };
  }

  function applyDashboardData(data: {
    orders: OrderCard[];
    users: UserSummary[];
    lifecycle: OrderStatusLifecycle;
  }) {
    setOrders(data.orders);
    setUsers(data.users);
    setLifecycle(data.lifecycle);
  }

  async function loadOrderDetail(orderId: string, accessToken: string, signal?: AbortSignal) {
    return readJson<OrderDetail>(`/orders/${orderId}`, accessToken, { signal });
  }

  async function refreshDashboardAndDetail(
    accessToken: string,
    preferredSelectedOrderId?: string | null,
    signal?: AbortSignal
  ) {
    const dashboard = await loadDashboardData(accessToken, signal);
    applyDashboardData(dashboard);

    const nextSelectedOrderId =
      preferredSelectedOrderId && dashboard.orders.some((order) => order.id === preferredSelectedOrderId)
        ? preferredSelectedOrderId
        : dashboard.orders[0]?.id ?? null;

    setSelectedOrderId(nextSelectedOrderId);

    if (!nextSelectedOrderId) {
      setSelectedOrderDetail(null);
      setDetailError(null);
      setIsDetailLoading(false);
      return;
    }

    setIsDetailLoading(true);
    setDetailError(null);
    try {
      const detail = await loadOrderDetail(nextSelectedOrderId, accessToken, signal);
      setSelectedOrderDetail(detail);
    } finally {
      setIsDetailLoading(false);
    }
  }

  function handleAuthExpired(message = "Your session expired. Sign in again.") {
    persistSession(null);
    setSession(null);
    setOrders([]);
    setUsers([]);
    setLifecycle(null);
    setSelectedOrderId(null);
    setSelectedOrderDetail(null);
    setDetailError(null);
    setCommentDraft("");
    setOpenActionsOrderId(null);
    setIsCreateOpen(false);
    setAuthError(message);
  }

  useEffect(() => {
    if (!session) {
      setIsLoading(false);
      return;
    }

    const accessToken = session.access_token;
    const controller = new AbortController();

    async function bootstrap() {
      try {
        setIsLoading(true);
        setError(null);
        await refreshDashboardAndDetail(
          accessToken,
          selectedOrderId,
          controller.signal
        );
      } catch (fetchError) {
        if (controller.signal.aborted) {
          return;
        }

        if (fetchError instanceof Error && fetchError.message === "AUTH_REQUIRED") {
          handleAuthExpired();
          return;
        }

        const message =
          fetchError instanceof Error
            ? fetchError.message
            : "Unknown error while loading dashboard data.";
        setError(message);
      } finally {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      }
    }

    void bootstrap();

    return () => controller.abort();
  }, [session]);

  useEffect(() => {
    if (!session || !selectedOrderId) {
      setSelectedOrderDetail(null);
      setDetailError(null);
      setIsDetailLoading(false);
      return;
    }

    const accessToken = session.access_token;
    const activeOrderId = selectedOrderId;
    const controller = new AbortController();

    async function bootstrapDetail() {
      try {
        setIsDetailLoading(true);
        setDetailError(null);
        const detail = await loadOrderDetail(activeOrderId, accessToken, controller.signal);
        setSelectedOrderDetail(detail);
        setCommentDraft("");
      } catch (fetchError) {
        if (controller.signal.aborted) {
          return;
        }

        if (fetchError instanceof Error && fetchError.message === "AUTH_REQUIRED") {
          handleAuthExpired();
          return;
        }

        setSelectedOrderDetail(null);
        setDetailError(
          fetchError instanceof Error
            ? fetchError.message
            : "Unable to load order detail."
        );
      } finally {
        if (!controller.signal.aborted) {
          setIsDetailLoading(false);
        }
      }
    }

    void bootstrapDetail();

    return () => controller.abort();
  }, [selectedOrderId, session]);

  useEffect(() => {
    function handlePointerDown(event: MouseEvent) {
      const target = event.target;

      if (!(target instanceof Element)) {
        return;
      }

      if (!target.closest(".column-filter-wrap")) {
        setIsStatusFilterOpen(false);
      }

      if (!target.closest(".row-action-menu")) {
        setOpenActionsOrderId(null);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);

    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, []);

  const groupedStatuses = useMemo(() => {
    const counts = orders.reduce<Record<OrderStatus, number>>((accumulator, order) => {
      accumulator[order.status] = (accumulator[order.status] ?? 0) + 1;
      return accumulator;
    }, {} as Record<OrderStatus, number>);

    return orderedStatuses.map((status) => [status, counts[status] ?? 0] as const);
  }, [orders]);

  const customer = useMemo(
    () => users.find((user) => user.role === "customer") ?? null,
    [users]
  );
  const operator = useMemo(
    () => users.find((user) => user.role === "operator") ?? null,
    [users]
  );

  const filteredOrders = useMemo(() => {
    if (statusFilter.length === 0) {
      return orders;
    }

    return orders.filter((order) => statusFilter.includes(order.status));
  }, [orders, statusFilter]);

  const sortedOrders = useMemo(() => {
    const next = [...filteredOrders];

    next.sort((left, right) => {
      if (sortField === "status") {
        const result = statusLabels[left.status].localeCompare(statusLabels[right.status]);
        return sortDirection === "asc" ? result : -result;
      }

      if (sortField === "customer_name") {
        const result = left.customer_name.localeCompare(right.customer_name);
        return sortDirection === "asc" ? result : -result;
      }

      const result =
        new Date(left.updated_at).getTime() - new Date(right.updated_at).getTime();
      return sortDirection === "asc" ? result : -result;
    });

    return next;
  }, [filteredOrders, sortDirection, sortField]);

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDirection((current) => (current === "asc" ? "desc" : "asc"));
      return;
    }

    setSortField(field);
    setSortDirection(field === "updated_at" ? "desc" : "asc");
  }

  function toggleStatusFilter(status: OrderStatus) {
    setStatusFilter((current) =>
      current.includes(status)
        ? current.filter((entry) => entry !== status)
        : [...current, status]
    );
  }

  function sortIndicator(field: SortField) {
    if (sortField !== field) {
      return "^";
    }

    return sortDirection === "asc" ? "^" : "v";
  }

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    try {
      setIsAuthenticating(true);
      setAuthError(null);

      const nextSession = await readJson<AuthSession>("/auth/login", undefined, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(authForm)
      });

      persistSession(nextSession);
      setSession(nextSession);
    } catch (loginError) {
      setAuthError(
        loginError instanceof Error && loginError.message === "AUTH_REQUIRED"
          ? "Invalid email or password."
          : loginError instanceof Error
            ? loginError.message
            : "Sign-in failed."
      );
    } finally {
      setIsAuthenticating(false);
    }
  }

  function handleLogout() {
    persistSession(null);
    setSession(null);
    setOrders([]);
    setUsers([]);
    setLifecycle(null);
    setSelectedOrderId(null);
    setSelectedOrderDetail(null);
    setDetailError(null);
    setCommentDraft("");
    setError(null);
    setActionError(null);
    setIsCreateOpen(false);
    setOpenActionsOrderId(null);
  }

  async function handleCreateOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!customer) {
      setActionError("Customer seed user is missing.");
      return;
    }

    if (!session) {
      setActionError("Sign in again to create orders.");
      return;
    }

    try {
      setIsSubmitting(true);
      setActionError(null);

      await readJson("/orders", session.access_token, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          title: formState.title,
          description: formState.description,
          customer_id: customer.id
        })
      });

      setFormState({ title: "", description: "" });
      setIsCreateOpen(false);
      await refreshDashboardAndDetail(session.access_token);
    } catch (submitError) {
      if (submitError instanceof Error && submitError.message === "AUTH_REQUIRED") {
        handleAuthExpired();
        return;
      }

      setActionError(
        submitError instanceof Error
          ? submitError.message
          : "Order creation failed."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleTransition(orderId: string, toStatus: OrderStatus) {
    if (!operator) {
      setActionError("Operator seed user is missing.");
      return;
    }

    if (!session) {
      setActionError("Sign in again to change status.");
      return;
    }

    try {
      setIsSubmitting(true);
      setActionError(null);

      await readJson(`/orders/${orderId}/status-transitions`, session.access_token, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          changed_by_id: operator.id,
          to_status: toStatus,
          reason: `Operator moved order to ${statusLabels[toStatus]}.`
        })
      });

      setOpenActionsOrderId(null);
      await refreshDashboardAndDetail(session.access_token, orderId);
    } catch (submitError) {
      if (submitError instanceof Error && submitError.message === "AUTH_REQUIRED") {
        handleAuthExpired();
        return;
      }

      setActionError(
        submitError instanceof Error
          ? submitError.message
          : "Status transition failed."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleAddComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!session) {
      setActionError("Sign in again to add comments.");
      return;
    }

    if (!selectedOrderId) {
      setActionError("Select an order before adding a comment.");
      return;
    }

    if (!commentDraft.trim()) {
      setActionError("Comment cannot be empty.");
      return;
    }

    try {
      setIsSubmitting(true);
      setActionError(null);

      await readJson(`/orders/${selectedOrderId}/comments`, session.access_token, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          author_id: session.user.id,
          body: commentDraft.trim()
        })
      });

      setCommentDraft("");
      await refreshDashboardAndDetail(session.access_token, selectedOrderId);
    } catch (submitError) {
      if (submitError instanceof Error && submitError.message === "AUTH_REQUIRED") {
        handleAuthExpired();
        return;
      }

      setActionError(
        submitError instanceof Error
          ? submitError.message
          : "Comment submission failed."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!session) {
    return (
      <main className="shell shell-auth">
        <section className="auth-panel">
          <img
            alt="StatusFlow operator console"
            className="hero-logo auth-logo"
            src={statusFlowLogo}
          />
          <p className="eyebrow">Operator sign in</p>
          <h1>Access the live workflow console</h1>
          <p className="lead">
            Sign in with an operator account to review orders, update statuses,
            and manage the shared queue.
          </p>

          <form className="auth-form" onSubmit={handleLogin}>
            <label className="field">
              <span>Email</span>
              <input
                autoComplete="username"
                value={authForm.email}
                onChange={(event) =>
                  setAuthForm((current) => ({ ...current, email: event.target.value }))
                }
              />
            </label>
            <label className="field">
              <span>Password</span>
              <input
                autoComplete="current-password"
                type="password"
                value={authForm.password}
                onChange={(event) =>
                  setAuthForm((current) => ({ ...current, password: event.target.value }))
                }
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
        </section>
      </main>
    );
  }

  if (session.user.role !== "operator") {
    return (
      <main className="shell shell-auth">
        <section className="auth-panel">
          <img
            alt="StatusFlow operator console"
            className="hero-logo auth-logo"
            src={statusFlowLogo}
          />
          <p className="eyebrow">Restricted</p>
          <h1>Operator access required</h1>
          <p className="lead">
            The web console is currently reserved for operator accounts.
          </p>
          <button className="primary-action" onClick={handleLogout} type="button">
            Sign out
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <section className="hero">
        <img
          alt="StatusFlow operator console"
          className="hero-logo"
          src={statusFlowLogo}
        />
        <p className="lead">
          Queue-first console for fast operator work across the shared web and
          mobile workflow.
        </p>

        <div className="hero-grid">
          <article className="hero-card accent">
            <span>Orders from API</span>
            <strong>{isLoading ? "Loading..." : `${orders.length} tracked orders`}</strong>
          </article>
          <article className="hero-card">
            <span>Signed in</span>
            <strong>{session.user.name} | {session.user.role}</strong>
          </article>
          <article className="hero-card">
            <span>Live endpoint</span>
            <strong>{API_BASE_URL}</strong>
          </article>
        </div>
      </section>

      <section className="panel panel-console">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Queue Console</p>
            <h2>Operate the live workflow</h2>
          </div>
          <div className="panel-actions">
            <a className="api-link" href={`${API_BASE_URL}/docs`}>
              Open API docs
            </a>
            <button className="api-link api-link-button" onClick={handleLogout} type="button">
              Sign out
            </button>
          </div>
        </div>

        <div className="summary-strip">
          {groupedStatuses.map(([status, count]) => (
            <article className="status-card" key={status}>
              <span>{statusLabels[status]}</span>
              <strong>{count}</strong>
            </article>
          ))}
        </div>

        <section className="table-stage">
          <div className="table-toolbar">
            <div>
              <p className="eyebrow">Active queue</p>
              <h3>Review and move orders forward</h3>
            </div>

            <div className="toolbar-actions">
              <button
                className="primary-action"
                onClick={() => setIsCreateOpen((current) => !current)}
                type="button"
              >
                {isCreateOpen ? "Close create form" : "Create order"}
              </button>
            </div>
          </div>

          {isCreateOpen ? (
            <article className="create-reveal">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Create order</p>
                  <h3>Add a new order to the live queue</h3>
                </div>
              </div>

              <form className="create-form create-form-inline" onSubmit={handleCreateOrder}>
                <label className="field">
                  <span>Order title</span>
                  <input
                    required
                    minLength={3}
                    value={formState.title}
                    onChange={(event) =>
                      setFormState((current) => ({ ...current, title: event.target.value }))
                    }
                    placeholder="Inspect delivery damage"
                  />
                </label>

                <label className="field field-wide">
                  <span>Description</span>
                  <textarea
                    value={formState.description}
                    onChange={(event) =>
                      setFormState((current) => ({
                        ...current,
                        description: event.target.value
                      }))
                    }
                    placeholder="Add context that web and mobile operators should both see."
                    rows={3}
                  />
                </label>

                <button
                  className="primary-action"
                  disabled={isSubmitting || !customer}
                  type="submit"
                >
                  {isSubmitting ? "Submitting..." : "Create order"}
                </button>
              </form>
            </article>
          ) : null}

          {actionError ? (
            <div className="feedback-card feedback-error compact">
              <span className="feedback-eyebrow">Action blocked</span>
              <strong>Action failed.</strong>
              <p>{actionError}</p>
            </div>
          ) : null}

          {isLoading ? (
            <div className="feedback-card feedback-sync">
              <span className="feedback-eyebrow">Live sync</span>
              <strong>Loading orders from the API</strong>
              <p>Refreshing the queue snapshot and row actions.</p>
            </div>
          ) : null}

          {error ? (
            <div className="feedback-card feedback-error">
              <span className="feedback-eyebrow">Error</span>
              <strong>Dashboard failed to load.</strong>
              <p>{error}</p>
            </div>
          ) : null}

          {!isLoading && !error ? (
            <div className="table-wrap">
              <table className="orders-table">
                <colgroup>
                  <col className="col-code" />
                  <col className="col-title" />
                  <col className="col-customer" />
                  <col className="col-status" />
                  <col className="col-updated" />
                  <col className="col-actions" />
                </colgroup>
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Title</th>
                    <th>
                      <button
                        className="column-sort"
                        onClick={() => toggleSort("customer_name")}
                        type="button"
                      >
                        Customer <span>{sortIndicator("customer_name")}</span>
                      </button>
                    </th>
                    <th>
                      <div className="column-filter-wrap">
                        <button
                          className="column-sort"
                          onClick={() => toggleSort("status")}
                          type="button"
                        >
                          Status <span>{sortIndicator("status")}</span>
                        </button>
                        <button
                          className={`filter-trigger ${statusFilter.length > 0 ? "active" : ""}`}
                          onClick={() => setIsStatusFilterOpen((current) => !current)}
                          type="button"
                        >
                          Filter
                        </button>
                        {isStatusFilterOpen ? (
                          <div className="filter-menu">
                            {orderedStatuses.map((status) => (
                              <label className="filter-option" key={status}>
                                <input
                                  checked={statusFilter.includes(status)}
                                  onChange={() => toggleStatusFilter(status)}
                                  type="checkbox"
                                />
                                <span>{statusLabels[status]}</span>
                              </label>
                            ))}
                          </div>
                        ) : null}
                      </div>
                    </th>
                    <th>
                      <button
                        className="column-sort"
                        onClick={() => toggleSort("updated_at")}
                        type="button"
                      >
                        Updated <span>{sortIndicator("updated_at")}</span>
                      </button>
                    </th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {sortedOrders.length === 0 ? (
                    <tr>
                      <td className="empty-row" colSpan={6}>
                        <span className="empty-row-eyebrow">No matching orders</span>
                        <strong>Nothing matches the current status filter.</strong>
                        <p>Clear one or more status selections to bring orders back into view.</p>
                      </td>
                    </tr>
                  ) : (
                    sortedOrders.map((order) => {
                      const nextStatuses = lifecycle?.allowed_transitions[order.status] ?? [];

                      return (
                        <tr
                          className={selectedOrderId === order.id ? "is-selected" : ""}
                          key={order.id}
                          onClick={() => setSelectedOrderId(order.id)}
                        >
                          <td className="cell-code">{order.code}</td>
                          <td className="cell-title">{order.title}</td>
                          <td className="cell-customer">{order.customer_name}</td>
                          <td>
                            <span className={`pill ${statusTone(order.status)}`}>
                              {statusLabels[order.status]}
                            </span>
                          </td>
                          <td className="cell-updated">{formatTimestamp(order.updated_at)}</td>
                          <td className="cell-actions">
                            <div className="table-actions">
                              {nextStatuses.length === 0 ? (
                                <span className="transition-terminal">Terminal state</span>
                              ) : (
                                <div className="row-action-menu">
                                  <button
                                    className="transition-button transition-trigger"
                                    onClick={() =>
                                      setOpenActionsOrderId((current) =>
                                        current === order.id ? null : order.id
                                      )
                                    }
                                    type="button"
                                  >
                                    Change status
                                  </button>
                                  {openActionsOrderId === order.id ? (
                                    <div className="row-dropdown">
                                      {nextStatuses.map((status) => (
                                        <button
                                          key={status}
                                          className="dropdown-action"
                                          disabled={isSubmitting}
                                          onClick={() => void handleTransition(order.id, status)}
                                          type="button"
                                        >
                                          {statusLabels[status]}
                                        </button>
                                      ))}
                                    </div>
                                  ) : null}
                                </div>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          ) : null}

          <section className="detail-inspector">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Order inspector</p>
                <h3>Comments and workflow history</h3>
              </div>
            </div>

            {isDetailLoading ? (
              <div className="feedback-card feedback-sync">
                <span className="feedback-eyebrow">Inspector</span>
                <strong>Loading order detail</strong>
                <p>Fetching comments, history, and the latest description.</p>
              </div>
            ) : null}

            {!isDetailLoading && detailError ? (
              <div className="feedback-card feedback-error">
                <span className="feedback-eyebrow">Inspector</span>
                <strong>Unable to load selected order.</strong>
                <p>{detailError}</p>
              </div>
            ) : null}

            {!isDetailLoading && !detailError && selectedOrderDetail ? (
              <div className="inspector-grid">
                <article className="inspector-card inspector-summary">
                  <div className="inspector-topline">
                    <span className="cell-code">{selectedOrderDetail.code}</span>
                    <span className={`pill ${statusTone(selectedOrderDetail.status)}`}>
                      {statusLabels[selectedOrderDetail.status]}
                    </span>
                  </div>
                  <h4>{selectedOrderDetail.title}</h4>
                  <p>{selectedOrderDetail.description || "No description provided yet."}</p>

                  <div className="inspector-meta">
                    <div>
                      <span>Customer</span>
                      <strong>{selectedOrderDetail.customer_name}</strong>
                    </div>
                    <div>
                      <span>Updated</span>
                      <strong>{formatTimestamp(selectedOrderDetail.updated_at)}</strong>
                    </div>
                  </div>
                </article>

                <article className="inspector-card">
                  <div className="inspector-section-heading">
                    <div>
                      <p className="eyebrow">History</p>
                      <h4>Recent status movement</h4>
                    </div>
                    <span className="inspector-count">{selectedOrderDetail.history.length}</span>
                  </div>

                  {selectedOrderDetail.history.length === 0 ? (
                    <div className="mini-empty">No workflow events recorded yet.</div>
                  ) : (
                    <div className="timeline-list">
                      {[...selectedOrderDetail.history].reverse().map((event) => (
                        <article className="timeline-item" key={event.id}>
                          <strong>{historySummary(event)}</strong>
                          <span>
                            {event.changed_by.name} | {formatTimestamp(event.changed_at)}
                          </span>
                          <p>{event.reason}</p>
                        </article>
                      ))}
                    </div>
                  )}
                </article>

                <article className="inspector-card">
                  <div className="inspector-section-heading">
                    <div>
                      <p className="eyebrow">Comments</p>
                      <h4>Operator notes</h4>
                    </div>
                    <span className="inspector-count">{selectedOrderDetail.comments.length}</span>
                  </div>

                  <form className="comment-form" onSubmit={handleAddComment}>
                    <label className="field field-wide">
                      <span>Add comment</span>
                      <textarea
                        value={commentDraft}
                        onChange={(event) => setCommentDraft(event.target.value)}
                        placeholder="Leave a note for the next operator."
                        rows={3}
                      />
                    </label>
                    <button
                      className="primary-action"
                      disabled={isSubmitting || commentDraft.trim().length === 0}
                      type="submit"
                    >
                      {isSubmitting ? "Posting..." : "Post comment"}
                    </button>
                  </form>

                  {selectedOrderDetail.comments.length === 0 ? (
                    <div className="mini-empty">No comments yet for this order.</div>
                  ) : (
                    <div className="timeline-list">
                      {[...selectedOrderDetail.comments].reverse().map((comment) => (
                        <article className="timeline-item" key={comment.id}>
                          <strong>{comment.author.name}</strong>
                          <span>{formatTimestamp(comment.created_at)}</span>
                          <p>{comment.body}</p>
                        </article>
                      ))}
                    </div>
                  )}
                </article>
              </div>
            ) : null}

            {!isDetailLoading && !detailError && !selectedOrderDetail ? (
              <div className="feedback-card feedback-sync">
                <span className="feedback-eyebrow">Inspector</span>
                <strong>Select an order to inspect.</strong>
                <p>Choose any row to review comments, history, and description.</p>
              </div>
            ) : null}
          </section>
        </section>
      </section>
    </main>
  );
}
