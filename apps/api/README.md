# API

Backend application and source of truth for business rules.

Planned responsibilities:
- authentication
- order CRUD
- status transitions
- comments
- history and audit behavior

## Local run

```bash
python -m uvicorn app.main:app --app-dir apps/api --reload
```

## Tests

```bash
python -m pip install --target .test-deps -r apps/api/requirements-dev.txt
PYTHONPATH=.test-deps;apps/api python -m pytest apps/api/tests -q
```

## Current API scope

- `POST /auth/login` issues bearer tokens for seeded users
- order status lifecycle endpoint
- seeded users and orders
- order listing and detail
- create order
- add comment
- transition order status with validation

## Seed credentials

- operator: `operator@example.com` / `operator123`
- customer: `customer@example.com` / `customer123`
