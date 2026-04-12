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
- operator sign-in now gates the console via bearer auth
- supports live order creation, valid status transitions, and a detail inspector for comments/history

## Seed credentials

- operator: `operator@example.com` / `operator123`

## Tests

```bash
npm.cmd run test --workspace apps/web
```

The current suite covers the queue summary, filter dropdown behavior, order creation flow, row-level status transitions, and the comments/history inspector with mocked API responses.

## End-to-end tests

Start the local stack first:

```bash
docker compose up -d --build postgres api web
```

Then run:

```bash
npm.cmd run test:e2e --workspace apps/web
```

The Playwright suite exercises the live operator console against the running API, including order creation, filter behavior, real status transitions, and live comment/history visibility in the inspector.

## Cross-client parity smoke

To verify `web -> mobile` and `mobile -> web` sync end to end, run the shared repo-level script:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/cross-client-sync.ps1 -StartEmulator
```

This uses a small Playwright driver against the live web console plus the Android adb flow to confirm both clients see each other's newly created orders, comments, and status transitions.
