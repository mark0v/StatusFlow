# StatusFlow Design Direction

## Product stance

StatusFlow is an operations product, not a consumer social app. The interface
should feel calm, decisive, and trustworthy under pressure. The visual system
should communicate workflow clarity first, then polish.

## Core qualities

- Operational, not playful
- Clear under load
- Fast to scan
- Confident but restrained
- Mobile-first for task completion

## UI principles

1. Show the queue before the chrome.
2. Use status color as a support signal, never as the only signal.
3. Prefer strong grouping and hierarchy over decorative surfaces.
4. Keep primary actions obvious and secondary actions quiet.
5. On mobile, each screen should answer one question at a time.

## Initial visual direction

- Overall feel: dark control-room interface with high legibility
- Primary surfaces: deep slate and steel blues
- Accent color: electric blue for active workflow and links
- Success: mint/teal
- Warning/review: warm amber
- Rejection/error: muted red
- Typography: clean sans with stronger weight contrast for hierarchy

## Layout direction

- Web should feel like an operator console with a clear queue, summary, and action lane.
- Mobile should use a queue-to-detail flow instead of dense split-screen layouts.
- Cards should feel informational, not ornamental.
- Empty states should guide the next action, not just report absence.

## MCP stack for UI phase

### Phase 1: Design source

- Primary target: Figma MCP
- Optional ideation target: Canva MCP
- Optional fallback target: Penpot MCP

Goal:
- create a single source of truth for screens, components, tokens, and layout rules

Current state:
- not connected in this environment yet

### Phase 2: Web implementation QA

- Primary target: Playwright MCP

Goal:
- verify responsive layout, interaction states, screenshots, and regressions in `apps/web`

Current state:
- available in this environment

### Phase 3: Mobile implementation QA

- Primary target: mobile-mcp
- Android fallback target: adb-mcp

Goal:
- inspect the real mobile UI, capture screenshots, validate flows, and iterate on `apps/mobile`

Current state:
- not connected in this environment yet

## Immediate next actions

1. Connect Figma MCP as the main design source.
2. Connect mobile-mcp and adb-mcp for Android UI inspection.
3. Keep Playwright MCP as the active web UI QA tool.
4. After MCP setup, create the first mobile and web UI passes from this design direction.
