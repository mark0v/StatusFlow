import { useEffect, useState } from "react";

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

export default function App() {
  const [orders, setOrders] = useState<OrderCard[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    async function loadOrders() {
      try {
        setIsLoading(true);
        setError(null);

        const response = await fetch(`${API_BASE_URL}/orders`, {
          signal: controller.signal
        });

        if (!response.ok) {
          throw new Error(`API returned ${response.status}`);
        }

        const payload = (await response.json()) as OrderCard[];
        setOrders(payload);
      } catch (fetchError) {
        if (controller.signal.aborted) {
          return;
        }

        const message =
          fetchError instanceof Error
            ? fetchError.message
            : "Unknown error while loading orders.";
        setError(message);
      } finally {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      }
    }

    loadOrders();

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

  return (
    <main className="shell">
      <section className="hero">
        <p className="eyebrow">Operator Workspace</p>
        <h1>StatusFlow control tower</h1>
        <p className="lead">
          Live operator dashboard backed by the shared workflow API. Use it to
          inspect seeded orders, validate lifecycle rules, and confirm what the
          mobile client should eventually sync.
        </p>

        <div className="hero-grid">
          <article className="hero-card accent">
            <span>Orders from API</span>
            <strong>{isLoading ? "Loading..." : `${orders.length} tracked orders`}</strong>
          </article>
          <article className="hero-card">
            <span>Current backend</span>
            <strong>FastAPI + SQLAlchemy + PostgreSQL</strong>
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
            <p className="eyebrow">Starter Queue</p>
            <h2>Tracked orders</h2>
          </div>
          <a className="api-link" href={`${API_BASE_URL}/docs`}>
            Open API docs
          </a>
        </div>

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
            {orders.map((order) => (
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

                <p className="updated">Updated {formatTimestamp(order.updated_at)}</p>
              </article>
            ))}
          </div>
        ) : null}
      </section>
    </main>
  );
}
