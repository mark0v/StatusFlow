# StatusFlow web layout TODO

- Keep `Refresh` in the current design as a manual sync fallback.
- Future sync direction: replace primary reliance on manual refresh with automatic server-driven updates, ideally WebSocket or Server-Sent Events rather than aggressive polling.
- Webhooks are not the right browser UI primitive here. They are better for server-to-server integrations.
