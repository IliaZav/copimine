# CopiMine Website Redesign Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the CopiMine website and admin-web UI without breaking the existing auth, donation, AR shop, treasury, plugin registry, and public server-status flows.

**Architecture:** Keep the current FastAPI backend contracts and modular public shell, then progressively move high-risk legacy frontend routes out of the giant SPA renderer. Public pages remain lightweight modules, while player/admin screens migrate behind safer renderers and stricter role/CSRF/session glue instead of a full backend rewrite.

**Tech Stack:** FastAPI, vanilla ES modules, existing modular CSS, legacy SPA runtime, Python validators, Node syntax checks.

---

## Preserved Contracts

- Keep existing backend endpoints in `admin-web/backend/main.py` for:
  - auth/session
  - donation balance and payment sessions
  - AR shop catalog
  - president treasury/public budget
  - plugin registry
  - resource pack status/apply
- Keep `frontend/index.html` as the public shell and keep `assets/app.js` as a bootstrap entry.
- Keep public homepage data sources:
  - `/api/public/status`
  - `/api/public/site-config`
  - `/api/public/modpack`
  - `/api/public/president-budget`
  - `/api/public/president-budget/history`
  - `/api/public/president`
- Preserve current donation/AR economics separation.
- Preserve current role split:
  - `player`
  - `junior_admin`
  - `admin`
  - `owner`

## Audit Snapshot

### Frontend

**Files:**
- Shell: `admin-web/frontend/index.html`
- Bootstrap: `admin-web/frontend/assets/app.js`
- Public modules:
  - `admin-web/frontend/assets/js/bootstrap.js`
  - `admin-web/frontend/assets/js/public/site-data.js`
  - `admin-web/frontend/assets/js/public/site-render.js`
  - `admin-web/frontend/assets/js/public/homepage.js`
- Shared browser helpers:
  - `admin-web/frontend/assets/js/shared/dom.js`
  - `admin-web/frontend/assets/js/shared/browser-state.js`
- Legacy SPA runtime:
  - `admin-web/frontend/assets/js/legacy/app-legacy.js`
- CSS already split into:
  - `base.css`
  - `layout.css`
  - `components.css`
  - `public.css`
  - `auth.css`
  - `player.css`
  - `admin.css`
  - `shop.css`
  - `president.css`
  - `animations.css`
  - `legacy.css`
  - `site-redesign.css`

**Current risks:**
- `app-legacy.js` still owns most authenticated UI and is too large.
- Legacy SPA still uses `innerHTML` extensively for complex player/admin rendering.
- Public shell and legacy runtime coexist correctly, but the boundary is not yet strict enough.
- Authenticated commerce/admin screens are still too coupled to the legacy SPA.

### Backend

**Files:**
- Main API surface: `admin-web/backend/main.py`
- Plugin registry support: `admin-web/backend/plugin_registry.py`, `plugin_registry_manifest.json`
- Donation/AR catalog helpers: `admin-web/backend/commerce_catalog.py`

**Current strengths:**
- Cookie auth, CSRF, rate limit helpers, role helpers, treasury helpers, donation flows, plugin registry endpoints already exist.
- Public president budget/profile endpoints already exist.
- Donation and AR endpoints already exist and should be reused instead of replaced.

**Current risks:**
- `main.py` is very wide, so high-risk changes must be additive and surgical.
- Frontend glue must not assume all routes are safe to expose just because the backend exists.

---

### Task 1: Freeze Preserved Backend Contracts

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebRoutesStillWork.ps1`

- [ ] **Step 1: Inventory the existing route groups that must remain backward-compatible**

Record and preserve these route families:

```text
/api/auth/*
/api/player/*
/api/admin/donation/*
/api/admin/shop/*
/api/admin/plugins/*
/api/public/*
/api/resourcepack/*
```

- [ ] **Step 2: Mark high-risk route families for wrapper-only changes**

Do not rename or remove these handlers. If extraction is needed later, wrap the old entrypoint and keep the path stable.

- [ ] **Step 3: Add or update a validator that fails if these route groups disappear**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebRoutesStillWork.ps1
```

Expected: `... passed.`

- [ ] **Step 4: Commit**

```bash
git add admin-web/backend/main.py tests/ValidateCopiMineWebRoutesStillWork.ps1
git commit -m "test: freeze website backend route contracts"
```

### Task 2: Harden the Public Shell Boundary

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\index.html`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\app.js`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\bootstrap.js`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebNoSingleIndexApp.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebNoHugeAppJs.ps1`

- [ ] **Step 1: Keep `index.html` as a shell-only document**

The shell must only contain:

```html
<link rel="stylesheet" href="/assets/style.css" />
<script type="module" src="/assets/app.js"></script>
```

and semantic mount points for public, auth, player, president, and admin zones.

- [ ] **Step 2: Keep `assets/app.js` as bootstrap-only**

Limit it to:

```js
import "./js/bootstrap.js";
```

- [ ] **Step 3: Move all route selection into `bootstrap.js`**

`bootstrap.js` should decide:

```js
if (publicRoute) {
  loadPublicModules();
} else {
  loadLegacyOrMigratedRuntime();
}
```

without embedding screen markup or business rules.

- [ ] **Step 4: Add a validator that fails if `app.js` grows back into a monolith**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebNoHugeAppJs.ps1
```

Expected: `... passed.`

- [ ] **Step 5: Commit**

```bash
git add admin-web/frontend/index.html admin-web/frontend/assets/app.js admin-web/frontend/assets/js/bootstrap.js tests/ValidateCopiMineWebNoSingleIndexApp.ps1 tests/ValidateCopiMineWebNoHugeAppJs.ps1
git commit -m "refactor: harden website shell bootstrap boundary"
```

### Task 3: Replace High-Risk Public Rendering With Safe DOM Builders

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\public\site-render.js`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\shared\dom.js`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoUnsafeInnerHtml.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineEscapesPublicHistory.ps1`

- [ ] **Step 1: Audit public rendering calls**

Keep only safe cases for:

```js
element.textContent = value;
parent.append(child);
```

Avoid new `innerHTML` for public data.

- [ ] **Step 2: Introduce DOM helpers for cards, metrics, and history rows**

Helpers should create nodes like:

```js
export function makeElement(tag, className = "", text = "") {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text) node.textContent = text;
  return node;
}
```

- [ ] **Step 3: Ensure president/public treasury data is always escaped**

All values from:

```text
current_president_name
ownerName
history.label
history.note
history.actor_name
```

must be rendered through text nodes only.

- [ ] **Step 4: Run XSS-focused validators**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoUnsafeInnerHtml.ps1
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineEscapesPublicHistory.ps1
```

Expected: both pass.

- [ ] **Step 5: Commit**

```bash
git add admin-web/frontend/assets/js/public/site-render.js admin-web/frontend/assets/js/shared/dom.js tests/ValidateCopiMineNoUnsafeInnerHtml.ps1 tests/ValidateCopiMineEscapesPublicHistory.ps1
git commit -m "security: harden public website renderers"
```

### Task 4: Migrate Authenticated Donation and Treasury Screens Off Raw Legacy Rendering

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\legacy\app-legacy.js`
- Create: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\player\donation-pages.js`
- Create: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\player\treasury-pages.js`
- Create: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\shared\formatters.js`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationFlowStillWorks.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineHomepagePresidentBudgetCounter.ps1`

- [ ] **Step 1: Carve donation balance, donation shop, donation items, and treasury views into dedicated modules**

New modules should own:

```text
loadPlayerDonationBalance
loadPlayerDonationShop
loadPlayerDonationItems
loadTreasuryViews
```

The legacy runtime may keep orchestration, but not huge inline markup builders for these screens.

- [ ] **Step 2: Reuse current API contracts instead of inventing new ones**

Use:

```text
/api/player/donation/balance
/api/player/donation/history
/api/player/shop/donation-items
/api/player/shop/owned
/api/player/donation/sbp/session
/api/public/president-budget
/api/public/president-budget/history
```

- [ ] **Step 3: Keep the current purchase split**

Render:

```text
Пополнение — на сайте и в игре
Покупка donation item — только на сайте
Claim/reclaim — только в игре
```

and do not move physical item issuance into the website.

- [ ] **Step 4: Add screen-level validators**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineDonationFlowStillWorks.ps1
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineHomepagePresidentBudgetCounter.ps1
```

Expected: both pass.

- [ ] **Step 5: Commit**

```bash
git add admin-web/frontend/assets/js/legacy/app-legacy.js admin-web/frontend/assets/js/player/donation-pages.js admin-web/frontend/assets/js/player/treasury-pages.js admin-web/frontend/assets/js/shared/formatters.js tests/ValidateCopiMineDonationFlowStillWorks.ps1 tests/ValidateCopiMineHomepagePresidentBudgetCounter.ps1
git commit -m "refactor: migrate donation and treasury screens off legacy renderer"
```

### Task 5: Tighten Cookie Auth, CSRF, Refresh, and Role Guards in the Frontend Glue

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\shared\browser-state.js`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\legacy\app-legacy.js`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineJwtAuth.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineRefreshTokenHttpOnly.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoRefreshTokenLocalStorage.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineCsrfForMutations.ps1`

- [ ] **Step 1: Keep refresh tokens cookie-only**

`browser-state.js` may store only UI state like:

```text
copimineLastRole
copimineDonationSessionId
copiminePlayerBankScope
```

It must never persist:

```text
refresh_token
access_token
bearer
authorization
```

- [ ] **Step 2: Centralize authenticated fetch**

All mutations should use a single helper that sends:

```js
credentials: "include",
headers: { "X-CSRF-Token": token }
```

and rejects when CSRF is missing.

- [ ] **Step 3: Keep role navigation honest**

Frontend can hide routes based on:

```text
player
junior_admin
admin
owner
```

but backend remains the final authority. Do not expose dangerous controls to `junior_admin`.

- [ ] **Step 4: Run auth/security validators**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineJwtAuth.ps1
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineRefreshTokenHttpOnly.ps1
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoRefreshTokenLocalStorage.ps1
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineCsrfForMutations.ps1
```

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add admin-web/frontend/assets/js/shared/browser-state.js admin-web/frontend/assets/js/legacy/app-legacy.js admin-web/backend/main.py tests/ValidateCopiMineJwtAuth.ps1 tests/ValidateCopiMineRefreshTokenHttpOnly.ps1 tests/ValidateCopiMineNoRefreshTokenLocalStorage.ps1 tests/ValidateCopiMineCsrfForMutations.ps1
git commit -m "security: tighten website session and csrf glue"
```

### Task 6: Junior Admin and Treasury/Admin Navigation Hardening

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\index.html`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\legacy\app-legacy.js`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineRoleChecksForAdminEndpoints.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebTreasuryAccessChecksEnabledRole.ps1`

- [ ] **Step 1: Keep junior admin routes visible but constrained**

Junior admin may see:

```text
dashboard
players
stats
inventories
investigations
anticheat
logs
audit
```

Junior admin must not receive owner/admin-only treasury mutation controls, admin account controls, or destructive plugin actions.

- [ ] **Step 2: Split treasury read/write controls**

Render treasury pages so:

```text
player without treasury access -> no treasury mutation controls
president/admin/owner -> treasury account visible
junior_admin -> read-only or hidden, depending on backend guard
```

- [ ] **Step 3: Re-check backend access helpers**

Keep `player_is_site_admin`, `player_is_active_president`, and `has_treasury_access` aligned with enabled role and Minecraft access requirements.

- [ ] **Step 4: Run guard validators**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineRoleChecksForAdminEndpoints.ps1
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWebTreasuryAccessChecksEnabledRole.ps1
```

Expected: both pass.

- [ ] **Step 5: Commit**

```bash
git add admin-web/frontend/index.html admin-web/frontend/assets/js/legacy/app-legacy.js admin-web/backend/main.py tests/ValidateCopiMineRoleChecksForAdminEndpoints.ps1 tests/ValidateCopiMineWebTreasuryAccessChecksEnabledRole.ps1
git commit -m "feat: harden treasury and junior admin website roles"
```

### Task 7: Remove AI/Placeholder/Broken Text and Verify Russian UI Quality

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\index.html`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\legacy\app-legacy.js`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\public\site-render.js`
- Modify: `D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\css\legacy.css`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoAiPlaceholderText.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoMojibakeBrokenEncoding.ps1`
- Test: `D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineHumanReadableRussianUi.ps1`

- [ ] **Step 1: Replace any player-facing debug, placeholder, or fake-success text**

Prefer messages like:

```text
Раздел временно недоступен
Источник данных не отвечает
Попробуйте позже
Оплата ожидает подтверждения
Заберите предмет в игре
```

- [ ] **Step 2: Remove mojibake or mixed broken Russian**

All user-facing Russian must be UTF-8 and readable in:

```text
index.html
public modules
legacy runtime
CSS pseudo-content if any
```

- [ ] **Step 3: Run text-quality validators**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoAiPlaceholderText.ps1
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineNoMojibakeBrokenEncoding.ps1
powershell -ExecutionPolicy Bypass -File D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineHumanReadableRussianUi.ps1
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add admin-web/frontend/index.html admin-web/frontend/assets/js/legacy/app-legacy.js admin-web/frontend/assets/js/public/site-render.js admin-web/frontend/assets/css/legacy.css tests/ValidateCopiMineNoAiPlaceholderText.ps1 tests/ValidateCopiMineNoMojibakeBrokenEncoding.ps1 tests/ValidateCopiMineHumanReadableRussianUi.ps1
git commit -m "fix: clean website ui text and encoding"
```

### Task 8: Verification, Security Gate, and Git Publish

**Files:**
- Modify: `D:\Desktop\Copimine\opt\copimine\tests\manual\COPIMINE_WEBSITE_REDESIGN_SECURITY_SMOKE.md`
- Test: all website/auth/commerce validators

- [ ] **Step 1: Run Python and frontend syntax checks**

Run:

```bash
python -m py_compile D:\Desktop\Copimine\opt\copimine\admin-web\backend\main.py
node --check D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\app.js
node --check D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\bootstrap.js
node --check D:\Desktop\Copimine\opt\copimine\admin-web\frontend\assets\js\public\homepage.js
```

Expected: no syntax errors.

- [ ] **Step 2: Run the website validator sweep**

Run the full relevant set:

```powershell
Get-ChildItem D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineWeb*.ps1 | ForEach-Object { powershell -ExecutionPolicy Bypass -File $_.FullName }
Get-ChildItem D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMineFrontend*.ps1 | ForEach-Object { powershell -ExecutionPolicy Bypass -File $_.FullName }
Get-ChildItem D:\Desktop\Copimine\opt\copimine\tests\ValidateCopiMine*Auth*.ps1 | ForEach-Object { powershell -ExecutionPolicy Bypass -File $_.FullName }
```

- [ ] **Step 3: Run the security review gate**

Review the final diff with the codex-security diff-scan workflow before pushing.

- [ ] **Step 4: Update manual smoke**

Write:

```text
D:\Desktop\Copimine\opt\copimine\tests\manual\COPIMINE_WEBSITE_REDESIGN_SECURITY_SMOKE.md
```

with homepage, login, refresh, donation, AR, treasury, plugin registry, and junior-admin coverage.

- [ ] **Step 5: Commit and push**

```bash
git add admin-web docs/superpowers/plans/2026-06-27-copimine-website-redesign-security-implementation.md tests/manual/COPIMINE_WEBSITE_REDESIGN_SECURITY_SMOKE.md
git commit -m "feat: redesign website shell and secure commerce flows"
git push -u origin $(git -C D:\Desktop\Copimine\opt\copimine branch --show-current)
```

---

## Risks To Watch

- **Idempotency risk:** donation session mark-paid and purchase-intent must not duplicate balance or claims.
- **Anti-dupe risk:** website may show a claimable item while in-game state already has an active instance; always trust backend state.
- **Role regression risk:** junior admin must not inherit owner/admin destructive controls through frontend navigation.
- **XSS risk:** the largest frontend risk remains the legacy SPA HTML-string renderer.
- **Migration risk:** a too-aggressive frontend rewrite can break working player/admin routes that the backend already supports.

## Required Security Gate Before Git

1. Review working diff for security regressions.
2. Fix findings and rerun validators.
3. Only then commit/push to GitHub.

## Independent Review Loop Before Git

After implementation reaches a stable state, run an explicit multi-pass review loop before any push:

1. **Pass 1:** frontend/backend regression sweep over website routes, auth, donation, AR shop, treasury, plugin registry
2. **Pass 2:** focused security review over XSS, CSRF, auth/session storage, role guards, price/balance trust boundaries
3. **Pass 3:** multi-user/state isolation review for per-user session state, cross-user data exposure, cabinet routing, treasury access
4. **Pass 4:** UI/runtime honesty review for dead buttons, fake statuses, placeholder text, broken error states, mobile shell regressions
5. **Pass 5:** final validator/build sweep and diff review

For each pass:

- record findings
- fix findings
- rerun the relevant validators/checks
- continue to the next pass only after the previous pass is clean

If a later pass introduces or reveals new defects in an earlier category, loop back and continue until the website is stable. Do not push while unresolved findings remain.

## Manual Smoke To Add

- Homepage loads with public budget and president card.
- Budget counter uses real backend data and graceful fallback.
- Login/register/logout/refresh stay cookie-based.
- Donation balance page creates only allowlisted fixed packs.
- Donation shop purchase says “Заберите предмет в игре” after purchase.
- AR shop still uses backend prices and stays separate from donation balance.
- Junior admin sees limited navigation and cannot access dangerous admin actions.
- Plugin registry pages cannot write arbitrary config keys or files.
