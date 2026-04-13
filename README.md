# StatusFlow

StatusFlow is a multi-client learning project for building and testing a real workflow/status management system end to end.

We are building:
- `apps/mobile` for the Android client used by operators and customers
- `apps/web` for the browser client used by operators and customers
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

## Documentation map

Start here when you need the current repo truth, not historical context:

- [docs/LOCAL_DEV_LOOP.md](/C:/Users/nuc/source/repos/StatusFlow/docs/LOCAL_DEV_LOOP.md): end-to-end local runbook for API, web, and mobile
- [docs/ARCHITECTURE.md](/C:/Users/nuc/source/repos/StatusFlow/docs/ARCHITECTURE.md): current system architecture and data flow
- [docs/ROADMAP.md](/C:/Users/nuc/source/repos/StatusFlow/docs/ROADMAP.md): shipped milestones, open gaps, and next phases
- [docs/TEST_STRATEGY.md](/C:/Users/nuc/source/repos/StatusFlow/docs/TEST_STRATEGY.md): automated test layers and expected coverage
- [docs/UI_PHASE_STACK.md](/C:/Users/nuc/source/repos/StatusFlow/docs/UI_PHASE_STACK.md): UI tooling stack across browser, emulator, and design MCPs
- [docs/MOBILE_UI_LOOP.md](/C:/Users/nuc/source/repos/StatusFlow/docs/MOBILE_UI_LOOP.md): focused Android emulator and preview loop
- [apps/api/README.md](/C:/Users/nuc/source/repos/StatusFlow/apps/api/README.md): API endpoints, seeded auth flow, and local testing
- [apps/web/README.md](/C:/Users/nuc/source/repos/StatusFlow/apps/web/README.md): web client behavior, test commands, and parity smoke entry points
- [apps/mobile/README.md](/C:/Users/nuc/source/repos/StatusFlow/apps/mobile/README.md): mobile run modes, smoke checks, and instrumentation
- [scripts/README.md](/C:/Users/nuc/source/repos/StatusFlow/scripts/README.md): helper script index

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
