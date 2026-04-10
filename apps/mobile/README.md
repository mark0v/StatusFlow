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

## API configuration

- default emulator API base URL: `http://10.0.2.2:8000/`
- override at build time with `-PstatusflowApiBaseUrl=http://your-host:8000/`
- the current mobile screen can fetch orders, filter and sort the queue, support pull-to-refresh and empty states, create a new order, inspect live order details, post operator comments, and trigger allowed status transitions
