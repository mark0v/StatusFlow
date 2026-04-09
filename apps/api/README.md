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

## Current API scope

- order status lifecycle endpoint
- seeded users and orders
- order listing and detail
- create order
- add comment
- transition order status with validation
