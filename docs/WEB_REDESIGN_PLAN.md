# Web Redesign Implementation Plan

This plan captures the approved direction from the committed mockups:

- `docs/design-mockups/web-home-layout-20260421/operator-grid-tabs.html`
- `docs/design-mockups/web-home-layout-20260421/customer-grid-tabs.html`

The target layout is a standard operations grid:

- Status counters across the top as clickable filters.
- Search and `Create order` in the table toolbar.
- Manual refresh retained as a compact icon inside the sync pill.
- Sort controls moved into table headers.
- Table remains the primary area.
- The first visible order opens by default.
- The right-side order card stays visible and uses tabs for `Overview`, `History`, and `Comments` or customer-facing `Messages`.
- Long history/comment lists scroll inside the card instead of growing the page.

## Planning Review

### Design Review

Verdict: proceed with the standard grid layout.

Why:

- It matches the actual job: scanning orders, selecting one, acting from context.
- It keeps the queue primary and avoids experimental dashboard chrome.
- The selected row plus persistent right card makes the page feel anchored.
- Clickable counters are a better status filter than a duplicate `Status: All` dropdown.
- The right-card tabs reduce cognitive load and prevent the inspector from becoming one long mixed panel.

Design risks:

- The right card can visually compete with the table if it is too wide.
- Counter cards can become too heavy if they grow taller again.
- Tabs need clear empty, locked, and overflow states.
- Customer mode must be read-only in tone and actions, not just operator mode with buttons hidden.

### Engineering Review

Verdict: implement in slices.

Why:

- `apps/web/src/App.tsx` already owns state orchestration.
- `OrderTable`, `StatusSummary`, and `OrderInspector` already exist, so we can evolve components without a full rewrite.
- Filtering and sorting are already implemented, but the UI ownership changes:
  - status filtering moves from dropdown to counters
  - sorting stays in table headers
  - page size remains in pagination footer
- The biggest behavioral change is default selection of the first visible order after filtering, paging, and initial load.

Engineering risks:

- Moving status filtering into counters can break existing status filter tests.
- Default-select-first-visible behavior must not fight manual selection.
- The right-card scroll container must work on desktop and narrow widths.
- Customer `Messages` wording must map carefully to existing API comments behavior.

## Implementation Backlog

### WR-1: Commit Mockup References

Status: done.

Files:

- `docs/design-mockups/web-home-layout-20260421/operator-grid-tabs.html`
- `docs/design-mockups/web-home-layout-20260421/customer-grid-tabs.html`
- `docs/design-mockups/web-home-layout-20260421/TODO.md`

Acceptance:

- Mockups are committed and can be opened directly in a browser.
- Operator and customer variants remain separate reference files.

### WR-2: Layout Shell And Grid Structure

Goal:

Move the live web app toward the approved page structure without changing business behavior.

Files:

- `apps/web/src/App.tsx`
- `apps/web/src/components/Hero.tsx`
- `apps/web/src/components/OrderTable.tsx`
- `apps/web/src/components/OrderInspector.tsx`
- `apps/web/src/styles.css`

Scope:

- Keep the logo in the top-left header.
- Rename main page heading to `Active orders` for operator and `My orders` for customer.
- Change the console body to a two-column grid: table left, order card right.
- Keep right card visible on desktop.
- Preserve current mobile card behavior for narrow screens.

Acceptance:

- Web still loads for operator and customer.
- Table remains the visual primary area.
- Inspector appears as the right-side card on desktop.
- Existing create, select, pagination, and refresh behavior still works.

Tests:

- `npm.cmd run test --workspace apps/web`
- `npm.cmd run build --workspace apps/web`

### WR-3: Status Counters As Filters

Goal:

Replace the `Status: All` dropdown with clickable status counters.

Files:

- `apps/web/src/components/StatusSummary.tsx`
- `apps/web/src/components/OrderTable.tsx`
- `apps/web/src/App.tsx`
- `apps/web/src/styles.css`

Scope:

- Add `Total` counter.
- Render counters for `New`, `In review`, `Approved`, `Rejected`, `Fulfilled`, and `Cancelled`.
- Make counters clickable.
- `Total` clears status filters and is selected by default.
- Selected counter has a visible active state.
- Remove the status filter dropdown from the table header.

Acceptance:

- Clicking `In review` filters to only in-review orders.
- Clicking `Total` clears the filter.
- Search and status counter filter compose correctly.
- Active filter is visually obvious.

Tests:

- Update status filter tests from dropdown behavior to counter behavior.
- Add regression coverage for `Total` clearing filters.

### WR-4: Table Header Sorting And Toolbar Cleanup

Goal:

Make table headers the only sorting control and keep the toolbar minimal.

Files:

- `apps/web/src/components/OrderTable.tsx`
- `apps/web/src/styles.css`

Scope:

- Keep search field.
- Keep `Create order`.
- Remove duplicate `Sort: Updated` UI if present.
- Show sort indicators in sortable table headers.
- Strengthen selected-row styling.
- Keep row click selection behavior.

Acceptance:

- Sort direction is visible in headers.
- Selected row clearly maps to the right card.
- Toolbar has no duplicate status or sort controls.

Tests:

- Existing sort tests still pass.
- Add assertion that selected row receives the selected class after click.

### WR-5: Sync Pill And Manual Refresh

Goal:

Keep refresh as a fallback without making it a primary workflow button.

Files:

- `apps/web/src/components/OrderTable.tsx`
- `apps/web/src/styles.css`

Scope:

- Replace text `Refresh` button with a compact circular icon.
- Place it inside a sync pill, for example `Refresh icon + Sync 11:42`.
- Preserve current `onRefresh` behavior.
- Keep disabled/loading state accessible.

Acceptance:

- Manual refresh still works.
- The button has an accessible label.
- Loading state is visible without layout jump.

Tests:

- Existing refresh tests still pass.
- Add accessible-name assertion for the icon button.

### WR-6: Pagination Footer Polish

Goal:

Match the approved footer: rows-per-page on the left, pages on the right.

Files:

- `apps/web/src/components/OrderTable.tsx`
- `apps/web/src/styles.css`

Scope:

- Move `Rows per page` to the left side.
- Keep showing range text, such as `Showing 1-10 of 66 orders`.
- Keep page buttons and prev/next on the right.
- Preserve existing page-size behavior.

Acceptance:

- Changing page size resets to page 1.
- Pagination remains usable on narrow widths.
- Footer does not visually overpower the table.

Tests:

- Existing pagination tests still pass.
- Add coverage for page-size change.

### WR-7: Default Selection Behavior

Goal:

Open the first visible order by default so the right card is never empty after load.

Files:

- `apps/web/src/App.tsx`
- `apps/web/src/data/webSyncStore.ts`

Scope:

- On initial dashboard load, select the first visible order if no manual selection exists.
- After filtering, if the current selected order is no longer visible, select the first visible result.
- Preserve manual selection when it remains visible.
- Do not select queued drafts in ways that break detail loading.

Acceptance:

- Fresh operator login opens the first visible order.
- Changing status filter updates selection only when needed.
- Manual selection does not get overwritten during normal refresh.

Tests:

- Add tests for initial default selection.
- Add tests for filter changing selection when selected row disappears.
- Add tests that refresh preserves selection when still visible.

### WR-8: Right Card Tabs

Goal:

Convert the inspector into a tabbed detail card.

Files:

- `apps/web/src/components/OrderInspector.tsx`
- `apps/web/src/styles.css`

Scope:

- Add local tab state for `Overview`, `History`, and `Comments`.
- For customer mode, label comments as `Messages`.
- Add counts to `History` and `Comments/Messages`.
- `Overview` shows customer, current status, and description.
- `History` shows status timeline.
- `Comments` shows operator comments and comment form for operators.
- Customer `Messages` uses customer-safe copy and actions.

Acceptance:

- Only the active tab content is visible.
- Tab counts match data.
- Customer does not see internal operator notes if product rules require hiding them.
- Operator can still add comments.

Tests:

- Add tab switching tests.
- Add operator comment submission test through the `Comments` tab.
- Add customer read-only or customer-message visibility tests based on current product rule.

### WR-9: Scroll Containment For Detail Lists

Goal:

Prevent the right card from growing forever when history or comments are long.

Files:

- `apps/web/src/components/OrderInspector.tsx`
- `apps/web/src/styles.css`

Scope:

- Set desktop max-height on the right card.
- Keep card header and tabs visible.
- Scroll active tab content.
- In `Comments`, keep compose controls reachable.

Acceptance:

- Long history scrolls inside the card.
- Long comments scroll inside the card.
- The table stays aligned with the selected card.
- Narrow screens still stack safely.

Tests:

- Add rendering tests with many comments/history events.
- Add browser QA screenshot at desktop height.

### WR-10: Customer Mode Refinement

Goal:

Apply the customer mockup without pretending customer is an operator.

Files:

- `apps/web/src/App.tsx`
- `apps/web/src/components/Hero.tsx`
- `apps/web/src/components/OrderTable.tsx`
- `apps/web/src/components/OrderInspector.tsx`
- `apps/web/src/styles.css`

Scope:

- Use `My orders` heading.
- Keep customer-scoped counters.
- Remove operator-only status actions.
- Use `Messages` wording instead of `Comments` if comments are customer-visible.
- Keep `Contact support` as the main customer card action if supported by product rules.

Acceptance:

- Customer mode is not a disabled operator UI.
- Operator-only controls do not appear.
- Customer can still create orders if current role rules allow it.

Tests:

- Existing customer role tests updated to the new layout.
- Regression test that customer does not see operator-only status controls.

### WR-11: QA And Visual Pass

Goal:

Verify the redesigned web app in real browser flows.

Scope:

- Operator login.
- Customer login.
- Create order.
- Select row.
- Filter by counters.
- Sort by headers.
- Change page size.
- Switch tabs.
- Add operator comment.
- Long history/comments overflow.
- Desktop and mobile widths.

Commands:

```powershell
npm.cmd run test --workspace apps/web
npm.cmd run build --workspace apps/web
npm.cmd run test:e2e --workspace apps/web
```

Acceptance:

- Unit tests pass.
- Build passes.
- Browser QA confirms the layout matches the mockup direction.

## Recommended Order

1. `WR-2` layout shell and grid structure.
2. `WR-3` status counters as filters.
3. `WR-4` table header sorting and toolbar cleanup.
4. `WR-5` sync pill and refresh icon.
5. `WR-6` pagination footer polish.
6. `WR-7` default selection behavior.
7. `WR-8` right card tabs.
8. `WR-9` scroll containment.
9. `WR-10` customer mode refinement.
10. `WR-11` QA and visual pass.

## Non-Goals For This Pass

- Server-driven realtime sync.
- WebSocket or SSE implementation.
- API schema changes.
- Mobile redesign.
- New state-management library.

