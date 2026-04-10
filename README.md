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

The project is now in a working MVP foundation stage.

What already works:

- `apps/api` serves real seeded order data from PostgreSQL
- `apps/web` runs as a live operator console against the API
- `apps/mobile` runs as a live Android client against the same API
- shared order lifecycle rules are enforced by the backend
- local Docker and emulator workflows are documented and reproducible
- automated tests exist for API, web UI, web end-to-end, mobile smoke, and mobile Compose instrumentation

What is not finished yet:

- authentication and session handling
- mobile local cache with Room and offline sync behavior
- web comments/history operator workflow polish
- stronger end-to-end synchronization coverage across both clients
- production readiness concerns such as deployment, auth hardening, and observability

## Local development

- `docker compose up --build api web postgres` runs the backend stack
- `apps/mobile/` contains the Android/Jetpack Compose starter project and runs separately in Android Studio
- `docs/LOCAL_DEV_LOOP.md` describes the current end-to-end local workflow
