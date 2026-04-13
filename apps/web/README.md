# Web App

Browser client for operators and customers working against the same live order workflow.

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
- seeded sign-in gates the app via bearer auth
- operator mode supports live order creation, valid status transitions, and a detail inspector for comments/history
- customer mode supports live order tracking and request creation with role-aware read-only restrictions

## Seed credentials

- operator: `operator@example.com` / `operator123`
- customer: `customer@example.com` / `customer123`

## Tests

```bash
npm.cmd run test --workspace apps/web
```

The current suite covers the queue summary, filter/search behavior, order creation flow, row-level status transitions, customer/operator role boundaries, recovery states, and the comments/history inspector with mocked API responses.

## End-to-end tests

Start the local stack first:

```bash
docker compose up -d --build postgres api web
```

Then run:

```bash
npm.cmd run test:e2e --workspace apps/web
```

The Playwright suite exercises the live web client against the running API, including order creation, filter behavior, real status transitions, and live comment/history visibility in the inspector.

## Cross-client parity smoke

To verify `web -> mobile` and `mobile -> web` sync end to end, run the shared repo-level script:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/cross-client-sync.ps1 -StartEmulator
```

This uses a small Playwright driver against the live web console plus the Android adb flow to confirm both clients see each other's newly created orders, comments, and status transitions.
