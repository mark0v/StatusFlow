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
- clearer separation of operator vs end-user capabilities
- stronger duplicate-submission and idempotency coverage
- richer error taxonomy and observability hooks
- auth hardening beyond the current seeded local bearer flow

### Web

Status: cross-client operator console is real and close to parity with mobile

Done:
- live table-first operator console
- inline create-order reveal flow
- row-level status actions
- sort/filter/search controls
- static queue counters
- customer mode parity
- comments and history surfaced in the inspector
- cached dashboard/detail fallback
- detail recovery state
- offline mutation queue for create/comment/status
- role-first sign-in and split operator/customer shells
- queue-first hierarchy and mobile-width card layout
- locked/empty-state cleanup and typography polish
- favicon, manifest, browser metadata
- mocked UI tests
- live Playwright end-to-end tests

Missing for v1:
- a fully green bidirectional cross-client parity smoke against Android
- accessibility pass on table controls and dropdowns
- finer-grained operator productivity features only if truly needed after comments/history land

### Mobile

Status: strongest sync-aware UX foundation in the repo right now

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
- Room cache
- offline mode and reconnect reconciliation
- explicit sync metadata such as last successful refresh

Missing for v1:
- stronger semantics assertions and broader device coverage
- auth hardening beyond seeded local accounts

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
- client parity: largely achieved for create/comment/status/history across both clients
- testing depth: good on API and web, emerging on mobile
- offline/sync realism: achieved in both clients, but cross-client smoke still needs one more reliability pass
- auth realism: not achieved yet

That means we are past the "can this product exist?" stage and squarely in the "make the system realistic and trustworthy" stage.

## Current UX modernization track

Completed:

1. `DB-UX-1` role-first sign in
2. `DB-UX-2` split shell by role
3. `DB-UX-3` queue-first hierarchy pass
4. `DB-UX-4` locked / empty / error state cleanup
5. `DB-UX-5` mobile web card layout
6. `DB-UX-7` typography and surface polish

Still worth doing:

- `DB-UX-6` bring the best mobile information architecture labels and grouping patterns over to web where it still helps clarity

## Current parity plan

Completed:

1. `WQ-1` web queue controls parity
2. `WQ-2` web customer mode parity
3. `WQ-3` web detail recovery state
4. `WQ-4` web parity test expansion
5. `WQ-5` extract web data and sync layer
6. `WQ-6` cached dashboard/detail foundation
7. `WQ-7` web offline mutation queue

In progress:

8. `WQ-8` cross-client parity smoke

Current blocker inside `WQ-8`:

- the shared smoke script can already drive the web console and mobile app in one flow, but Android adb navigation is still flaky when opening the exact freshly synced web-created order detail/comments view

Immediate next actions:

1. make the mobile detail-open step deterministic, ideally through a small debug/test hook that opens detail by `orderId`
2. rerun `scripts/cross-client-sync.ps1` until both `web -> mobile` and `mobile -> web` directions are green
3. once the parity smoke is green, tighten artifact naming/assertions and move the script back into a “done” state

## Updated top-level plan

### Phase 1: Finish MVP reality

This is the most important phase now.

1. Add authentication to API, web, and mobile.
2. Finish the last flaky gap in cross-client parity smoke.
3. Extend end-to-end tests to cover cross-client create/update visibility more routinely.
4. Add stronger auth/session realism and hardening.
5. Keep tightening failure-state and observability coverage.

Exit criteria:
- a user can authenticate
- mobile and web can survive temporary network loss with local data
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

1. Close the last `WQ-8` Android detail-navigation flake
2. Authentication foundation
3. Harden cross-client end-to-end synchronization tests
4. Observability and failure diagnostics

That is the shortest path from "nice working demo" to "realistic system for testing synchronization behavior."
