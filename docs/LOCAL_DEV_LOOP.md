# Local Dev Loop

## Goal

Run the whole local stack in a repeatable way and verify the first shared flow:

1. API serves seeded users and orders
2. Web dashboard reads the same live orders
3. Mobile app reads the same live orders from emulator/device
4. API accepts a valid status transition and rejects an invalid one

## Start the backend stack

```bash
docker compose up -d --build postgres api web
```

Key local URLs:

- API: `http://localhost:8000`
- Swagger: `http://localhost:8000/docs`
- Web dashboard: `http://localhost:3000`

## Build the mobile app

```bash
cd apps/mobile
gradlew.bat assembleDebug
```

Default mobile API base URL is `http://10.0.2.2:8000/` for the Android emulator.

Override for a different host:

```bash
gradlew.bat assembleDebug -PstatusflowApiBaseUrl=http://YOUR_HOST:8000/
```

## API smoke check

```bash
powershell -ExecutionPolicy Bypass -File scripts/e2e-smoke.ps1
```

This script:

- verifies `/health`
- fetches seeded users and orders
- creates a new order
- adds a comment
- performs a valid status transition
- verifies an invalid backward transition returns `422`

## Manual end-to-end scenario

1. Open the web dashboard and confirm live orders appear.
2. Run the mobile app in emulator and confirm the same order feed loads.
3. Use the smoke script or Swagger to create a new order.
4. Confirm the new order appears in web and mobile after refresh/relaunch.
5. Transition the order to `in_review`.
6. Confirm the updated status appears in both clients.
