# Web App

Operator/admin client for reviewing and updating orders.

Planned responsibilities:
- login
- order dashboard
- filters
- status updates
- comments and history

## Local run

```bash
npm.cmd install
npm.cmd run dev --workspace apps/web
```

The dashboard now reads real order data from the API.

- default API base URL: `http://localhost:8000`
- override with `VITE_API_BASE_URL`
- supports live order creation and valid status transitions from the operator UI

## Tests

```bash
npm.cmd run test --workspace apps/web
```

The current suite covers the queue summary, filter dropdown behavior, order creation flow, and row-level status transitions with mocked API responses.

## End-to-end tests

Start the local stack first:

```bash
docker compose up -d --build postgres api web
```

Then run:

```bash
npm.cmd run test:e2e --workspace apps/web
```

The Playwright suite exercises the live operator console against the running API, including order creation, filter behavior, and a real status transition.
