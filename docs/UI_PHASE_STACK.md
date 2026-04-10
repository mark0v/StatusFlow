# UI Phase Stack

## Current availability

- Web UI QA: Playwright MCP is available now
- Design-source MCP: not connected yet
- Mobile UI MCP: not connected yet
- Android adb/emulator CLI: not available on PATH in this environment

## Recommended stack

### Design source

- Figma MCP as primary source of truth
- Canva MCP as optional ideation layer
- Penpot MCP as optional open-source fallback

### Web UI

- Playwright MCP for UI verification, screenshots, responsive checks, and regression QA

### Mobile UI

- mobile-mcp as primary mobile interaction layer
- adb-mcp as Android-focused fallback for screenshots, hierarchy, install, and logs

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

Until Phase 1 and Phase 3 MCPs are connected, we can still prepare design direction
and continue code-side mobile/web UI structure, but visual sign-off for mobile should
wait for a real mobile UI inspection loop.
