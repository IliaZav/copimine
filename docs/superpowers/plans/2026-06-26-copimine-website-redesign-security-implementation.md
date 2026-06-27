# CopiMine Website Redesign / Security Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Довести `admin-web` до рабочего и безопасного состояния без сноса существующих donation/AR/plugin-registry flows, одновременно обновив публичный сайт, личный кабинет и админ-панель.

**Architecture:** Работа идёт как аккуратное расширение существующего foundation. `backend/main.py` остаётся точкой совместимости по маршрутам, но часть логики выносится в отдельные backend-модули и frontend-модули там, где это снижает риск. UI обновляется поверх существующих API и state-flow, а не через переписывание backend с нуля.

**Tech Stack:** FastAPI, PostgreSQL, vanilla ES modules, CSS modules, Paper plugin-backed APIs, PowerShell validators, GitHub handoff, codex-security review gates.

---

## 1. Audit Summary Locked Before Coding

### Current facts from the codebase

- `frontend/index.html` уже стал shell-страницей и не является старым монолитом.
- `frontend/assets/js/bootstrap.js` уже разделяет публичный сайт и legacy SPA.
- `frontend/assets/js/public/homepage.js` уже рендерит главную страницу, но содержит mojibake в публичных текстах.
- `frontend/assets/js/legacy/app-legacy.js` остаётся огромным legacy-runtime файлом и по-прежнему содержит:
  - `localStorage` state;
  - множество `innerHTML`;
  - старые навигационные и page-render flows;
  - потенциальные XSS/maintenance риски.
- `backend/main.py` уже содержит:
  - cookie-auth;
  - CSRF cookie/header flow;
  - security headers;
  - donation endpoints;
  - plugin registry endpoints;
  - president budget public/admin endpoints.
- `backend/plugin_registry.py` и `docs/superpowers/specs/*.md` содержат mojibake.
- `tests/ValidateCopiMineNoBrokenRussianEncoding.ps1` сейчас проверяет неверный путь к `CopiMineClient`.

### Design constraints for this pass

- Не ломать существующие working endpoints, если их можно сохранить.
- Не удалять donation / AR / plugin registry backend foundation.
- Не превращать новый UI в fake dashboard.
- Не переносить refresh token в `localStorage`.
- Не делать raw config editor для plugin registry.
- Сначала сохранить поведение, потом улучшать структуру и UX.

---

## 2. File / Module Map

### Existing files to modify

- `D:/Desktop/Copimine/opt/copimine/admin-web/backend/main.py`
- `D:/Desktop/Copimine/opt/copimine/admin-web/backend/plugin_registry.py`
- `D:/Desktop/Copimine/opt/copimine/admin-web/backend/plugin_registry_manifest.json`
- `D:/Desktop/Copimine/opt/copimine/admin-web/backend/commerce_catalog.py`
- `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/index.html`
- `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/style.css`
- `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/bootstrap.js`
- `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/public/homepage.js`
- `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/legacy/app-legacy.js`
- `D:/Desktop/Copimine/opt/copimine/tests/ValidateCopiMineNoBrokenRussianEncoding.ps1`

### New files expected in this pass

- `D:/Desktop/Copimine/opt/copimine/admin-web/backend/auth_security.py`
- `D:/Desktop/Copimine/opt/copimine/admin-web/backend/public_site.py`
- `D:/Desktop/Copimine/opt/copimine/admin-web/backend/player_portal.py`
- `D:/Desktop/Copimine/opt/copimine/admin-web/backend/admin_portal.py`
- `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/public/site-data.js`
- `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/public/site-render.js`
- `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/shared/http.js`
- `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/shared/dom.js`
- `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/shared/security.js`
- `D:/Desktop/Copimine/opt/copimine/tests/ValidateCopiMineWebNoSingleIndexApp.ps1`
- `D:/Desktop/Copimine/opt/copimine/tests/ValidateCopiMineWebNoHugeAppJs.ps1`
- `D:/Desktop/Copimine/opt/copimine/tests/ValidateCopiMineNoAiPlaceholderText.ps1`
- `D:/Desktop/Copimine/opt/copimine/tests/ValidateCopiMineNoMojibakeBrokenEncoding.ps1`
- `D:/Desktop/Copimine/opt/copimine/tests/manual/COPIMINE_WEBSITE_REDESIGN_SECURITY_SMOKE.md`

### Adjacent files likely touched if integration gaps appear

- `D:/Desktop/Copimine/opt/copimine/copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java`
- `D:/Desktop/Copimine/opt/copimine/copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- `D:/Desktop/Copimine/opt/copimine/copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java`

Only touch these if the site exposes a real bug in API or data contract.

---

## 3. Risks To Close

### Security risks

- refresh/session confusion between cookie-auth and any bearer fallback;
- lingering `localStorage` auth/session state in legacy frontend;
- `innerHTML` rendering of user data;
- inline event handlers or weak CSP exceptions;
- admin mutation endpoints without strict auth/CSRF/rate-limit enforcement;
- plugin registry path/config abuse.

### Product risks

- removing already-working donation shop or AR cabinet flows while redesigning;
- breaking public homepage counters and president block while moving modules;
- splitting frontend in a way that leaves dead buttons or broken routing;
- keeping mojibake in public/admin/player UI after redesign.

### Maintenance risks

- `main.py` remains too large to reason about;
- `app-legacy.js` remains the only runtime and blocks future work;
- spec/plan/docs stay corrupted and mislead future work.

---

## 4. Delivery Order

1. Fix plan-level blockers: mojibake in docs/validators that make future work unsafe.
2. Audit backend/public/player/admin routes and map all current UI entry points.
3. Split the web pass into backend hardening, shared frontend infra, public site, cabinet/admin UI, and validation.
4. Move high-risk helpers out of `main.py` without changing route contracts.
5. Introduce shared frontend helpers, then migrate the public homepage off brittle inline rendering.
6. Reduce legacy runtime dependence for the most important player/admin screens.
7. Run validators and security review.
8. Fix validated findings.
9. Only then prepare final GitHub handoff.

---

## 5. Task Plan

### Task 1: Fix design docs and encoding blockers first

**Files:**
- Modify: `D:/Desktop/Copimine/opt/copimine/docs/superpowers/specs/2026-06-25-copimine-donation-shop-design.md`
- Modify: `D:/Desktop/Copimine/opt/copimine/docs/superpowers/plans/2026-06-26-donation-ar-shop-plugin-registry-implementation.md`
- Modify: `D:/Desktop/Copimine/opt/copimine/tests/ValidateCopiMineNoBrokenRussianEncoding.ps1`

- [ ] Re-save the approved donation/shop spec in readable UTF-8 Russian.
- [ ] Re-save the current donation/AR/plugin-registry implementation plan in readable UTF-8 Russian.
- [ ] Fix `ValidateCopiMineNoBrokenRussianEncoding.ps1` so it scans the real `D:/Desktop/Copimine/CopiMineClient` path.
- [ ] Extend the validator so it also scans current `docs/superpowers/specs` and `docs/superpowers/plans`, because unreadable docs are now a release defect.

### Task 2: Freeze the route map before refactoring

**Files:**
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/backend/main.py`
- Add: `D:/Desktop/Copimine/opt/copimine/tests/manual/COPIMINE_WEBSITE_REDESIGN_SECURITY_SMOKE.md`

- [ ] Extract a route inventory for:
  - public homepage/status/president budget;
  - auth/csrf/session endpoints;
  - player donation/bank/shop/history endpoints;
  - admin donation/AR/plugin-registry endpoints.
- [ ] Write the route inventory into the new smoke doc so later refactors can be checked against it.
- [ ] Identify which routes are compatibility-critical and must keep their existing path/response shape.

### Task 3: Harden backend security helpers without changing feature scope

**Files:**
- Create: `D:/Desktop/Copimine/opt/copimine/admin-web/backend/auth_security.py`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/backend/main.py`

- [ ] Move CSRF/origin/session helper code into `auth_security.py` while keeping current middleware behavior.
- [ ] Keep cookie-auth as canonical flow.
- [ ] Keep bearer fallback disabled by default and clearly isolated behind config.
- [ ] Add explicit helper checks for:
  - `Origin`;
  - `Sec-Fetch-Site` when useful;
  - CSRF token presence/match on mutations;
  - no-cache for private responses.
- [ ] Normalize error envelopes so private failures do not leak stack traces.
- [ ] Add or strengthen rate-limits for:
  - login;
  - register;
  - refresh;
  - donation create-session;
  - purchase intent;
  - admin top-up;
  - plugin apply/reload.

### Task 4: Split public site data loading from rendering

**Files:**
- Create: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/shared/http.js`
- Create: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/shared/dom.js`
- Create: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/public/site-data.js`
- Create: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/public/site-render.js`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/public/homepage.js`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/bootstrap.js`

- [ ] Move raw fetch/state logic for the homepage into `site-data.js`.
- [ ] Move rendering helpers for budget/history/president card into `site-render.js`.
- [ ] Rebuild `homepage.js` as a small coordinator module.
- [ ] Replace mojibake strings in homepage public UI with readable Russian text.
- [ ] Keep the current president budget counter, president card and public history features alive during the split.

### Task 5: Clean the public homepage shell and polish the design

**Files:**
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/index.html`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/style.css`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/css/base.css`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/css/layout.css`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/css/public.css`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/css/components.css`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/css/animations.css`

- [ ] Preserve the existing public sections that already correspond to real data.
- [ ] Remove AI/placeholder/debug wording from the public shell.
- [ ] Improve spacing, hierarchy, interaction states, and responsive behavior without inventing fake cards.
- [ ] Keep “Скачать моды”, “Войти”, “Начать играть”, donation and budget entry points visible.
- [ ] Ensure no inline handlers are introduced.

### Task 6: Reduce legacy frontend risk without full rewrite

**Files:**
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/legacy/app-legacy.js`
- Create: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/shared/security.js`

- [ ] Replace the highest-risk direct `innerHTML` paths used for user-controlled data with safer DOM or sanitized rendering.
- [ ] Remove or reduce `localStorage` usage for anything auth-sensitive.
- [ ] Keep only harmless UX persistence in storage, if still needed:
  - last role tab;
  - non-sensitive view preferences.
- [ ] Ensure donation session state and player bank scope do not become security state.
- [ ] Keep old routes working while moving repeated fetch/header logic into shared helpers.

### Task 7: Improve player cabinet and commerce glue without redesigning from zero

**Files:**
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/legacy/app-legacy.js`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/css/player.css`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/css/shop.css`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/backend/main.py`

- [ ] Keep player donation balance, donation shop, AR shop, item history and bank pages functional.
- [ ] Replace broken or misleading text with short readable Russian copy.
- [ ] Make donation and AR separation obvious in the UI.
- [ ] Keep purchase buttons wired to existing backend flows.
- [ ] Ensure “claim in game” states are honest and not rendered as false success.

### Task 8: Improve admin surfaces for donation, treasury, plugin registry and account roles

**Files:**
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/backend/main.py`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/backend/plugin_registry.py`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/backend/plugin_registry_manifest.json`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/legacy/app-legacy.js`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/css/admin.css`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/css/president.css`

- [ ] Keep admin overview/donation/plugin-registry/account flows working.
- [ ] Add or verify junior-admin visibility restrictions in backend role checks where the site exposes dangerous actions.
- [ ] Make plugin registry UI use the manifest-backed editable keys only.
- [ ] Remove mojibake from admin-facing registry and economy copy.
- [ ] Ensure president treasury screens make the separate treasury account explicit and do not present it as a normal player account.

### Task 9: Fix plugin registry encoding and hardening gaps

**Files:**
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/backend/plugin_registry.py`
- Modify: `D:/Desktop/Copimine/opt/copimine/admin-web/backend/plugin_registry_manifest.json`

- [ ] Replace all mojibake exception messages with proper Russian.
- [ ] Re-check path confinement, key allowlist, schema validation, backup-before-apply and reload restrictions.
- [ ] Ensure no raw path from request input reaches filesystem writes.
- [ ] Ensure apply/backup/audit responses remain human-readable for the admin panel.

### Task 10: Validators, compile checks and smoke docs

**Files:**
- Add/Modify: `D:/Desktop/Copimine/opt/copimine/tests/Validate*.ps1`
- Modify: `D:/Desktop/Copimine/opt/copimine/tests/manual/COPIMINE_WEBSITE_REDESIGN_SECURITY_SMOKE.md`

- [ ] Add validators for:
  - no single huge frontend entrypoint;
  - no auth-sensitive `localStorage`;
  - no placeholder/AI text;
  - no mojibake in web files;
  - no inline handlers;
  - CSP/no-unsafe-inline;
  - admin route role checks;
  - plugin registry allowlist enforcement.
- [ ] Update smoke doc with:
  - homepage budget/president/history checks;
  - login/register/logout/refresh/CSRF checks;
  - donation/AR purchase and history rendering checks;
  - plugin registry allowlist/backup/apply checks;
  - junior-admin access boundaries.

### Task 11: Build, security review and GitHub gate

**Files:**
- All files touched in this pass

- [ ] Run:
  - `python -m py_compile` for changed backend files;
  - `node --check` for changed JS files;
  - relevant `Validate*.ps1`.
- [ ] Run diff-scoped `codex-security` review for changed web/backend files.
- [ ] Fix validated findings.
- [ ] Run full independent project review gate before final git handoff:
  - active plugins;
  - `admin-web`;
  - `CopiMineClient` if touched by the pass.
- [ ] Fix validated high-severity findings before any push.
- [ ] Only after green checks perform the explicit end sequence:
  1. проверка;
  2. исправление;
  3. git/GitHub publish.

---

## 6. Validators To Add Or Refresh

- `ValidateCopiMineWebNoSingleIndexApp.ps1`
- `ValidateCopiMineWebNoHugeAppJs.ps1`
- `ValidateCopiMineWebRoutesStillWork.ps1`
- `ValidateCopiMineExistingFunctionsNotRemoved.ps1`
- `ValidateCopiMineJwtAuth.ps1`
- `ValidateCopiMineRefreshTokenRotation.ps1`
- `ValidateCopiMineRefreshTokenHttpOnly.ps1`
- `ValidateCopiMineNoRefreshTokenLocalStorage.ps1`
- `ValidateCopiMineCsrfForMutations.ps1`
- `ValidateCopiMineRoleChecksForAdminEndpoints.ps1`
- `ValidateCopiMineRateLimitSensitiveEndpoints.ps1`
- `ValidateCopiMineSecureHeaders.ps1`
- `ValidateCopiMineNoAiPlaceholderText.ps1`
- `ValidateCopiMineNoMojibakeBrokenEncoding.ps1`
- `ValidateCopiMineNoFakeDashboardData.ps1`
- `ValidateCopiMineNoPlayerFacingDebugText.ps1`
- `ValidateCopiMineNoUnsafeInnerHtml.ps1`
- `ValidateCopiMineUploadSecurity.ps1`
- `ValidateCopiMineNoPathTraversal.ps1`

---

## 7. Build / Check Matrix

### Python

- `python -m py_compile D:/Desktop/Copimine/opt/copimine/admin-web/backend/main.py`
- `python -m py_compile D:/Desktop/Copimine/opt/copimine/admin-web/backend/auth_security.py`
- `python -m py_compile D:/Desktop/Copimine/opt/copimine/admin-web/backend/public_site.py`
- `python -m py_compile D:/Desktop/Copimine/opt/copimine/admin-web/backend/player_portal.py`
- `python -m py_compile D:/Desktop/Copimine/opt/copimine/admin-web/backend/admin_portal.py`
- `python -m py_compile D:/Desktop/Copimine/opt/copimine/admin-web/backend/plugin_registry.py`
- `python -m py_compile D:/Desktop/Copimine/opt/copimine/admin-web/backend/commerce_catalog.py`

### Frontend

- `node --check D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/app.js`
- `node --check D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/bootstrap.js`
- `node --check D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/public/homepage.js`
- `node --check D:/Desktop/Copimine/opt/copimine/admin-web/frontend/assets/js/legacy/app-legacy.js`

### Validators

- all web/auth/commerce/plugin-registry validators touched in this pass
- existing cross-project encoding validators if docs or Java/Python/JS text is changed

---

## 8. Security Review Gate Before Git / Push

### Gate A: Diff-scoped review

- run `codex-security` on:
  - `admin-web/backend/main.py`
  - new backend helper modules
  - `plugin_registry.py`
  - `commerce_catalog.py`
  - `frontend/assets/js/*` changed in this pass

### Gate B: Fix pass

- fix every validated issue
- rerun impacted builds/validators
- document any non-blocking residual risk

### Gate C: Final sequence

1. **Проверка**
   - full independent review across active plugins and web
2. **Исправление**
   - close validated release blockers
3. **Гит**
   - commit and push only after green checks

---

## 9. Done Criteria

This pass is done only when:

1. Public homepage remains functional and uses real data or honest fallback states.
2. President budget block and president card work without fake data.
3. Cookie-auth + refresh + logout + CSRF flow are intact and hardened.
4. No auth-sensitive state remains in `localStorage`.
5. Donation and AR flows remain operational and clearly separated in UI.
6. Plugin registry stays allowlisted and safe.
7. Major mojibake is removed from touched web/docs/validator files.
8. Validators and compile checks pass.
9. codex-security review -> fix -> final git sequence is completed.
