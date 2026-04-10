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
