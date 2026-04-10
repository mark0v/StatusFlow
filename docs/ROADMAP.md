# StatusFlow Roadmap

## Where we are now

StatusFlow is no longer a scaffold. It is a working multi-client MVP foundation with:

- a persistent `api` backed by PostgreSQL
- a live `web` operator console
- a live Android `mobile` client
- shared status lifecycle rules enforced at the backend
- repeatable local dev, smoke, and test workflows

The core queue flow is already real:

1. create an order
2. view it in web and mobile
3. transition status through valid lifecycle states
4. add comments and inspect history

## Current product scorecard

### API

Status: strong MVP foundation

Done:
- order listing and detail
- create order
- comments
- validated status transitions
- status lifecycle endpoint
- seeded users and orders
- PostgreSQL persistence
- automated API tests

Missing for v1:
- authentication and authorization
- clearer separation of operator vs end-user capabilities
- stronger duplicate-submission and idempotency coverage
- richer error taxonomy and observability hooks

### Web

Status: operator MVP is real and usable

Done:
- live table-first operator console
- inline create-order reveal flow
- row-level status actions
- sort/filter controls
- static queue counters
- favicon, manifest, browser metadata
- mocked UI tests
- live Playwright end-to-end tests

Missing for v1:
- comments and history surfaced in the web UI
- clearer empty/error/loading polish for all edge cases
- accessibility pass on table controls and dropdowns
- finer-grained operator productivity features only if truly needed after comments/history land

### Mobile

Status: strongest UX foundation in the repo right now

Done:
- queue-first mobile layout
- queue to detail flow
- create order
- refresh, search, filter, sort
- status transitions
- comments in detail view
- compact-screen and large-text resilience
- accessibility semantics pass
- device smoke script
- Compose instrumentation tests on emulator

Missing for v1:
- Room cache
- offline mode and reconnect reconciliation
- explicit sync metadata such as last successful refresh
- authentication/session flow
- stronger semantics assertions and broader device coverage

## Relative to the final goal

Our stated end goal is not just "three clients compile". It is:

- shared workflow across clients
- realistic synchronization behavior
- rich testing seams
- network/debug visibility
- confidence around state transitions and failure handling

Against that goal:

- shared workflow: largely achieved for MVP
- status lifecycle correctness: achieved for current scope
- client parity: partially achieved
- testing depth: good on API and web, emerging on mobile
- offline/sync realism: not achieved yet
- auth realism: not achieved yet

That means we are past the "can this product exist?" stage and squarely in the "make the system realistic and trustworthy" stage.

## Updated top-level plan

### Phase 1: Finish MVP reality

This is the most important phase now.

1. Add authentication to API, web, and mobile.
2. Add Room cache and sync model to mobile.
3. Expose comments and history properly in web.
4. Add explicit sync state in mobile: last refresh, stale state, retry messaging.
5. Extend end-to-end tests to cover cross-client create/update visibility.

Exit criteria:
- a user can authenticate
- mobile can survive temporary network loss with local data
- operator actions in web are visible in mobile after sync
- test suite covers the main shared flow end to end

### Phase 2: Hardening and observability

1. Add idempotency and duplicate-submission protection.
2. Add structured error states and clearer retry behavior.
3. Improve database and API integrity checks.
4. Add better logging and developer inspection hooks for network/debug work.

Exit criteria:
- failure modes are visible, not silent
- core sync and mutation flows have clear diagnostics
- repeated actions do not create broken or duplicate state

### Phase 3: Product polish after truth is in place

1. Final mobile and web accessibility pass.
2. Broader responsive/device coverage.
3. Workflow speed features only after observing real friction.
4. Optional deployment and environment hardening.

Exit criteria:
- interface feels finished
- quality is consistent across screen sizes and interaction states
- remaining work is mostly expansion, not core-system repair

## NOT in scope right now

- push notifications, because sync and auth are still more important
- real-time sockets, because explicit refresh is enough for current learning goals
- payments, uploads, and broader role hierarchies, because they expand surface area without improving the main learning loop
- pixel-perfect extra web features, unless they unblock comments/history or operator throughput

## Recommended immediate next steps

If we optimize for the actual end goal, not just visible progress, the best order now is:

1. Authentication foundation
2. Mobile Room cache and offline sync
3. Web comments/history surface
4. Cross-client end-to-end synchronization tests

That is the shortest path from "nice working demo" to "realistic system for testing synchronization behavior."
