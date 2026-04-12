# QA Report: localhost web app

Date: 2026-04-13
Target: `http://127.0.0.1:3000`
Mode: focused web QA, operator + customer role flows
Status: DONE

## Summary

- Issues found: 1
- Issues fixed: 1
- Console errors after fix: 0
- Verification:
  - `npm.cmd run test --workspace apps/web`
  - `npm.cmd run build --workspace apps/web`
  - live browser QA against rebuilt `localhost:3000`

## Issue 001

Title: customer web session rendered operator framing and leaked operator-only notes
Severity: high
Category: functional / authorization UX

What was wrong:

- fresh customer login still showed operator headings like `Operate the live workflow`
- customer detail view exposed operator comments even though the UI also said comments were operator-only

User impact:

- customer users saw the wrong product framing
- operator notes were visible in a read-only customer session

Fix:

- made console and queue headings role-aware
- hid comment list contents for non-operator sessions while keeping the lock message
- added regression coverage for customer role copy and comment visibility

Files changed:

- `apps/web/src/App.tsx`
- `apps/web/src/components/OrderTable.tsx`
- `apps/web/src/components/OrderInspector.tsx`
- `apps/web/src/App.regression-1.test.tsx`
- `apps/web/src/App.test.tsx`

## Evidence

- operator flow still passed create/search/comment/refresh QA
- customer flow after fix showed:
  - `Track the live workflow`
  - `Track your orders across the live workflow`
  - no `Operate the live workflow`
  - no visible operator comment bodies
  - no `Post comment` action
  - visible `Comments are available in operator mode.` message after selecting an order

Artifacts:

- `.gstack/qa-reports/screenshots/web-qa-operator-inspector.png`
- `.gstack/qa-reports/screenshots/web-qa-customer-fixed.png`

## Remaining concerns

- this was a focused QA pass, not a full exhaustive crawl of every page/state
- there are unrelated untracked workspace files outside this fix:
  - `.qwen/`
  - `temp_compile.ps1`
