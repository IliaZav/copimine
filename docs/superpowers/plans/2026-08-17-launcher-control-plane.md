# CopiMine Launcher control plane implementation plan

> **Execution rule:** implement each task in order. For every task, add a failing test first, run it to confirm the intended failure, make the smallest implementation change, run the focused and relevant regression tests, review `git diff --check`, then commit the green slice. Never include the existing `thirdparty/grim-creative-patch/build/GrimAC-2.3.74-40684fb-creative-fix.jar` change.

## Goal

Add a safe local/staging control plane that lets site administrators manage Launcher releases, client mods, news/patch notes, and aggregate distribution diagnostics while preserving the existing signed manifest and public static contracts. Production game data and Paper remain outside the scope.

## Task 1 — Pure control-plane storage and validation

**Files:** `admin-web/backend/launcher_control.py`, `tests/test_launcher_control_plane.py`.

1. Add tests for a temporary control directory covering:
   - atomic JSON state creation/recovery;
   - component id, version, filename, path, SHA-256, size and URL validation;
   - traversal, duplicate component/path, oversize and untrusted browser checksum rejection;
   - draft mod add/replace/remove with content-addressed binary storage;
   - preservation of non-mod manifest fields when a draft is built;
   - news title/slug/summary/section/item validation, plain-text escaping and texture URL safety;
   - bounded telemetry event validation and JSONL counter aggregation.
2. Run the focused test and capture the expected failures before writing implementation.
3. Implement a standalone module with injectable root/source paths, atomic writes, bounded inputs, no import-time mutation, and JSONL-only control audit/stat storage.
4. Seed the first draft from the existing `instance-manifest.json` without importing `backend.main` or touching PostgreSQL.
5. Run focused tests, then the launcher contract tests. Commit `feat: add launcher control-plane storage`.

## Task 2 — Signing, publication, immutable releases and rollback

**Files:** `admin-web/backend/launcher_control.py`, `tests/test_launcher_control_plane.py`, `admin-web/requirements.txt`, `tools/publish_instance_manifest.py` only if a shared helper is required.

1. Add failing tests for missing-key fail-closed validation, ephemeral Ed25519 staging publication, detached signature verification, publication ordering, immutable release retention, and rollback to a verified previous release.
2. Implement server-only key loading from an environment value or protected file, never serialize private key material, and return stable diagnostic reason codes.
3. Publish content-addressed binaries first, then signed manifest/signature, then patch detail contracts, and update mutable latest/index pointers last. Leave the previous pointer untouched on failure.
4. Keep the generated instance manifest compatible with the native Launcher and preserve Java/Minecraft/server/non-managed fields.
5. Run focused tests and the existing manifest publisher/release tests. Commit `feat: publish signed launcher releases safely`.

## Task 3 — FastAPI admin/public API and distribution routes

**Files:** `admin-web/backend/main.py`, `admin-web/backend/launcher_control.py`, `tests/test_launcher_control_api.py`.

1. Add failing API tests with a temporary control/public root for admin auth, CSRF, confirmation and bounded upload behavior; public read-only launcher/news/file routes; telemetry rejection/acceptance; and static fallback behavior.
2. Add lazy control-plane integration to `main.py` without creating DB tables or writing existing production audit tables. Keep existing `require_admin`/CSRF/sensitive confirmation gates.
3. Implement the admin routes for dashboard, mod upload/edit/delete, validate/publish/rollback, news CRUD/publish and stats.
4. Implement public launcher/news/file and telemetry routes. Serve only validated SHA-256 paths and reject traversal/mismatches.
5. Run the focused API tests plus `admin-web/scripts/backend_smoketest.py`, security self-tests and launcher contract tests. Commit `feat: expose launcher control-plane api`.

## Task 4 — Admin CMS Launcher and Новости experience

**Files:** `admin-web/frontend/assets/js/admin/launcher-pages.js`, `admin-web/frontend/assets/js/admin/news-pages.js`, `admin-web/frontend/assets/js/cabinet-runtime.js`, `admin-web/frontend/cabinet/cabinet.html`, relevant admin CSS, `tests/test_launcher_admin_contract.py`.

1. Add failing source/contract tests for a dedicated Launcher navigation item, dedicated Новости editor, API method coverage, escaped user content, no inline event handlers and clear error/empty states.
2. Add a compact Launcher admin page with current release health, validate/publish/rollback actions, managed mod table with upload/edit/remove, release history/audit and distribution statistics.
3. Add a normal news editor for title/slug/version/publication time, summary bullets, general/technical/fix groups and item-aware entries with safe texture URLs.
4. Reuse existing cabinet tokens and navigation/loading conventions; do not mix release mutations into the generic CMS page.
5. Run contract tests and the existing frontend/backend contract suite. Commit `feat: add launcher and news admin pages`.

## Task 5 — Public contracts and native Launcher update feed

**Files:** `admin-web/frontend/assets/js/public/launcher-data.js`, `admin-web/frontend/assets/js/public/patch-data.js`, `admin-web/frontend/assets/js/public/patch-render.js` only as needed, `scripts/stage_copimine_launcher_site.ps1`, `tests/test_launcher_public_api_contract.py`.

1. Add failing tests for API-first loading with static-contract fallback, safe detail URLs, item texture preservation and current installer/manifest metadata checks in the staging script.
2. Update public data clients to use `/api/public/launcher` and `/api/public/news` first and retain the current static JSON fallback during backend outage.
3. Ensure publication generates schema-compatible launcher metadata and patch/news details without unsafe HTML interpolation.
4. Make staging fail if the published installer metadata hash/size does not match the staged artifact or if required signed manifest files are absent.
5. Run the public contract tests, patch-note tests and staging script locally. Commit `feat: connect launcher updates to public contracts`.

## Task 6 — Local/staging integration and browser verification

**Files:** `admin-web/scripts/launcher_control_smoketest.py`, `scripts/run_copimine_launcher_staging.ps1`, `tests/test_launcher_staging_contract.py`, evidence under `artifacts/launcher/Release/verification/`.

1. Add a disposable local control/public root and synthetic mod/news fixtures; hard-fail if a test environment points at a production DB host/name or world path.
2. Start the local FastAPI/static staging service, exercise upload → validate → publish → public download → manifest verification → news fetch → telemetry → rollback.
3. Use the in-app Browser skill to capture desktop and mobile screenshots of public Launcher/news pages and the local admin page when local admin auth is available; record console/network errors.
4. Run native Launcher update/reconciliation tests against the staged contracts, clean-machine install/update/rollback tests, and the full repository launcher suite. Do not use production server data.
5. Commit `test: verify launcher control-plane staging flow` only if all focused checks are green; otherwise record explicit `UNVERIFIED` evidence and fix before continuing.

## Task 7 — Final release gate and handoff

**Files:** release evidence only; no production data.

1. Run `git diff --check`, full Python/backend/frontend tests, .NET build/package, installer verification, SHA-256, clean-machine install, update, rollback, Minecraft launch, local server, ticket/client handshake and website/news checks already required by the Launcher contract.
2. Re-run the three protected regression gates only in local/disposable staging: ping/main-thread blocking, bed waypoint/respawn/relog/restart/break, and AuthMe delayed-slowness authorization/reconnect. Production must remain untouched.
3. Run an independent verification pass over the diff, test logs, artifact hashes and staging files.
4. Create the acceptance record only if every mandatory gate is `PASS`; otherwise report each `UNVERIFIED` gate and do not call the product release-ready.
5. Commit only completed green evidence changes with a descriptive message. Do not push or deploy production from this task.
