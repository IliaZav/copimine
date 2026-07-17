# CopiMine Release UI and Backend Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:executing-plans` to implement this plan task by task. Every
> behavior change follows red-green-refactor.

**Goal:** Turn the current readable but flat release UI into a polished,
responsive two-theme product and verify that every represented web action has a
working, authorized backend contract.

**Architecture:** Keep the existing FastAPI backend, static HTML/ES-module
frontend, routes, authentication boundaries, generated showcase imagery, and
deployment layout. Replace the final release override layer instead of reviving
the conflicting legacy cascade. Add shared control/motion primitives and keep
page-specific layout in the existing public and cabinet modules.

**Tech stack:** Python 3, FastAPI, vanilla HTML/CSS/ES modules, PowerShell
contract validators, Browser/IAB, PostgreSQL-compatible repository layer.

---

## Working rules

- Work only inside `D:\Desktop\Copimine\opt\copimine`.
- Preserve the pre-existing untracked CSS files and screenshot directory.
- Do not modify TAB assets or configuration.
- Add no decorative control that does not change real state.
- Keep only light and dark themes.
- Write and run a failing focused check before each production behavior change.
- Stage only files belonging to the current task.

### Task 1: Capture the release UI and backend baseline

**Files:**
- Create: `tests/ValidateCopiMineReleaseUiQuality.ps1`
- Modify only if a confirmed gap requires it:
  `admin-web/scripts/regression_contract_test.py`

- [ ] Add a UI validator that rejects global removal of all backgrounds/shadows,
  requires two theme token sets, reduced-motion handling, visible focus, mobile
  admin drawer rules, minimum touch targets, and semantic switch/segment styles.
- [ ] Run the validator and confirm it fails for the current global suppression
  and mobile admin overflow.
- [ ] Run all existing `tests/ValidateCopiMine*.ps1`, backend smoke, regression,
  SQL, security, Python compile, and JavaScript syntax checks; record exact
  failures before editing production code.
- [ ] Inventory every frontend request in `assets/js/**` and match it to a
  FastAPI route, method, role check, validation path, and response contract.

Commands:

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\ValidateCopiMineReleaseUiQuality.ps1
Get-ChildItem .\tests\ValidateCopiMine*.ps1 | ForEach-Object {
  & powershell -ExecutionPolicy Bypass -File $_.FullName
  if ($LASTEXITCODE -ne 0) { throw "FAILED $($_.Name)" }
}
& .\admin-web\.codex-venv\Scripts\python.exe .\admin-web\scripts\backend_smoketest.py
& .\admin-web\.codex-venv\Scripts\python.exe .\admin-web\scripts\regression_contract_test.py
& .\admin-web\.codex-venv\Scripts\python.exe .\admin-web\scripts\sql_selftest.py
& .\admin-web\.codex-venv\Scripts\python.exe .\admin-web\scripts\security_selftest.py
```

### Task 2: Replace the final visual override with a real design system

**Files:**
- Modify: `admin-web/frontend/assets/css/release-ui.css`
- Modify when required: `admin-web/frontend/assets/css/tokens.css`
- Modify: `admin-web/frontend/assets/js/theme/theme-toggle.js`
- Modify: `admin-web/frontend/assets/js/cabinet-polish.js`
- Test: `tests/ValidateCopiMineReleaseUiQuality.ps1`

- [ ] Define exact light/dark semantic tokens, 3/4/6 px radii, bounded shadows,
  controlled gradients, focus rings, and motion timings.
- [ ] Remove the universal `background-image: none !important` and
  `box-shadow: none !important` rules while keeping legacy conflicts contained
  under the release shell.
- [ ] Implement buttons, icon buttons, fields, switches, segmented controls,
  steppers, range inputs, tables, menus, drawers, dialogs, notices, loading,
  empty, and disabled states.
- [ ] Make the theme control an accessible two-state switch with synchronized
  label/icon and saved preference.
- [ ] Add section reveal, feedback, drawer/dialog, theme, and server-pulse motion
  with a complete `prefers-reduced-motion` branch.
- [ ] Run the focused validator until green, then run JavaScript syntax checks.

### Task 3: Recompose public pages and authentication

**Files:**
- Modify: `admin-web/frontend/index.html`
- Modify: `admin-web/frontend/server.html`
- Modify: `admin-web/frontend/shops.html`
- Modify: `admin-web/frontend/mods.html`
- Modify: `admin-web/frontend/signin.html`
- Modify: `admin-web/frontend/register.html`
- Modify as needed: `admin-web/frontend/assets/js/public/**`
- Modify: `admin-web/frontend/assets/css/release-ui.css`
- Test: `tests/ValidateCopiMineReleaseUiQuality.ps1`

- [ ] Add failing structure checks for first-viewport balance, form-first mobile
  order, one theme switch per visible shell, and no duplicate primary action.
- [ ] Rebalance each hero so the primary purpose, action, server state, and real
  image appear without the current empty desktop column.
- [ ] Preserve existing copy and routes, remove redundant explanatory blocks,
  and keep a hint of the next section in common viewports.
- [ ] Add only functional catalog filters or view modes and verify their rendered
  state changes without a backend round trip where appropriate.
- [ ] Verify both themes at 1440x900, 1024x768, and 390x844 in Browser/IAB.

### Task 4: Repair player and admin responsive shells

**Files:**
- Modify: `admin-web/frontend/assets/cabinet.css`
- Modify: `admin-web/frontend/assets/css/release-ui.css`
- Modify: `admin-web/frontend/assets/js/cabinet-polish.js`
- Modify when markup hooks are needed: `admin-web/frontend/cabinet/*.html`
- Modify: `admin-web/frontend/preview-player.html`
- Modify: `admin-web/frontend/preview-admin.html`
- Test: `tests/ValidateCopiMineReleaseUiQuality.ps1`

- [ ] Add failing checks for a mobile drawer trigger, overlay, close behavior,
  body scroll lock, and absence of horizontal page overflow.
- [ ] Keep the desktop sidebar persistent and turn it into a keyboard-accessible
  drawer below the cabinet breakpoint.
- [ ] Add a compact command bar, consistent metric/status hierarchy, working
  segmented view modes, binary preference switches, and bounded numeric controls
  only where those states already exist.
- [ ] Make tables responsive through column priority, scroll containers, or row
  details without converting dense admin data into decorative cards.
- [ ] Verify public-to-cabinet navigation, drawer, theme switch, one player
  workflow, and one admin workflow in Browser/IAB.

### Task 5: Audit and repair frontend-to-backend contracts

**Files:**
- Modify only confirmed owners under `admin-web/backend/*.py`
- Modify only confirmed callers under `admin-web/frontend/assets/js/**`
- Add focused regression coverage under `admin-web/scripts/` or `tests/`

- [ ] For each visible action, trace request construction, authentication/CSRF,
  handler, role gate, validation, transaction, audit log, and safe response.
- [ ] Reproduce each confirmed gap with a failing test before changing code.
- [ ] Prioritize auth/session, AR/donation operations, reports/technical reports,
  shop/deferred grants, recipe revisions, elections/tax exemption, uploads,
  plugin registry, Discord bridge, and system helpers.
- [ ] Review every broad exception suppression branch. Keep deliberate optional
  fallbacks, but give unexpected failures bounded server-side diagnostics and
  stable client errors.
- [ ] Run the focused test after each fix and the full backend suite after the
  owning area is green.

### Task 6: Final browser, release, and GitHub verification

**Files:**
- Add final screenshots outside committed source unless already required by the
  repository release process.
- Modify release manifests only after all source checks pass.

- [ ] Run all validators and backend self-tests fresh.
- [ ] Run Python compile and JavaScript syntax checks fresh.
- [ ] Browser-test all public routes and representative player/admin workflows in
  both themes at desktop/tablet/mobile sizes, including console/network health.
- [ ] Check focus, keyboard navigation, reduced motion, overflow, contrast,
  clipping, image loading, and functional control state.
- [ ] Capture final screenshots and inspect them with `view_image` against the
  design specification and visual inventory.
- [ ] Regenerate the release archive and manifests only when the tree is green.
- [ ] Commit scoped changes, push `codex/release-overhaul`, and report exact
  verification counts, remaining runtime-only risks, and Ubuntu handoff path.

