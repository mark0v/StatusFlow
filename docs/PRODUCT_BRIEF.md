# Product Brief

## Working title

StatusFlow

## One-line summary

StatusFlow is a multi-client order and status management system built for learning how to test mobile, web, API, database, and synchronization behavior end to end.

## Product goal

Create a realistic but compact system where a mobile client and a web client work with the same backend data, while status changes, comments, and updates propagate across the system in a way that is easy to test and inspect.

## Primary learning outcomes

- test Android app behavior with local caching and offline mode
- test web app operator workflows
- test REST API behavior and contracts
- test PostgreSQL data integrity
- inspect and analyze network traffic
- validate sync behavior between clients
- practice negative, exploratory, and integration testing

## Core users

### End user

Uses the mobile app to create and track orders.

### Operator

Uses the web app to review orders, update statuses, and add comments.

## Core workflow

1. A user logs into the mobile app.
2. The user creates an order.
3. The order is saved remotely and cached locally.
4. An operator sees the order in the web app.
5. The operator changes the order status.
6. The mobile app refreshes and shows the new status.
7. The user can inspect what changed in UI, API responses, and database state.

## MVP scope

- authentication
- mobile order list
- mobile order details
- create order in mobile app
- operator web dashboard
- operator status updates
- order comments
- order status history
- local Room cache in mobile app
- explicit refresh and sync timestamp

## Status model

- `new`
- `in_review`
- `approved`
- `rejected`
- `fulfilled`
- `cancelled`

## Out of scope for v1

- push notifications
- payments
- file uploads
- role hierarchies beyond user and operator
- real-time sockets

## Product principles

- keep the domain simple, keep the state transitions rich
- optimize for testability over product breadth
- make network behavior visible in debug builds
- prefer realistic workflows over toy screens
