# StatusFlow

StatusFlow is a multi-client learning project for building and testing a real workflow/status management system end to end.

We are building:
- `apps/mobile` for the end-user Android client
- `apps/web` for operators and admins
- `apps/api` for the backend API
- PostgreSQL for the server-side data store
- shared packages for contracts, test data, and typed models

The main goal is to practice realistic system design and QA:
- synchronization between clients
- status transition rules
- offline and retry behavior
- API contract correctness
- database consistency
- traffic inspection with tools like Burp, Charles, and mitmproxy

## Repository layout

- `apps/mobile/` mobile application workspace
- `apps/web/` web application workspace
- `apps/api/` backend API workspace
- `packages/shared-types/` shared enums, DTOs, and schemas
- `packages/api-contract/` API contract notes and generated artifacts
- `packages/test-data/` fixtures and seed data
- `docs/` product, architecture, design, and testing documents
- `infra/` infrastructure-related assets
- `scripts/` helper scripts
- `docker-compose.yml` local development orchestration

## Current stage

The repository is currently an initial scaffold. The next step is to turn each app workspace into a runnable service and wire local development through Docker Compose.

## Local development

- `docker compose up --build api web postgres` runs the backend stack
- `apps/mobile/` contains the Android/Jetpack Compose starter project and runs separately in Android Studio
- `docs/LOCAL_DEV_LOOP.md` describes the current end-to-end local workflow
