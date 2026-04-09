type OrderCard = {
  id: string;
  code: string;
  customer_name: string;
  status: string;
  updated_at: string;
};

const demoOrders: OrderCard[] = [
  {
    id: "ord_1001",
    code: "SF-1001",
    customer_name: "Alex Morgan",
    status: "new",
    updated_at: "Just now"
  },
  {
    id: "ord_1002",
    code: "SF-1002",
    customer_name: "Taylor Kim",
    status: "in_progress",
    updated_at: "4 min ago"
  },
  {
    id: "ord_1003",
    code: "SF-1003",
    customer_name: "Jordan Lee",
    status: "ready",
    updated_at: "12 min ago"
  }
];

const statusTone = (status: string) => {
  switch (status) {
    case "new":
      return "tone-new";
    case "in_progress":
      return "tone-progress";
    case "ready":
      return "tone-ready";
    default:
      return "tone-default";
  }
};

export default function App() {
  return (
    <main className="shell">
      <section className="hero">
        <p className="eyebrow">Operator Workspace</p>
        <h1>StatusFlow control tower</h1>
        <p className="lead">
          A lightweight dashboard for watching orders move through the system,
          validating status transitions, and exercising the shared API.
        </p>

        <div className="hero-grid">
          <article className="hero-card accent">
            <span>Shared source of truth</span>
            <strong>FastAPI + PostgreSQL</strong>
          </article>
          <article className="hero-card">
            <span>Operator UI</span>
            <strong>React + Vite + TypeScript</strong>
          </article>
          <article className="hero-card">
            <span>Mobile sync target</span>
            <strong>Android app coming next</strong>
          </article>
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Starter Queue</p>
            <h2>Tracked orders</h2>
          </div>
          <a className="api-link" href="http://localhost:8000/docs">
            Open API docs
          </a>
        </div>

        <div className="orders">
          {demoOrders.map((order) => (
            <article className="order-card" key={order.id}>
              <div>
                <p className="order-code">{order.code}</p>
                <h3>{order.customer_name}</h3>
              </div>
              <span className={`pill ${statusTone(order.status)}`}>
                {order.status.replace("_", " ")}
              </span>
              <p className="updated">{order.updated_at}</p>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
