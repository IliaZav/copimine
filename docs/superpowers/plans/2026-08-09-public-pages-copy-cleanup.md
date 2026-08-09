# Public Pages Copy Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove unnecessary AI-like and release/service copy from public CopiMine pages, improve responsive copy hierarchy, verify every public route visually, deploy the signed release, and perform the separately confirmed game-state wipe.

**Architecture:** Keep the existing public HTML/CSS shell and token system. Make player-facing wording changes in the static templates and the shared public renderer, add a source-level forbidden-copy contract, then verify the built/installed payload with browser screenshots and signed release hashes. Keep release hashes and operational diagnostics in admin/deploy artifacts only.

**Tech Stack:** Static HTML, CSS, browser ES modules, Python contract tests, PowerShell release packaging, signed Linux payload, Paper/PostgreSQL runtime.

## Global Constraints

- Public pages must not display `Обновлено ...`, `Только просмотр`, archive SHA/hash values, archive verification details, source commits, API/route/health-check diagnostics, or old-build troubleshooting copy.
- Public pages must retain useful player instructions for account creation, whitelist, PIN, payments, in-game delivery, and genuine extra downloads.
- The current visual system, responsive navigation, Minecraft imagery, and existing brewing/elections/gameplay behavior are out of scope.
- Release metadata and hashes remain in admin/deploy manifests and are not deleted from the release pipeline.
- The game wipe must preserve website accounts, whitelist, `ops.json`, `server.properties`, prices/catalogs, and brewing configuration, and must be preceded by the documented database/world backups.
- Do not stage `copimine-election-core/plugin.yml`, probe files, or unrelated user-owned files.

---

### Task 1: Add the public-copy regression contract

**Files:**
- Modify: `tests/test_public_president_and_player_link.py` or create `tests/test_public_copy_contract.py`
- Test: `admin-web/frontend/index.html`, `server.html`, `elections.html`, `mods.html`, `404.html`, `error.html`, `assets/js/public/site-render.js`, `assets/js/public/homepage.js`

**Interfaces:**
- Consumes: current public templates and shared renderer source.
- Produces: a deterministic source-level test that distinguishes forbidden public copy from allowed admin/deploy metadata.

- [ ] **Step 1: Write the failing test**

Assert that the selected public source set contains none of these exact user-facing phrases or generators: `Обновлено `, `Только просмотр`, `SHA1`, `SHA архива`, `контрольные суммы`, `API health-check`, `API-обработчик`, `старой версии кабинета`, `только просмотр`, and public archive-size/date/hash labels. Assert that the public page still contains the useful actions and data mounts: `Скачать модпак`, `publicElectionRefresh`, `presidentLaws`, `modpackMetaGrid`, and the 404/error recovery links.

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
python -m pytest tests/test_public_copy_contract.py -q
```

Expected: FAIL because the current public templates and renderer still contain the forbidden strings.

- [ ] **Step 3: Keep the contract narrow**

Exclude `admin-web/frontend/assets/js/admin`, `admin-web/frontend/cabinet`, `deploy`, and `admin-web/backend` from the forbidden-copy scan so release/admin diagnostics remain available where operators need them.

- [ ] **Step 4: Commit the test**

```powershell
git add tests/test_public_copy_contract.py
git commit -m "test: guard public copy from release noise"
```

### Task 2: Clean home, server, and election copy

**Files:**
- Modify: `admin-web/frontend/index.html`
- Modify: `admin-web/frontend/server.html`
- Modify: `admin-web/frontend/elections.html`
- Modify: `admin-web/frontend/assets/js/public/site-render.js`
- Modify: `admin-web/frontend/assets/js/public/homepage.js`
- Test: `tests/test_public_copy_contract.py`, `tests/test_public_president_and_player_link.py`

**Interfaces:**
- Consumes: existing public data payloads and current DOM IDs.
- Produces: the same data behavior with shorter player-facing copy and no technical refresh metadata.

- [ ] **Step 1: Replace static archive and election filler**

In `index.html`, replace the archive explanation with `Готовый комплект для входа на сервер.` and keep the existing download action. In `elections.html`, replace the long readonly sentence with `Голосование проходит в игре. Здесь опубликованы кандидаты и результаты.`; remove the readonly badge while keeping the result panel and in-game explanation.

- [ ] **Step 2: Remove server/home timestamp presentation**

In `site-render.js`, make the treasury detail use `Публичный баланс казны.` or an actionable unavailable message; never interpolate `Обновлено` or a timestamp into public DOM. Keep timestamps attached to actual treasury history entries because those describe events, not API freshness.

- [ ] **Step 3: Simplify election status text**

Change the election meta to `Тур N · одобренные кандидаты: X` when active and `Активная кампания не запущена.` when inactive. Change the generated-at field to a concise status such as `Данные доступны` or an actionable `Не удалось получить данные. Нажмите «Обновить».` Remove the candidate-card `Только просмотр` suffix while retaining vote totals and percentages. Preserve the refresh button and loading text `Обновляем данные` only during the user-triggered request.

- [ ] **Step 4: Run focused tests and syntax check**

Run:

```powershell
python -m pytest tests/test_public_copy_contract.py tests/test_public_president_and_player_link.py -q
node --check admin-web/frontend/assets/js/public/site-render.js
node --check admin-web/frontend/assets/js/public/homepage.js
```

Expected: PASS with zero forbidden public-copy matches and valid module syntax.

### Task 3: Simplify the modpack public page

**Files:**
- Modify: `admin-web/frontend/mods.html`
- Modify: `admin-web/frontend/index.html`
- Modify: `admin-web/frontend/assets/js/public/site-render.js`
- Modify: `admin-web/frontend/assets/public-data/modpack_snapshot.json` only if its player-facing notes are rendered publicly
- Test: `tests/test_public_copy_contract.py`

**Interfaces:**
- Consumes: `modpack.manifest.files`, `requiredExternal`, `minecraftVersion`, and `loader`.
- Produces: friendly public mod list and download facts without hashes, archive dates, raw paths, or license noise.

- [ ] **Step 1: Replace the modpack hero/checklist copy**

Use `Версия Minecraft, Fabric и список модов.` in the hero and a short checklist containing `Minecraft 1.21.1`, `Fabric`, and `Все обязательные моды уже внутри архива.` when there are no external downloads. Do not mention SHA, archive size, archive date, or control sums.

- [ ] **Step 2: Render player-facing mod facts**

In `renderModpack`, render only Minecraft version, loader, number of mods, and the external-download count when nonzero. Remove the size and modified-date entries from `modpackMetaGrid`; set `modpackSummaryLead` to `Готовый комплект для входа на сервер.` when available. Render each file card with friendly component name and version only; omit raw `mods/...jar` paths and license labels.

- [ ] **Step 3: Keep the deployment metadata intact**

Do not alter `deploy/release_manifest.json`, `deploy/sbom.spdx.json`, or the release signing flow to remove technical hashes. The public renderer is the boundary that hides those values from players.

- [ ] **Step 4: Run the public-copy and modpack contracts**

Run:

```powershell
python -m pytest tests/test_public_copy_contract.py tests/test_release_wipe_and_world_backup_contract.py -q
node --check admin-web/frontend/assets/js/public/site-render.js
```

Expected: PASS with release metadata still present in deploy manifests and absent from public templates/renderers.

### Task 4: Reduce 404 and load-error diagnostics to recovery actions

**Files:**
- Modify: `admin-web/frontend/404.html`
- Modify: `admin-web/frontend/error.html`
- Test: `tests/test_public_copy_contract.py`

**Interfaces:**
- Consumes: existing `error-page.js` reload action and navigation URLs.
- Produces: short, non-technical recovery pages.

- [ ] **Step 1: Simplify 404 content**

Keep `Этой страницы здесь нет.`, `На главную`, and `Открыть вход`. Replace the paragraphs and side-card list with `Проверьте адрес или выберите раздел в меню.` and a short `Вернитесь в рабочий раздел.` note.

- [ ] **Step 2: Simplify load-error content**

Keep the reload button and home link. Replace route/API/health-check wording with `Попробуйте обновить страницу или вернитесь на главную.` and a concise side note `Если ошибка повторяется, сообщите об этом администрации.`

- [ ] **Step 3: Run the focused copy contract**

Run:

```powershell
python -m pytest tests/test_public_copy_contract.py -q
```

Expected: PASS with recovery controls still present.

### Task 5: Verify the visual result on every public route

**Files:**
- Create: `docs/audits/YYYY-MM-DD-public-pages/` screenshots and notes
- Modify: `docs/superpowers/specs/2026-08-09-public-pages-copy-and-design-audit.md` only for measured findings

**Interfaces:**
- Consumes: live/local public HTML and the Browser skill capture surface.
- Produces: accepted desktop screenshots for all public routes and mobile screenshots for the key responsive routes.

- [ ] **Step 1: Capture desktop routes**

Capture `index.html`, `server.html`, `elections.html`, `shops.html`, `mods.html`, `signin.html`, `register.html`, `cart.html`, `404.html`, and `error.html`. Check body text and screenshots for forbidden copy, loading states, clipped cards, broken images, and horizontal overflow.

- [ ] **Step 2: Capture mobile routes**

At 390×844 capture home, elections, shops, and mods. Verify the mobile menu opens, cards reflow, buttons remain reachable, and long law/product text wraps.

- [ ] **Step 3: Run static and accessibility-oriented checks**

Run `node --check` for every changed module, the full selected Python contracts, and a DOM check that every public page has one `h1`, labeled navigation, visible action text, and no forbidden public phrases.

- [ ] **Step 4: Commit audit artifacts separately**

```powershell
git add docs/audits/2026-08-09-public-pages docs/superpowers/specs/2026-08-09-public-pages-copy-and-design-audit.md
git commit -m "docs: record public page visual audit"
```

### Task 6: Build, upload, install, and verify the signed release

**Files:**
- Use: `scripts/package_full_release.ps1`
- Use: `scripts/windows/upload_release.ps1`
- Use: `/usr/local/sbin/copimine-install-release` on the server
- Test: `scripts/validate_release_bundle.ps1` and remote service/HTTPS checks

**Interfaces:**
- Consumes: committed public-page changes and current release signing key.
- Produces: a signed archive whose payload is installed on the server without a wipe.

- [ ] **Step 1: Run the full build and release validator**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\package_full_release.ps1 -ProjectRoot 'D:\Desktop\Copimine\copimine-main' -ReleaseDir 'D:\Desktop\Copimine\release' -SigningKeyPath 'C:\Users\zavod\.codex\copimine-release-signing-key'
```

Run the generated `scripts/validate_release_bundle.ps1` against the archive and bootstrap manifest. Expected: successful plugin/client build, binary scan, payload manifest, signature, and bundle validation.

- [ ] **Step 2: Upload with exact SHA verification**

Use `scripts/windows/upload_release.ps1` with server `90.188.115.155`, user `qwerty`, port `2222`, and the repository `deploy/release_manifest.json`. Confirm remote size and SHA exactly match the local archive.

- [ ] **Step 3: Install without wipe**

Use the active allowed wrapper `/usr/local/sbin/copimine-install-release <archive> <sha>`. Do not pass `--wipe-worlds` or a database dump. Confirm `World wipe enabled: 0`, existing database retained, public HTTPS routes healthy, and all services active.

- [ ] **Step 4: Verify installed public payload**

Compare the installed public HTML/CSS/JS hashes against the signed release manifest and fetch every public route over HTTPS. Repeat the forbidden-copy body-text scan against the live site.

### Task 7: Create the pre-wipe backup and perform the controlled game-state reset

**Files:**
- Use: `deploy/ubuntu/reset_game_state_preserve_accounts.sh`
- Use: `deploy/ubuntu/copimine_unpack_and_verify.sh --wipe-worlds` only after backup verification
- Reference: `docs/deploy/COPIMINE_GAME_WIPE_MANIFEST_RU.md`

**Interfaces:**
- Consumes: the installed, verified release and the exact preservation manifest.
- Produces: a durable PostgreSQL dump/counts/manifest, a world snapshot, and a clean game state with protected accounts/configuration intact.

- [ ] **Step 1: Print and review the deletion manifest**

Verify that the operation preserves website accounts/passwords/admin access, Minecraft links, whitelist, `ops.json`, `server.properties`, plugin configs, prices/catalogs, brewing settings, and site CMS. Verify that it deletes only election runtime, AR/economy/artifact runtime, brewing player runtime, world-bound runtime, and configured world directories.

- [ ] **Step 2: Create and verify pre-wipe backups**

Run the reset script with `COPIMINE_CONFIRM_GAME_WIPE=YES` only after it has written `/opt/copimine-backups/game-wipe-<UTC>/copimine-before-wipe.dump`, its SHA/counts/manifest, and the world snapshot under `/opt/copimine-backups/worlds/`. Verify the dump and snapshot are readable before deletion.

- [ ] **Step 3: Execute the wipe**

Run the controlled installer/reset path with the explicit world-wipe flag and no account/config wipe. Record the exact log path and deletion counts.

- [ ] **Step 4: Verify preservation and clean state**

Compare protected counts before/after, verify accounts and whitelist remain, verify prices/catalogs/brewing settings remain, confirm election/AR runtime is empty, confirm world directories were recreated cleanly, and verify all services and HTTPS routes after restart.
