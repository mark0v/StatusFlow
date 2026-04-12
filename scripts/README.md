# Scripts

Helper scripts for local setup, seeding, validation, and test workflows.

Current workflow script:
- `e2e-smoke.ps1` verifies the first shared order flow against the running API
- `start-mobile-emulator.ps1` boots the local Android emulator for mobile UI work
- `mobile-preview.ps1` installs the debug apk, launches the app, and captures a screenshot
- `mobile-smoke.ps1` validates the Android app launches and renders expected login/queue UI markers
- `cross-client-sync.ps1` runs the end-to-end parity smoke across the live web console and Android client in both directions
