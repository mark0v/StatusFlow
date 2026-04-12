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

- stronger end-to-end synchronization coverage across both clients
- a fully reliable cross-client parity smoke for `web -> mobile` and `mobile -> web`
- production readiness concerns such as deployment, auth hardening, and observability

## Local development

Start the shared backend and web stack:

```bash
docker compose up -d --build postgres api web
```

Key local URLs:

- API: `http://localhost:8000`
- Swagger: `http://localhost:8000/docs`
- Web: `http://localhost:3000`

Run the mobile app separately from `apps/mobile/`:

```powershell
cd apps/mobile
.\gradlew.bat assembleDebug
```

Useful scripts from the repo root:

- `powershell -ExecutionPolicy Bypass -File scripts/e2e-smoke.ps1`
- `powershell -ExecutionPolicy Bypass -File scripts/mobile-smoke.ps1 -StartEmulator`
- `powershell -ExecutionPolicy Bypass -File scripts/cross-client-sync.ps1 -StartEmulator`

## Test commands

Web:

```bash
npm.cmd run test --workspace apps/web
npm.cmd run build --workspace apps/web
npm.cmd run test:e2e --workspace apps/web
```

API:

```powershell
python -m pip install --target .test-deps -r apps/api/requirements-dev.txt
$env:PYTHONPATH='.test-deps;apps/api'
python -m pytest apps/api/tests -q
```

Mobile:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/mobile-smoke.ps1 -StartEmulator
cd apps/mobile
.\gradlew.bat connectedDebugAndroidTest
```

`docs/LOCAL_DEV_LOOP.md` describes the current end-to-end workflow in more detail.
