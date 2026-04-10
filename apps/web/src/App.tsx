import { FormEvent, useEffect, useMemo, useState } from "react";

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

type UserSummary = {
  id: string;
  email: string;
  name: string;
  role: "customer" | "operator";
};

type OrderStatusLifecycle = {
  statuses: OrderStatus[];
  allowed_transitions: Record<OrderStatus, OrderStatus[]>;
};

type CreateOrderFormState = {
  title: string;
  description: string;
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000";

const statusLabels: Record<OrderStatus, string> = {
  new: "New",
  in_review: "In review",
  approved: "Approved",
  rejected: "Rejected",
  fulfilled: "Fulfilled",
  cancelled: "Cancelled"
};

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

const sortStatuses = (entries: [OrderStatus, number][]) => {
  const order: OrderStatus[] = [
    "new",
    "in_review",
    "approved",
    "rejected",
    "fulfilled",
    "cancelled"
  ];

  return entries.sort((left, right) => order.indexOf(left[0]) - order.indexOf(right[0]));
};

async function readJson<T>(path: string, init?: RequestInit) {
  const response = await fetch(`${API_BASE_URL}${path}`, init);

  if (!response.ok) {
    throw new Error(`API returned ${response.status}`);
  }

  return (await response.json()) as T;
}

export default function App() {
  const [orders, setOrders] = useState<OrderCard[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [lifecycle, setLifecycle] = useState<OrderStatusLifecycle | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [formState, setFormState] = useState<CreateOrderFormState>({
    title: "",
    description: ""
  });

  async function loadDashboardData(signal?: AbortSignal) {
    const [ordersPayload, usersPayload, lifecyclePayload] = await Promise.all([
      fetch(`${API_BASE_URL}/orders`, { signal }),
      fetch(`${API_BASE_URL}/users`, { signal }),
      fetch(`${API_BASE_URL}/order-status-lifecycle`, { signal })
    ]);

    if (!ordersPayload.ok || !usersPayload.ok || !lifecyclePayload.ok) {
      throw new Error("Failed to load dashboard dependencies from the API.");
    }

    const [ordersJson, usersJson, lifecycleJson] = await Promise.all([
      ordersPayload.json() as Promise<OrderCard[]>,
      usersPayload.json() as Promise<UserSummary[]>,
      lifecyclePayload.json() as Promise<OrderStatusLifecycle>
    ]);

    setOrders(ordersJson);
    setUsers(usersJson);
    setLifecycle(lifecycleJson);
  }

  useEffect(() => {
    const controller = new AbortController();

    async function bootstrap() {
      try {
        setIsLoading(true);
        setError(null);
        await loadDashboardData(controller.signal);
      } catch (fetchError) {
        if (controller.signal.aborted) {
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

    bootstrap();

    return () => controller.abort();
  }, []);

  const groupedStatuses = sortStatuses(
    Object.entries(
      orders.reduce<Record<OrderStatus, number>>((accumulator, order) => {
        accumulator[order.status] = (accumulator[order.status] ?? 0) + 1;
        return accumulator;
      }, {} as Record<OrderStatus, number>)
    ) as [OrderStatus, number][]
  );

  const customer = useMemo(
    () => users.find((user) => user.role === "customer") ?? null,
    [users]
  );
  const operator = useMemo(
    () => users.find((user) => user.role === "operator") ?? null,
    [users]
  );

  async function handleCreateOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!customer) {
      setActionError("Customer seed user is missing.");
      return;
    }

    try {
      setIsSubmitting(true);
      setActionError(null);

      await readJson("/orders", {
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
      await loadDashboardData();
    } catch (submitError) {
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

    try {
      setIsSubmitting(true);
      setActionError(null);

      await readJson(`/orders/${orderId}/status-transitions`, {
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

      await loadDashboardData();
    } catch (submitError) {
      setActionError(
        submitError instanceof Error
          ? submitError.message
          : "Status transition failed."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="shell">
      <section className="hero">
        <p className="eyebrow">Operator Workspace</p>
        <h1>StatusFlow control tower</h1>
        <p className="lead">
          Live operator dashboard backed by the shared workflow API. Create new
          orders, review the current queue, and move orders through the allowed
          status lifecycle from one place.
        </p>

        <div className="hero-grid">
          <article className="hero-card accent">
            <span>Orders from API</span>
            <strong>{isLoading ? "Loading..." : `${orders.length} tracked orders`}</strong>
          </article>
          <article className="hero-card">
            <span>Seed actors</span>
            <strong>
              {users.length === 0 ? "Loading users..." : `${users.length} shared users`}
            </strong>
          </article>
          <article className="hero-card">
            <span>Live endpoint</span>
            <strong>{API_BASE_URL}</strong>
          </article>
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Operator Actions</p>
            <h2>Create order and steer workflow</h2>
          </div>
          <a className="api-link" href={`${API_BASE_URL}/docs`}>
            Open API docs
          </a>
        </div>

        <form className="create-form" onSubmit={handleCreateOrder}>
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
              placeholder="Add context that the operator and mobile app should both see."
              rows={3}
            />
          </label>

          <button className="primary-action" disabled={isSubmitting || !customer} type="submit">
            {isSubmitting ? "Submitting..." : "Create order"}
          </button>
        </form>

        {actionError ? (
          <div className="state-error compact">
            <strong>Action failed.</strong>
            <p>{actionError}</p>
          </div>
        ) : null}

        <div className="status-overview">
          {groupedStatuses.map(([status, count]) => (
            <article className="status-card" key={status}>
              <span>{statusLabels[status]}</span>
              <strong>{count}</strong>
            </article>
          ))}
        </div>

        {isLoading ? <p className="state-message">Loading orders from the API...</p> : null}
        {error ? (
          <div className="state-error">
            <strong>Dashboard failed to load.</strong>
            <p>{error}</p>
          </div>
        ) : null}

        {!isLoading && !error ? (
          <div className="orders">
            {orders.map((order) => {
              const nextStatuses = lifecycle?.allowed_transitions[order.status] ?? [];

              return (
                <article className="order-card" key={order.id}>
                  <div>
                    <p className="order-code">{order.code}</p>
                    <h3>{order.title}</h3>
                  </div>

                  <p className="customer-label">Customer</p>
                  <p className="customer-name">{order.customer_name}</p>

                  <span className={`pill ${statusTone(order.status)}`}>
                    {statusLabels[order.status]}
                  </span>

                  <div className="transition-row">
                    {nextStatuses.length === 0 ? (
                      <span className="transition-terminal">Terminal state</span>
                    ) : (
                      nextStatuses.map((status) => (
                        <button
                          key={status}
                          className="transition-button"
                          disabled={isSubmitting}
                          onClick={() => handleTransition(order.id, status)}
                          type="button"
                        >
                          Move to {statusLabels[status]}
                        </button>
                      ))
                    )}
                  </div>

                  <p className="updated">Updated {formatTimestamp(order.updated_at)}</p>
                </article>
              );
            })}
          </div>
        ) : null}
      </section>
    </main>
  );
}
