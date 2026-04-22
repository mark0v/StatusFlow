# Mobile Redesign Implementation Plan

Branch: `codex/mobile-redesign`

Design source: `docs/MOBILE_UX_REDESIGN_MOCKUPS.html`

Saved direction: `mobile-redesign v1`

## Goal

Rebuild the mobile app around the same principles as the redesigned web console,
without squeezing the web table into a phone layout.

The mobile app should become a queue-to-detail workflow:

- Queue screen: scan active orders, filter by status counters, search, create.
- Detail screen: one selected order, status actions, tabs for overview/history/comments.
- Create screen: focused composer for a new order, no unrelated controls.

## Current UX Problems

The current `MainActivity.kt` screen is functionally rich but visually overloaded:

- The signed-in header, queue snapshot, queue controls, status filters, order cards,
  create composer, backend card, and detail view can all compete in the same scroll.
- `CreateOrderCard` also owns refresh controls, which makes create and sync feel like
  one mixed concern.
- `ListControlsCard` still exposes sorting UI, while the new direction assumes newest
  first by default and keeps sort hidden for now.
- Detail view stacks next steps, history, and comments vertically. This works, but it
  makes long orders feel endless and does not match the web inspector model.
- Order selection currently jumps directly into detail mode. The new mockup wants a
  selected bottom tray first, then explicit `Open`.
- Much of the UI is still implemented in one large `MainActivity.kt`, so visual changes
  are harder than they need to be.

## Design Review Notes

Using the gstack design-review lens, the saved direction scores well on information
architecture but needs disciplined implementation.

| Dimension | Score | What Must Stay True |
| --- | ---: | --- |
| Scanability | 8/10 | Queue cards stay compact; status counters do not become another full panel. |
| Mobile fit | 8/10 | One screen answers one question. Queue, detail, and create are separate modes. |
| Web consistency | 8/10 | Use web principles: counters, selected order, inspector tabs. Do not copy web table. |
| Action clarity | 7/10 | `Create`, `Open`, `Change status`, `Add note` need stable placement and labels. |
| Density control | 7/10 | History/comments must scroll inside the detail content area, not elongate the whole app. |

## Target Architecture

Introduce explicit screen mode and split UI into smaller composables.

```kotlin
private enum class MobileScreenMode {
    Queue,
    Detail,
    Create
}
```

Keep the existing `MobileHomeViewModel` data flow:

- `refresh`
- `createOrder`
- `selectOrder`
- `transitionOrder`
- `addComment`
- offline queued mutation messaging
- seeded session behavior

Move visual structure into focused composables:

- `MobileAppHeader`
- `StatusCounterCarousel`
- `QueueToolbar`
- `QueueOrderCard`
- `SelectedOrderTray`
- `OrderDetailScreen`
- `OrderDetailTabs`
- `CreateOrderScreen`
- shared `StatusBadge`, `MetaTile`, `PrimaryActionButton`, `QuietActionButton`

This can still live in `MainActivity.kt` for the first implementation, but the plan
should leave seams for moving UI into separate files after behavior is stable.

## Work Queue

### WQ-M1: Screen Mode Skeleton

Create the navigation/state skeleton before touching visual polish.

- Add `MobileScreenMode` in `MobileHomeScreen`.
- Replace `isShowingDetail` and `isCreateExpanded` with one mode variable.
- Queue mode shows only header, status carousel, search/create toolbar, order list,
  selected tray, and small backend/sync state if needed.
- Detail mode shows detail screen only.
- Create mode shows create composer only.
- Preserve debug intent behavior: if an order is pre-selected, mode should become
  `Detail`.

Acceptance:

- Selecting an order marks it selected but does not immediately hide the queue.
- Pressing `Open` enters detail mode.
- Pressing `Create` enters create mode.
- Back/cancel returns to queue.

### WQ-M2: Queue Header And Top Actions

Implement the compact header from the mockup.

- Left: StatusFlow mark/title.
- Right: refresh pill with last-sync text, then initials avatar.
- Remove long explanatory copy from the signed-in home.
- Keep sign-out accessible through avatar interaction or a quiet menu/sheet later.
  For first pass, a small sign-out action can live behind the avatar or in a temporary
  quiet button if menu work is too much.

Acceptance:

- No `MOBILE OPS` hero block on signed-in queue screen.
- Refresh is visible but quiet.
- User identity is visible as initials, not a large signed-in card.

### WQ-M3: Status Counter Carousel

Replace queue snapshot and status filter controls with web-like counter cards.

Statuses:

- `Total`
- `New`
- `In review`
- `Approved`
- `Rejected`
- `Fulfilled`
- `Cancelled`

Behavior:

- `Total` means no status filter.
- Active filter has stronger selected state.
- Counts come from all orders, not only visible filtered orders.
- Carousel is horizontal and thumb-scrollable.

Acceptance:

- Old `Queue snapshot` card is gone from queue mode.
- Old `Status filter` label/control block is gone.
- Counter tap updates the filter.
- Filter selection is visually obvious.

### WQ-M4: Queue Toolbar And Newest-First List

Simplify list controls.

- Keep search input.
- Move `Create` button into the toolbar where sort lived in the mockup.
- Remove visible sort control for now.
- Keep data ordered newest first using the existing API order / `UPDATED_DESC`.
- Remove `Sort` copy from the UI.

Acceptance:

- Toolbar contains search and `Create`.
- No visible sort button or sort label.
- Orders still render newest first.

### WQ-M5: Queue Cards And Selected Tray

Make queue cards closer to the mockup and reduce per-card noise.

- Card top: order code left, status chip right.
- Main title.
- Two compact meta tiles: customer and updated.
- Selected card gets stronger border/left accent.
- Add bottom selected tray with `SF-#### selected`, helper text, and `Open`.

Acceptance:

- Tapping a card selects it and updates tray.
- Tapping `Open` enters detail mode.
- Card content remains readable on emulator width.
- Existing test tags for order cards survive or are updated in tests.

### WQ-M6: Detail Screen With Tabs

Rework detail view into a mobile inspector inspired by web.

Structure:

- Header with back and avatar.
- Order card: code, title, status, customer, updated.
- Primary actions: `Change status`, `Add note`.
- Tabs: `Overview`, `History count`, `Comments count`.

Tab behavior:

- `Overview`: customer, owner if available, created/updated, description.
- `History`: scrollable list of events.
- `Comments`: scrollable list plus add-comment input for operators.

Acceptance:

- Detail does not show Overview, History, and Comments all at once.
- Long history/comments scroll inside content.
- Status transitions remain available for operators only.
- Customer/read-only behavior remains intact.

### WQ-M7: Create Screen

Replace expanded create card with a focused create mode.

Fields:

- Customer display/selection behavior should match current capability.
- Order title.
- Description.

Actions:

- `Create order`.
- `Cancel`.

No `Save draft`, since the product does not support drafts.

After successful create:

- Clear fields.
- Return to queue or open newly created detail. Recommendation: return to queue with
  the new order selected and visible in the selected tray. This keeps the queue-first
  mental model and avoids surprise navigation.

Acceptance:

- `Create` from queue opens create mode.
- `Create order` submits existing `createOrder` flow.
- Successful create selects newest order.
- Cancel does not submit or clear existing queue state.

### WQ-M8: Empty, Error, Offline, And Loading States

Fit existing recovery states into the new modes.

- Loading should be a compact queue skeleton or status card, not a large explainer.
- Sync error should be a dismissible/inline banner near top of queue.
- Offline queued mutation message should appear as a small action banner.
- Backend `Live` card should move out of the primary queue path, probably into a
  debug/footer area or temporary small footer.

Acceptance:

- No recovery state reintroduces the old tall stacked layout.
- Existing mobile smoke can still find login/queue/order text.
- Offline/pending sync remains visible enough for QA.

### WQ-M9: Tests And Automation Updates

Update tests after implementation stabilizes.

- `MobileHomeScreenTest.kt`:
  - queue header/counters render
  - counter filter selects status
  - create mode submits trimmed data
  - order card selection requires `Open`
  - detail tabs switch content
  - comments tab posts comment
- `scripts/mobile-smoke.ps1`:
  - update text expectations if labels change
  - verify queue screen and detail open flow
- `scripts/cross-client-sync.ps1`:
  - update UI text waits if detail/comment labels change

Acceptance:

- Android connected tests pass.
- Mobile smoke passes.
- Cross-client web-to-mobile smoke passes.

## Suggested Implementation Order

1. WQ-M1 and WQ-M2 in one commit: mode skeleton plus compact header.
2. WQ-M3 and WQ-M4 in one commit: counters and simplified toolbar.
3. WQ-M5 in one commit: queue cards and selected tray.
4. WQ-M6 in one or two commits: detail screen shell, then tabs/content.
5. WQ-M7 in one commit: create mode.
6. WQ-M8 in one commit: loading/error/offline polish.
7. WQ-M9 in one or more commits: tests and smoke scripts.

## Risks

- The current `MainActivity.kt` is large. We should avoid a giant all-at-once rewrite
  that mixes behavior and visual changes.
- Android Compose tests depend on current labels and test tags. Preserve tags where
  possible, but update tests intentionally where the interaction model changes.
- Cross-client scripts use UI text like `Comments`, `Refresh`, and selected order
  markers. Detail tabs and create labels must remain automation-friendly.
- Avatar/sign-out behavior needs a simple first pass. Do not block the redesign on a
  perfect account menu.

## Non-Goals For This Pass

- No customer-mode redesign beyond preserving existing read-only behavior.
- No draft orders.
- No new sort UI.
- No push notifications or realtime sync changes.
- No full file/module refactor unless it directly reduces implementation risk.

## Definition Of Done

- Mobile app visually matches the saved `mobile-redesign v1` direction.
- Queue, detail, and create are separate user modes.
- Status counters replace the old queue snapshot/filter block.
- Detail uses tabs instead of one long stack.
- Create is focused and has no draft action.
- Existing API/session/offline behavior still works.
- Mobile smoke and cross-client smoke are updated and passing.
