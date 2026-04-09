# Order API Contract

## Canonical status lifecycle

The source of truth for order status values is:

- `new`
- `in_review`
- `approved`
- `rejected`
- `fulfilled`
- `cancelled`

Allowed transitions:

- `new -> in_review`, `cancelled`
- `in_review -> approved`, `rejected`, `cancelled`
- `approved -> fulfilled`, `cancelled`
- `rejected -> in_review`, `cancelled`
- `fulfilled ->` terminal
- `cancelled ->` terminal

## Primary endpoints

- `GET /health`
- `GET /users`
- `GET /order-status-lifecycle`
- `GET /orders`
- `GET /orders/{order_id}`
- `POST /orders`
- `POST /orders/{order_id}/comments`
- `POST /orders/{order_id}/status-transitions`

## Data ownership

- API is the only place that may validate status transitions
- `OrderStatusHistory` is append-only
- comments are attached to orders and authored by a user
- clients may cache order data, but they do not own workflow rules
