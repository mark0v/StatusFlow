# Mobile App

Android client for end users.

Planned responsibilities:
- login
- order list and details
- create order
- local Room cache
- sync and offline handling

## Current scaffold

The app now includes a minimal Jetpack Compose Android shell with a single
screen that can become the base for auth, order list, and sync flows.

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
- assert key queue-first markers are visible in the rendered screen

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
- create-order composer submission
- order-card selection callback
- detail-screen status transition
- fallback recovery cards

## API configuration

- default emulator API base URL: `http://10.0.2.2:8000/`
- override at build time with `-PstatusflowApiBaseUrl=http://your-host:8000/`
- the current mobile screen can fetch orders, preserve search/filter/sort state, support pull-to-refresh and empty states, and move through a queue-to-detail flow for commenting and status changes
- the current pass also adds accessibility semantics for primary controls and a graceful fallback when a selected order detail is temporarily unavailable
- key queue, detail, and composer layouts now collapse more safely on small screens and under larger text scaling
- the app now also includes a green Compose instrumentation suite for core operator interactions on the emulator
