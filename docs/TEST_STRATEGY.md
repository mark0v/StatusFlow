# Test Strategy

## Goal

Use StatusFlow as a training system for testing across UI, API, database, synchronization, and network traffic.

## Test levels

### Mobile

- functional UI tests
- local persistence checks
- offline and reconnect flows
- validation and error handling

### Web

- operator workflow checks
- filtering and status changes
- role-based behavior

### API

- authentication
- CRUD for orders
- status transition rules
- validation, authorization, and error responses

### Database

- row creation and update correctness
- status history integrity
- timestamp correctness
- orphan and duplicate prevention

### End-to-end

- create in mobile, verify in web
- update in web, verify in mobile
- inspect API traffic during both flows

## Key scenarios

1. Mobile creates a new order and it appears in web.
2. Web moves an order to `approved` and mobile reflects the change after sync.
3. Mobile works with stale local data while offline, then refreshes correctly.
4. API rejects an invalid status transition.
5. Expired token forces re-authentication.
6. Duplicate submission does not create duplicate orders.

## Traffic inspection goals

- inspect request and response payloads
- verify auth headers
- compare UI state to API payloads
- compare API payloads to database rows
- observe timeout and retry behavior

## Debug-build requirements

- no certificate pinning in debug
- network security config compatible with proxy certificate installation
- request logging enabled in debug
- configurable API base URL for local environment

## Initial tooling targets

- Burp Suite or Charles or mitmproxy
- Android emulator
- browser devtools
- PostgreSQL client
- API docs via Swagger/OpenAPI

## Test data strategy

- shared seed users for mobile and operator roles
- deterministic order fixtures
- explicit edge-case records for invalid transitions and stale data
