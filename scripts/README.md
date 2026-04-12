# Scripts

Helper scripts for local setup, seeding, validation, and test workflows.

Current workflow script:
- `e2e-smoke.ps1` verifies the first shared order flow against the running API
- `start-mobile-emulator.ps1` boots the local Android emulator in headless `-no-window` mode for automation
- `start-mobile-emulator-visible.ps1` boots the local Android emulator with a visible window for manual mobile testing
- `mobile-preview.ps1` installs the debug apk, launches the app, and captures a screenshot
- `mobile-smoke.ps1` validates the Android app launches and renders expected login/queue UI markers in headless mode
- `cross-client-sync.ps1` runs the end-to-end parity smoke across the live web console and Android client in both directions

## Mobile launch modes

- Visible/manual mode: `powershell -ExecutionPolicy Bypass -File scripts/start-mobile-emulator-visible.ps1`
- Headless/automation mode: use `start-mobile-emulator.ps1`, `mobile-preview.ps1`, `mobile-smoke.ps1`, or `cross-client-sync.ps1`
