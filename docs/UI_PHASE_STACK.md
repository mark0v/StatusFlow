# UI Phase Stack

## Current availability

- Web UI QA: Playwright MCP is configured at project level
- Design-source MCP: Figma MCP and Canva MCP are configured at project level
- Canva Dev MCP: configured at project level for Canva platform/dev docs
- Mobile UI MCP: mobile-mcp is configured at project level
- Android adb/emulator CLI: local `adb.exe` exists under `.local/android-sdk/platform-tools`
- Android emulator tooling is available through the repo-local SDK and helper scripts under `scripts/`
- `adb-mcp` is intentionally not enabled because of a published critical command-injection advisory on the public package

## Recommended stack

### Design source

- Figma MCP as primary source of truth
- Canva MCP as optional ideation layer
- Penpot MCP as optional open-source fallback

Auth note:
- Figma MCP and Canva MCP are remote servers and will require per-user authentication in the Codex client after refresh/restart.

### Web UI

- Playwright MCP for UI verification, screenshots, responsive checks, and regression QA

### Mobile UI

- mobile-mcp as primary mobile interaction layer
- local repo-scoped Android platform tools as the current Android fallback
- `scripts/start-mobile-emulator.ps1` for headless automation
- `scripts/start-mobile-emulator-visible.ps1` for visible manual testing

Note:
- `adb-mcp` is excluded for now due to a public GitHub security advisory (`GHSA-54j7-grvr-9xwg`) against the public package.

## Phase plan

### Phase 1

Goal:
- connect design-source MCP and establish screen/component source of truth

Deliverables:
- connected design MCP
- agreed visual direction
- first screen references for web and mobile

### Phase 2

Goal:
- implement and QA web UI with browser automation

Deliverables:
- Playwright-based checks for main web flows
- screenshots for review
- responsive verification

### Phase 3

Goal:
- implement and QA mobile UI with emulator/device-aware tooling

Deliverables:
- connected mobile MCP
- runnable mobile UI inspection flow
- screenshots and flow verification for queue and detail screens

## Execution note

After adding or changing `.codex/config.toml`, the Codex client may need a restart or MCP
refresh before the new servers appear in the active tool list.
