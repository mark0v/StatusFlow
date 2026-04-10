# Mobile UI Loop

This project now has a working local Android emulator loop for `apps/mobile`.

## Current local setup

- repo-local Android SDK: `.local/android-sdk`
- repo-local emulator AVD: `StatusFlow_API_34`
- repo-local adb path: `.local/android-sdk/platform-tools/adb.exe`
- built debug APK path: `apps/mobile/app/build/outputs/apk/debug/app-debug.apk`

## Start the emulator

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-mobile-emulator.ps1
```

## Install the app and capture a screenshot

```powershell
powershell -ExecutionPolicy Bypass -File scripts/mobile-preview.ps1
```

By default this writes the screenshot to:

```text
outputs/mobile-ui/mobile-home.png
```

## Why this matters

This gives us a reproducible preview loop for the mobile UI phase:

1. boot emulator
2. install latest debug apk
3. launch `apps/mobile`
4. capture screenshots for review
5. iterate on UI safely before design sign-off

## MCP note

`mobile-mcp` is configured in `.codex/config.toml` and can use the same local Android SDK.
If new MCP servers do not appear immediately in Codex, refresh or restart the client first.
