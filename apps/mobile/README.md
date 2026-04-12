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

## Run the app manually

From the repo root, first build the debug APK:

```powershell
cd apps/mobile
.\gradlew.bat assembleDebug
cd ../..
```

### Visible emulator mode

Use this when you want to see the Android window and click through the app by hand:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-mobile-emulator-visible.ps1
```

This starts a normal visible emulator window, installs the debug APK, and launches `StatusFlow` inside it.

Useful options:

- `-RestartExisting` stops an already running headless emulator first, then reopens it visibly
- `-SkipInstall` reuses the APK already installed on the device
- `-SkipLaunch` boots the emulator but does not open the app

### Headless emulator mode

Use this for automation, smoke checks, and artifact capture:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-mobile-emulator.ps1
powershell -ExecutionPolicy Bypass -File scripts/mobile-preview.ps1
```

Notes:

- `scripts/start-mobile-emulator.ps1` intentionally uses `-no-window`, so the emulator runs without a visible UI
- `scripts/mobile-preview.ps1` installs the APK, launches the app, and saves a screenshot
- `scripts/mobile-smoke.ps1 -StartEmulator` is also headless and is the right entry point for automated validation
- `scripts/cross-client-sync.ps1 -StartEmulator` also uses the headless path because it is meant for end-to-end automation rather than manual clicking

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

## Cross-client sync smoke

From the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/cross-client-sync.ps1 -StartEmulator
```

This scenario:

- seeds the Android app with the operator session used for sync verification
- drives a live `web -> mobile` scenario by creating, transitioning, and commenting on an order in the web console, then verifying the refreshed Android UI
- drives a live `mobile -> web` scenario by creating, transitioning, and commenting on an order in the Android app, then verifying the refreshed web console
- saves the latest mobile screenshot/UI dump plus web verification screenshots for both directions

Artifacts are written to:

- `outputs/mobile-ui/cross-client-mobile.png`
- `outputs/mobile-ui/cross-client-mobile.xml`
- `outputs/mobile-ui/cross-client-web-create.png`
- `outputs/mobile-ui/cross-client-web-verify.png`

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
