# Mobile App

Android client for end users.

Planned responsibilities:
- login
- order list and details
- create order
- local Room cache
- sync and offline handling

## Current scaffold

The app now includes a Jetpack Compose Android client with:

- sign-in for seeded operator and customer accounts
- queue and order detail screens against the live API
- Room-backed queue cache and pending offline mutations
- create-order flow
- comments and status changes in operator mode
- pull-to-refresh, empty states, and device smoke coverage

## Local build

```bash
cd apps/mobile
gradlew.bat assembleDebug
```

The current workspace uses a repository-local Android toolchain under `.local/`
for JDK, Gradle, and Android SDK components.

## Device smoke check

From the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/mobile-smoke.ps1 -StartEmulator
```

This will:

- build the debug APK
- boot the repo-local emulator if needed
- install and launch the app
- verify the focused activity
- capture a screenshot and UI hierarchy dump
- assert key login or queue markers are visible in the rendered screen
- fail immediately if the Gradle build, install, launch, dump, or screenshot step returns a non-zero exit code

Artifacts are written to:

- `outputs/mobile-ui/mobile-smoke.png`
- `outputs/mobile-ui/mobile-smoke.xml`

## Compose UI instrumentation

From `apps/mobile`:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The current instrumentation layer covers:

- queue header and overview rendering
- login card rendering
- create-order composer submission
- order-card selection callback
- detail-screen status transition
- fallback recovery cards

## API configuration

- default emulator API base URL: `http://10.0.2.2:8000/`
- override at build time with `-PstatusflowApiBaseUrl=http://your-host:8000/`
- seeded credentials:
  - `operator@example.com / operator123`
  - `customer@example.com / customer123`
- the current mobile screen can authenticate, fetch orders, preserve search/filter/sort state, support pull-to-refresh and empty states, and move through a queue-to-detail flow for commenting and status changes
- dashboard data is cached in local Room storage and used as a fallback when the API is temporarily unavailable
- create/comment/status mutations now queue locally when the device is offline and attempt reconciliation on the next successful sync
- the current pass also adds accessibility semantics for primary controls and a graceful fallback when a selected order detail is temporarily unavailable
- key queue, detail, and composer layouts now collapse more safely on small screens and under larger text scaling
- the app now also includes a green Compose instrumentation suite for core operator interactions on the emulator
