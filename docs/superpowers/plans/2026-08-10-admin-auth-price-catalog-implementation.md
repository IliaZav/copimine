# Админская авторизация и каталог магазина Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow site-only admin accounts, make shop price updates report the real operation result without false 500 errors, and publish the requested item descriptions and prices locally and on the server.

**Architecture:** Keep `cm_admin_users` as the auth source and add optional Minecraft identity columns. Panel authentication will require only enabled admin credentials and role; Minecraft identity is used only for optional OP/whitelist actions and status display. Shop price mutation will preserve its staged two-file update and plugin reload, but audit/event failures after a confirmed mutation become warnings instead of uncaught 500 responses. `copimine-artifacts/items.yml` remains canonical and the runtime copy is updated from it.

**Tech Stack:** FastAPI/Pydantic backend, SQLite/PostgreSQL-compatible auth schema, vanilla JavaScript cabinet runtime, YAML catalog, PowerShell validators, Python regression scripts, Paper/RCON deployment, in-app browser smoke test.

## Global Constraints

- For admin creation, Minecraft-ник and whitelist are optional; ordinary player registration remains Minecraft-linked and whitelist-backed.
- Do not change brewing, elections, AR Creative transport, recipes, cooldowns, limits, effect IDs, or world/DB data.
- Change only the 20 user-listed catalog entries; preserve admin-only `kozyrny_tuz_pozdnyakova`.
- AR prices use `price_ar`; donation prices use `price-donation`.
- Never deploy before focused tests, validators, build, browser smoke, and local/remote hash checks pass.
- Preserve unrelated existing worktree changes and stage only files belonging to this request plus the design/plan commits.

---

### Task 1: Add failing regression coverage

**Files:**
- Modify: `admin-web/scripts/backend_security_regression_test.py`
- Create: `tests/test_admin_auth_price_catalog_contract.py`

**Interfaces:**
- The backend regression script uses its existing disposable SQLite loader and must exercise real `backend.main` functions without touching the live server.
- The source contract test reads the tracked backend/frontend/catalog files and catches accidental reintroduction of Minecraft-only admin login or stale prices.

- [ ] **Step 1: Write the failing source contract tests**

Add tests that assert:

```python
def test_admin_contract_has_site_login_and_optional_minecraft_identity():
    assert 'minecraft_name: Optional[str]' in BACKEND
    assert 'valid_site_username(username)' in BACKEND
    assert 'if data.ensure_whitelist and not minecraft_name' in BACKEND
    assert 'minecraft_name TEXT NOT NULL DEFAULT' in BACKEND
```

Add catalog assertions for all 20 requested IDs and prices, and assert the admin-only item remains `source: ADMIN_ONLY` with `price_ar: 5000`.

- [ ] **Step 2: Add a failing backend regression for a site-only admin**

Add `assert_site_only_admin_creation_does_not_require_minecraft_access(main)` to the existing disposable backend script. Seed an owner, call the admin creation helper/endpoint with `username="panel-login"`, `minecraft_name=""`, `ensure_whitelist=False`, `ensure_op=False`, and assert the stored admin can authenticate while `whitelist.json` remains `[]`.

- [ ] **Step 3: Add the failing price regression**

Use two temporary copies of `items.yml`, monkeypatch `_shop_catalog_paths`, `rcon_sync`, `audit_event`, and `append_panel_event`. Make `audit_event` raise after a successful reload and assert the operation still returns `ok=True`, both files contain the new price, and the response contains an audit warning. Repeat with `append_panel_event` raising.

- [ ] **Step 4: Run the focused tests and confirm they fail for the current implementation**

Run:

```powershell
python -m pytest tests/test_admin_auth_price_catalog_contract.py -q
admin-web\.venv\Scripts\python.exe admin-web\scripts\backend_security_regression_test.py
```

Expected failure: the current admin creation rejects `panel-login` as a Minecraft name, and a post-reload audit exception escapes from `update_shop_price_sync`.

### Task 2: Decouple admin login from Minecraft access

**Files:**
- Modify: `admin-web/backend/main.py:AdminAccessIn, AdminUpdateIn, AdminAccountUpdateIn`
- Modify: `admin-web/backend/main.py:ensure_auth_db, auth_db_user_rows, upsert_auth_user, current_admin_users_nonblocking`
- Modify: `admin-web/backend/main.py:require_panel_admin_context, rotate_auth_pair_from_refresh_sync, login, session_login, session_me, me`
- Modify: `admin-web/backend/main.py:admin_public_row, ensure_minecraft_access_for_new_admin, revoke_minecraft_admin_access, security_add_admin, security_update_admin, security_update_own_account`
- Modify: `admin-web/frontend/assets/js/cabinet-runtime.js:loadAdmins, createAdminUser`

**Interfaces:**
- Store fields as `minecraft_uuid` and `minecraft_name`, defaulting to empty strings.
- Use existing `valid_site_username` for admin login validation and existing `valid_minecraft_name` only for the optional Minecraft field.
- `ensure_minecraft_access_for_new_admin(minecraft_name, ensure_op, ensure_whitelist)` must do nothing when the name is empty and must never reject an unwhitelisted name when neither action was requested.

- [ ] **Step 1: Extend the auth schema and row serializers**

Add idempotent columns to `cm_admin_users`; include them in SELECT-to-dict and UPSERT paths. Preserve old rows with empty identity fields.

- [ ] **Step 2: Remove the Minecraft gate from panel authentication**

Panel auth checks enabled state and role only. Keep the existing `minecraft_access_ok` function for status/other player-linked behavior, but do not use it as a prerequisite for admin site login, refresh, `session_me`, or `require_panel_admin_context`.

- [ ] **Step 3: Implement optional identity/action semantics**

Validate the site login separately, normalize an optional nickname, reject `ensure_whitelist`/`ensure_op` when no nickname is supplied, execute requested RCON commands only for the nickname, and persist its UUID/name. Demotion/revoke uses the stored nickname and skips Minecraft commands for site-only accounts.

- [ ] **Step 4: Update the admin form and result table**

Render separate fields `Логин`, `Minecraft-ник (необязательно)`, and `Пароль`; default whitelist unchecked; send `minecraft_name`. Display the linked nickname or `не привязан`, and keep player suggestions only on the nickname field.

- [ ] **Step 5: Run focused auth tests**

Run the two tests from Task 1. Expected: site-only admin and ordinary player-registration assertions pass; existing admin demotion/access validators remain green.

### Task 3: Make price updates transactional from the browser’s point of view

**Files:**
- Modify: `admin-web/backend/main.py:update_shop_price_sync, admin_shop_price_update`
- Modify: `admin-web/frontend/assets/js/cabinet-runtime.js:updateShopPrice`
- Modify: `tests/test_admin_auth_price_catalog_contract.py`

**Interfaces:**
- The successful response keeps `ok`, `item_id`, `category`, `old_price`, `price`, and `reload`, and may add `auditWarning`.
- A failure before reload remains an HTTP error and restores all staged files.

- [ ] **Step 1: Add a post-mutation warning collector**

After confirmed reload, call audit and panel-event writes in separate `try/except` blocks. Log the exception with `LOGGER.exception`/`LOGGER.warning` without rolling back the already confirmed catalog mutation. Return one safe warning string or list without secrets.

- [ ] **Step 2: Keep pre-reload rollback strict**

Do not weaken YAML validation, item matching, staged writes, temporary-file replacement, reload response validation, or rollback. Ensure any RCON/reload failure still restores both catalog copies and returns a structured 503/404/500 detail.

- [ ] **Step 3: Surface the result in the admin UI**

After `api()` succeeds, show `Цена обновлена`; if `auditWarning` exists, show the price success plus a non-blocking audit warning. Never report a generic error for a successful price mutation.

- [ ] **Step 4: Run focused backend and JavaScript checks**

Run the temporary-copy price tests and `node --check admin-web/frontend/assets/js/cabinet-runtime.js`. Confirm both audit-failure variants pass and reload failure still rolls back.

### Task 4: Update canonical item descriptions and prices

**Files:**
- Modify: `copimine-artifacts/items.yml`
- Modify: `minecraft/server/plugins/CopiMineArtifacts/items.yml`
- Modify: `tests/test_admin_auth_price_catalog_contract.py`

**Interfaces:**
- Keep item IDs, materials, effects, cooldowns, limits, and catalog structure unchanged.
- Use the exact user-provided Russian descriptions in game lore; mirror donation descriptions in `effect-description` for website/catalog consistency.

- [ ] **Step 1: Replace AR lore and `price_ar` values**

Update the 11 requested AR entries: `zmei_gorynych=150`, `kopatel_transhey_shovel=30`, `craftsman_hammer=300`, `fermer_bez_sna_hoe=50`, `lesnoy_bespredel_axe=90`, `copimine_miner_pickaxe=500`, `smena_bez_perekura_pickaxe=100`, `treasurer_chestplate=120`, `dezhurniy_argument_sword=200`, `eternal_totem=9999`, `vechniy_razgon_firework=9999`.

- [ ] **Step 2: Replace donation lore/effect descriptions and `price-donation` values**

Update `gde_moy_lut_blyat_compass=100`, `vremya_platit_nalogi_clock=300`, `pohuy_na_debaffy_amulet=150`, `ne_segodnya_suka_shield=50`, `kaska_prorab_huev=50`, `mne_pohuy_ya_v_tanke_vest=150`, `kosa_nalogovoy_inspekcii=200`, `nu_ty_i_nakopal_blyat_pickaxe=400`, `batin_remen_sudnogo_dnya=500`.

- [ ] **Step 3: Verify the two copies are byte-equivalent**

Parse both files with the project’s YAML-capable Python environment, compare the requested rows, and run the existing AR/donation catalog validators. Do not rewrite unrelated catalog formatting or the admin-only row.

### Task 5: Validate and package

**Files:**
- Modify only generated release files produced by `scripts/package_full_release.ps1` if the packaging script updates tracked metadata.
- Review: `RELEASE_DEPLOYMENT.md`, `scripts/package_full_release.ps1`, `deploy/windows/send_copimine_release.ps1`

- [ ] **Step 1: Run focused tests and validators**

Run:

```powershell
python -m pytest tests/test_admin_auth_price_catalog_contract.py -q
node --check admin-web/frontend/assets/js/cabinet-runtime.js
powershell -NoProfile -ExecutionPolicy Bypass -File tests/RunCopiMineValidators.ps1
```

- [ ] **Step 2: Run the backend security regression with the project environment**

Run `admin-web\.venv\Scripts\python.exe admin-web\scripts\backend_security_regression_test.py` locally when dependencies are present; otherwise run the same command with the server’s `/opt/copimine/admin-web/.venv/bin/python` against a disposable test directory and record the result.

- [ ] **Step 3: Build the full release and record hashes**

Run `scripts/package_full_release.ps1`, capture the archive SHA-256, and verify the archive contains both catalog copies and the backend/frontend files.

### Task 6: Browser smoke test and controlled deployment

**Files:**
- No source changes; use the built release and current browser session.

- [ ] **Step 1: Connect to the in-app browser and inspect the authenticated admin session**

Open the admin shop page, confirm the AR and Donation rows show the new values, and do not inspect cookies/local storage.

- [ ] **Step 2: Change one price through the browser**

Use the existing admin confirmation flow to change a disposable test price, confirm the success status, reload the shop page, and verify the input/API value remains changed. Restore it to the requested final price and verify again.

- [ ] **Step 3: Upload one verified release archive**

Use `deploy/windows/send_copimine_release.ps1` with the exact archive; verify remote archive size and SHA-256 before installation. Do not use wipe flags.

- [ ] **Step 4: Install and verify the server**

Run the configured `install_release.sh` through the authorized sudo rule, then collect diagnostics, `/api/health`, service/process status, remote catalog hashes, and the requested price values. Only a normal service restart is allowed.

### Task 7: Review, commit, and push

- [ ] **Step 1: Inspect `git diff --check`, status, and staged diff**

Stage only the implementation, tests, catalog, generated release metadata, and already committed design/plan files for this request. Leave unrelated existing modifications unstaged.

- [ ] **Step 2: Request a code review**

Review the final diff against `f97e092`, fix all critical/important findings, and rerun the required tests.

- [ ] **Step 3: Commit and push the current branch**

Create a focused commit such as `fix: decouple admin login and repair shop prices`, push the current branch to its configured Git remote, and report the commit SHA together with deployment hashes.
