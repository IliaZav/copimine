# CopiMine Website Vitrine + Strict Cabinets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the CopiMine website into a branded public showcase with separate route-first pages and a stricter cabinet shell, while removing generic AI-looking copy and stabilizing responsive behavior.

**Architecture:** Public pages use one shared visual language and one signature brand system, but each route has its own job and content hierarchy. Cabinet pages reuse the same brand assets but switch to denser, more operational layouts through shared shell CSS and role-specific overrides.

**Tech Stack:** Static HTML, shared CSS imports, vanilla ES modules for public renderers, static JSON fallback data for modpack page, local browser screenshot verification.

---

## File Structure Map

- `admin-web/frontend/index.html`
  - Public homepage route map only
- `admin-web/frontend/server.html`
  - Public server status page
- `admin-web/frontend/shops.html`
  - Public route page for AR vs donation shops
- `admin-web/frontend/mods.html`
  - Public modpack product page
- `admin-web/frontend/signin.html`
  - Centered login surface only
- `admin-web/frontend/register.html`
  - Centered registration surface only
- `admin-web/frontend/cabinet/*.html`
  - Shared authenticated shell documents that must use logo-driven sidebar branding
- `admin-web/frontend/assets/css/site-redesign.css`
  - Public visual system, hero, cards, route shells
- `admin-web/frontend/assets/css/auth.css`
  - Auth-only screen rules
- `admin-web/frontend/assets/css/player.css`
  - Player cabinet styling
- `admin-web/frontend/assets/css/admin.css`
  - Admin and junior-admin cabinet styling
- `admin-web/frontend/assets/css/responsive.css`
  - Mobile and tablet layout rules
- `admin-web/frontend/assets/css/animations.css`
  - Meaningful motion only
- `admin-web/frontend/assets/js/public/site-render.js`
  - Public page rendering and copy-safe UI assembly
- `admin-web/frontend/assets/js/public/site-data.js`
  - Public route data loading and static fallback bridging
- `admin-web/frontend/assets/public-data/modpack_snapshot.json`
  - Static modpack fallback for `mods.html`
- `scripts/thirdparty/build_modpack.ps1`
  - Rebuild bundled client archive and regenerate snapshot
- `scripts/thirdparty/build_modpack.sh`
  - Linux equivalent snapshot generation
- `tests/ValidateCopiMineWebModpackDownloadFoundation.ps1`
  - Modpack foundation validation
- `tests/manual/COPIMINE_WEBSITE_REDESIGN_SECURITY_SMOKE.md`
  - Manual smoke steps for site rendering

## Task 1: Lock Shared Visual System To The Chosen Direction

**Files:**
- Modify: `admin-web/frontend/assets/css/site-redesign.css`
- Modify: `admin-web/frontend/assets/css/animations.css`
- Modify: `admin-web/frontend/assets/css/responsive.css`
- Test: browser screenshots on `index.html`, `server.html`, `shops.html`, `mods.html`

- [ ] **Step 1: Identify and remove generic template styling from public shells**

Check for:

- overused dashboard-grid behavior on public pages
- repetitive safe card styling with no page-specific hierarchy
- decorative gradients or glow that do not support the page job

Target selectors:

```css
.public-site
.public-nav
.public-hero
.public-section
.public-stage-grid
.gateway-grid
.commerce-grid
.modpack-shell
```

- [ ] **Step 2: Rewrite public shell spacing and hierarchy around route-first pages**

Keep or implement:

```css
.public-nav,
.public-hero,
.public-section {
  width: min(1220px, calc(100vw - 32px));
  margin-inline: auto;
}

.public-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 22px;
  padding: 28px;
  border-radius: 36px;
}
```

Expected result:

- public pages feel editorial and deliberate
- sections do not read like cloned dashboard cards

- [ ] **Step 3: Rebalance motion so one orchestrated layer stays and random motion disappears**

Keep only:

- page-load rise
- gentle hover lift
- restrained accent pulse

Ensure reduced-motion fallback still exists in:

```css
@media (prefers-reduced-motion: reduce) { ... }
```

- [ ] **Step 4: Tighten mobile breakpoints for route pages**

Verify and, if needed, adjust:

```css
@media (max-width: 1180px) { ... }
@media (max-width: 900px) { ... }
@media (max-width: 720px) { ... }
```

Required mobile outcomes:

- no horizontal overflow
- hero stacks vertically
- nav collapses cleanly
- cards stop clipping or overlapping

- [ ] **Step 5: Run visual verification**

Check with browser screenshots:

- `http://127.0.0.1:8088/index.html`
- `http://127.0.0.1:8088/server.html`
- `http://127.0.0.1:8088/shops.html`
- `http://127.0.0.1:8088/mods.html`

Expected:

- no overlapping panels
- no clipped copy
- public pages share one identity but do not feel identical

## Task 2: Rewrite Public Page Content Hierarchy And Human Copy

**Files:**
- Modify: `admin-web/frontend/index.html`
- Modify: `admin-web/frontend/server.html`
- Modify: `admin-web/frontend/shops.html`
- Modify: `admin-web/frontend/mods.html`
- Modify: `admin-web/frontend/assets/js/public/site-render.js`
- Test: local page render plus grep for banned phrases

- [ ] **Step 1: Replace homepage with route-map logic only**

Homepage must contain:

- server entry
- shop entry
- modpack entry
- sign-in entry

Homepage must not contain:

- giant all-in-one summary
- full shop content
- admin-style detail dumps

- [ ] **Step 2: Rewrite page copy to direct operational language**

Forbidden phrases to remove from public pages and renderer copy:

- `всё собрано в одном месте`
- `никаких лишних блоков`
- `только реальные данные сервера`
- generic system self-praise

Preferred copy style:

- name the page
- say what is on the page
- say what the next action is

- [ ] **Step 3: Make `mods.html` a real archive product page**

Required blocks:

- archive headline
- download CTA
- Fabric / Minecraft version
- bundled file list
- SHA1
- notes about optional graphics mods

Renderer must use:

```js
loadPublicModsPageData()
renderModpack(...)
```

with a static fallback snapshot if API data is missing.

- [ ] **Step 4: Keep `shops.html` as a routing and explanation page, not a purchase wall**

Ensure:

- AR and donation are clearly split
- preview cards exist but do not pretend to be the full cabinet shop
- “open in cabinet” is the next action

- [ ] **Step 5: Verify with grep that banned AI-sounding phrases are removed from public entry pages**

Run:

```bash
rg -n "всё собрано в одном месте|никаких лишних блоков|только реальные данные сервера|каноничес" admin-web/frontend/index.html admin-web/frontend/server.html admin-web/frontend/shops.html admin-web/frontend/mods.html admin-web/frontend/assets/js/public/site-render.js
```

Expected:

- no matches in the redesigned surfaces

## Task 3: Make Mods Page Work Without Live API

**Files:**
- Modify: `admin-web/frontend/assets/js/public/site-data.js`
- Create: `admin-web/frontend/assets/public-data/modpack_snapshot.json`
- Modify: `scripts/thirdparty/build_modpack.ps1`
- Modify: `scripts/thirdparty/build_modpack.sh`
- Modify: `thirdparty/modpack_manifest.json` if needed
- Test: `mods.html` on static localhost plus validator

- [ ] **Step 1: Add static snapshot loader to public data layer**

Required helper:

```js
async function fetchStaticModpackSnapshot() {
  return fetchJson("/assets/public-data/modpack_snapshot.json", {});
}
```

- [ ] **Step 2: Prefer API payload when available and fall back to static snapshot when not**

Required merge rule:

```js
const apiModpack = modpackPayload?.data || {};
const resolvedModpack = apiModpack && (apiModpack.available || apiModpack.filename || apiModpack.manifest)
  ? apiModpack
  : (staticModpack || {});
```

- [ ] **Step 3: Regenerate the snapshot from the real bundled archive during modpack build**

Snapshot must contain:

- `available`
- `filename`
- `downloadUrl`
- `size`
- `sha1`
- `modified`
- `manifest`

- [ ] **Step 4: Rebuild modpack and verify snapshot**

Run:

```bash
powershell -ExecutionPolicy Bypass -File D:/Desktop/Copimine/opt/copimine/scripts/thirdparty/build_modpack.ps1 -ProjectRoot D:/Desktop/Copimine/opt/copimine
```

Expected:

- `thirdparty/CopiMineMods.zip` rebuilt
- `thirdparty/CopiMineMods.sha1` updated
- `admin-web/frontend/assets/public-data/modpack_snapshot.json` synced

- [ ] **Step 5: Run validator**

Run:

```bash
powershell -ExecutionPolicy Bypass -File D:/Desktop/Copimine/opt/copimine/tests/ValidateCopiMineWebModpackDownloadFoundation.ps1
```

Expected:

- `ValidateCopiMineWebModpackDownloadFoundation passed.`

## Task 4: Rebuild Auth Screens As Single-Purpose Entry Pages

**Files:**
- Modify: `admin-web/frontend/signin.html`
- Modify: `admin-web/frontend/register.html`
- Modify: `admin-web/frontend/assets/css/auth.css`
- Test: browser screenshots on auth pages

- [ ] **Step 1: Keep only one centered auth card per page**

Signin page must include:

- logo
- title
- login field
- password field
- submit button
- one text link to registration

Register page must include:

- logo
- title
- login
- password
- Minecraft nickname
- submit button
- one text link to sign-in

- [ ] **Step 2: Ensure no role selection or mode switcher appears in auth HTML**

Run:

```bash
rg -n "Игрок|Команда сервера|Администрация|role switch|режим работы" admin-web/frontend/signin.html admin-web/frontend/register.html
```

Expected:

- no role selector UI text in auth documents

- [ ] **Step 3: Keep auth visuals restrained and centered**

Required shell behavior:

```css
.auth-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
}
```

Expected:

- no side marketing columns
- no extra page furniture

- [ ] **Step 4: Verify auth pages visually**

Check:

- `http://127.0.0.1:8088/signin.html`
- `http://127.0.0.1:8088/register.html`

Expected:

- one centered card
- no overflow
- no extra blocks

## Task 5: Normalize Cabinet Shell Branding And Strictness

**Files:**
- Modify: `admin-web/frontend/cabinet/*.html`
- Modify: `admin-web/frontend/assets/css/player.css`
- Modify: `admin-web/frontend/assets/css/admin.css`
- Test: route shell rendering for at least one player page and one admin page

- [ ] **Step 1: Replace `CM` placeholder branding with real CopiMine logo in all cabinet shell files**

Required shell fragment:

```html
<div class="side-brand">
  <img class="side-brand-logo" src="/assets/brand/copimine-logo.png" alt="" />
  <div class="side-brand-copy">
    <strong>CopiMine</strong>
    <span id="userBadge">загрузка</span>
  </div>
</div>
```

- [ ] **Step 2: Make player cabinets feel calmer and warmer than admin cabinets**

Player pages:

- softer borders
- less visual aggression
- more breathing room

Admin pages:

- clearer structure
- stronger line system
- more explicit action/status hierarchy

- [ ] **Step 3: Keep cabinet topbar operational**

Avoid:

- giant public hero patterns inside cabinet shell
- decorative feature blocks in header

Keep:

- page title
- subtitle
- refresh/status/theme actions

- [ ] **Step 4: Check a player route and admin route for consistency**

Check at least:

- `/cabinet/bank.html`
- `/cabinet/admins.html`

Expected:

- shared shell
- different tonal emphasis by role
- no leftover `CM` badge

## Task 6: Responsive And Visual Smoke Pass

**Files:**
- Modify: `admin-web/frontend/assets/css/responsive.css`
- Modify: `tests/manual/COPIMINE_WEBSITE_REDESIGN_SECURITY_SMOKE.md`
- Test: browser screenshots desktop and mobile

- [ ] **Step 1: Capture desktop screenshots**

Capture:

- `index.html`
- `server.html`
- `shops.html`
- `mods.html`
- `signin.html`
- `register.html`

- [ ] **Step 2: Capture mobile screenshots**

Use viewport around:

```text
390 x 844
```

Required checks:

- no overlapping cards
- no cutoff titles
- nav opens cleanly
- action buttons stack correctly

- [ ] **Step 3: Patch only observed responsive failures**

Do not guess.

Adjust selectors only after a visible issue is confirmed in screenshot output.

- [ ] **Step 4: Update manual smoke checklist**

Add explicit checks for:

- public route separation
- auth single-card flow
- static modpack fallback
- cabinet logo shell
- mobile no-overlap checks

## Task 7: Verification And Commit Gate

**Files:**
- Modify: any touched files from previous tasks
- Test: syntax checks, validator, git status

- [ ] **Step 1: Run frontend syntax checks**

Run:

```bash
C:/Users/zavod/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe --check D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/public/site-data.js
C:/Users/zavod/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe --check D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/public/site-render.js
C:/Users/zavod/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe --check D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/public/public-page.js
```

Expected:

- all commands exit `0`

- [ ] **Step 2: Run modpack validator**

Run:

```bash
powershell -ExecutionPolicy Bypass -File D:/Desktop/Copimine/opt/copimine/tests/ValidateCopiMineWebModpackDownloadFoundation.ps1
```

Expected:

- validator passes

- [ ] **Step 3: Review git diff for unintended backend drift**

Run:

```bash
git diff --stat
git status --short
```

Expected:

- only intended frontend, snapshot, modpack-script, and supporting files changed

- [ ] **Step 4: Commit redesign slices in coherent units**

Suggested commits:

```bash
git commit -m "Refine CopiMine public page design system"
git commit -m "Redesign CopiMine auth and cabinet shell branding"
git commit -m "Add static modpack snapshot fallback"
```

- [ ] **Step 5: Final review pass**

Before push, re-check:

- no obvious AI copy remains on redesigned public entry pages
- `mods.html` works without API
- auth pages stay single-purpose
- cabinet shell uses real logo branding

## Self-Review

### Spec coverage

- public showcase direction covered by Tasks 1 and 2
- auth simplification covered by Task 4
- cabinet strictness covered by Task 5
- mobile stability covered by Task 6
- static modpack realism covered by Task 3

### Placeholder scan

- no TBD or TODO placeholders in tasks

### Type consistency

- uses existing route/page file names
- uses existing public renderer names
- uses one static fallback file path consistently: `admin-web/frontend/assets/public-data/modpack_snapshot.json`
