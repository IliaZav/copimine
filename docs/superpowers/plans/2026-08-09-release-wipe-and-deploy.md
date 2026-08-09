# CopiMine Release Wipe and Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare and deploy a release that keeps the website identity boundary and whitelist while resetting the requested game state, with a verified backup and rollback path.

**Architecture:** Build the release from a committed Git tree, verify the signed payload, upload it to the existing `/home/qwerty/copimine-upload` staging directory, and install through the guarded release entrypoint. Perform the destructive reset as a separate, explicitly confirmed operation: stop services, create a PostgreSQL custom dump plus SHA-256 manifest, verify protected row counts, delete only the approved game tables and world directories, restore services, and verify health.

**Tech Stack:** PowerShell/OpenSSH on Windows, Bash/systemd/PostgreSQL on Ubuntu, FastAPI static frontend, Paper plugins, Git signed release manifests.

## Global Constraints

- Do not overwrite or stage unrelated user-owned working-tree files.
- Do not delete data until the exact deletion manifest, backup path, restore test, retention period, and final confirmation are recorded.
- Preserve the website account records, administrator access, whitelist identity records, `server.properties`, `ops.json`, and the configuration/catalog rows for AR prices, artifact prices and brewing recipes.
- Preserve working brewing, AR, and election runtime behavior; do not rebuild or replace those plugins outside the release artifacts selected for this release.
- Upload only an archive whose local SHA-256 equals the remote SHA-256, and verify service health after installation.
- A release is not complete until local Git, remote installed files, service state, and post-wipe protected row counts are independently checked.

---

### Task 1: Freeze scope and inventory current state

**Files:**
- Read: `admin-web/backend/main.py`
- Read: `db/runtime/reset_game_state_preserve_accounts.sql`
- Read: `deploy/ubuntu/reset_game_state_preserve_accounts.sh`
- Read: `deploy/shared/common.sh`
- Read: `minecraft/server/server.properties`
- Read: `deploy/release_manifest.json`

**Interfaces:**
- Consumes the current local Git tree and the read-only server inventory.
- Produces an itemized release file list, database table list, world directory list, and protected identity list for operator approval.

- [x] **Step 1: Record the local branch, HEAD, dirty files, and release inputs.**

  Current branch is `agent/deep-election-artifacts-audit`. The website changes are in `admin-web/backend/main.py`, `admin-web/frontend/index.html`, `admin-web/frontend/assets/css/public-president.css`, `admin-web/frontend/assets/js/public/site-render.js`, `admin-web/frontend/assets/js/cabinet-runtime.js`, and `tests/test_public_president_and_player_link.py`. Existing generated/plugin files and probe artifacts remain user-owned until explicitly selected.

- [x] **Step 2: Record the server runtime inventory without mutating it.**

  The remote host is `server-rpgrp`; all five CopiMine services were active. The world directories are `CopiMine`, `CopiMine_nether`, and `CopiMine_the_end`. The remote installed hashes were captured before any deployment.

- [x] **Step 3: Approve the protected identity boundary.**

  The user confirmed that website accounts, `server.properties`, and `ops.json` stay. The final protected boundary is `site_accounts`, `player_web_accounts`, `cm_admin_users`, `cm_admin_sessions`, `cm_refresh_sessions`, `minecraft_account_links`, `cmv4_account_links`, `whitelist_account_links`, `whitelist_requests`, the auth-link state tables, `site_cms_entries`, `ar_settings`, `artifact_items_catalog`, `narcotics_schema_version`, and `narcotics_config_values`. `usercache.json`, `whitelist.json`, plugin configuration and the server properties are preserved as files. World-bound shop/ATM coordinates and player/runtime rows are reset.

---

### Task 2: Make the wipe manifest explicit and testable

**Files:**
- Modify: `db/runtime/reset_game_state_preserve_accounts.sql`
- Modify: `deploy/ubuntu/reset_game_state_preserve_accounts.sh`
- Modify: `tests/ValidateCopiMineGameWipePreservesAccounts.ps1`
- Create: `docs/deploy/COPIMINE_GAME_WIPE_MANIFEST_RU.md`

**Interfaces:**
- SQL keeps only the approved identity tables and deletes all listed gameplay/runtime tables if present.
- The guarded wrapper requires `COPIMINE_CONFIRM_GAME_WIPE=YES`, creates a custom PostgreSQL dump, writes a SHA-256 file and manifest, verifies protected counts, optionally removes exactly the configured world directories, and restarts the known services.

- [x] **Step 1: Write a failing validator for the approved preservation list.**

  Added `tests/test_release_wipe_and_world_backup_contract.py`; its first run failed on the old modpack contents and the missing configuration preservation contract.

- [x] **Step 2: Run the validator and record the expected failure.**

  Run `powershell -NoProfile -ExecutionPolicy Bypass -File tests/ValidateCopiMineGameWipePreservesAccounts.ps1`. It must fail until the existing broader preservation list is reconciled with the approved boundary.

- [x] **Step 3: Implement the narrow SQL and wrapper changes.**

  Keep identifier validation, `SET search_path`, `ON_ERROR_STOP`, `pg_dump --format=custom`, `sha256sum`, `realpath -e`, and the protected-count assertions. Do not use a broad recursive deletion outside the three validated world paths.

- [x] **Step 4: Add the exact manifest and restore procedure.**

  Document the backup location, dump checksum, protected row counts, world paths, table list, restore command using `pg_restore` into a staging database, and the required retention period before deleting the backup.

- [x] **Step 5: Run the validator again.**

  The Python contract runner passes all four checks after the SQL, wrapper and modpack changes.

### Task 3a: Configure low-priority world backups

- [x] Add `deploy/ubuntu/world_backup.sh` with RCON save flush, `flock`, atomic publication, hard-linked incremental copies, a 12-hour retention window and a newest-snapshot safety floor.
- [x] Add `copimine-world-backup.service` and `.timer` for an initial 10-minute run followed by five-hour intervals.
- [x] Install/enable the units through `deploy/shared/common.sh` and require a durable pre-wipe snapshot before a world deletion.
- [x] Document the deletion and restore manifest in `docs/deploy/COPIMINE_GAME_WIPE_MANIFEST_RU.md`.

---

### Task 3: Commit and package the release

**Files:**
- Modify only selected release source and generated artifacts after the scope review.
- Use: `scripts/package_full_release.ps1`
- Use: `scripts/validate_release_bundle.ps1`

**Interfaces:**
- Produces a committed Git tree, a signed `copimine-opt-full-*.tar.gz`, its SHA-256, bootstrap manifest, and exported installer helpers.

- [ ] **Step 1: Stage only the approved website, wipe, test, and release files.**

  Do not stage `_ar_player_probe_*.dat`, probe directories, unrelated JARs, server properties, or generated manifests unless their diff is explicitly selected as part of this release.

- [ ] **Step 2: Run source and release validators before committing.**

  Run the affected contract tests, `python -m py_compile`, `node --check` for changed JavaScript, `git diff --check`, and the release safety validators.

- [ ] **Step 3: Commit with a release-specific message.**

  Create a commit only after the staged diff contains the exact approved files and the working-tree diff has been reviewed. Record the full commit SHA in the release metadata.

- [ ] **Step 4: Build and validate the signed release archive.**

  Run `scripts/package_full_release.ps1` with the configured signing key and then `scripts/validate_release_bundle.ps1` against the generated archive and bootstrap manifest. Abort on any manifest, signature, payload, or artifact mismatch.

---

### Task 4: Backup and destructive reset on the server

**Files:**
- Use: `deploy/ubuntu/reset_game_state_preserve_accounts.sh`
- Use: `db/runtime/reset_game_state_preserve_accounts.sql`

**Interfaces:**
- Consumes the approved release and explicit operator confirmation.
- Produces a durable pre-wipe backup, a clean world, reset gameplay data, preserved website accounts/whitelist, and service logs.

- [ ] **Step 1: Verify the backup target and retention.**

  Confirm the exact `/opt/copimine-backups/game-wipe-<timestamp>` path, that the dump and SHA-256 are non-empty, and that the backup will be retained until a restore test and post-release acceptance check pass.

- [ ] **Step 2: Run the guarded reset with both confirmations.**

  Use `sudo COPIMINE_CONFIRM_GAME_WIPE=YES bash /opt/copimine/deploy/ubuntu/reset_game_state_preserve_accounts.sh --wipe-worlds` only after the preservation list is approved. The wrapper must stop services, dump the database, delete the approved tables/world directories, verify protected counts, and restart services.

- [ ] **Step 3: Verify the post-reset identity boundary.**

  Independently query counts/hashes for the preserved account tables and `whitelist.json`, and verify the wiped table counts/world directories. Do not accept the wrapper log alone as proof.

---

### Task 5: Upload, install, and verify the release

**Files:**
- Use: `scripts/windows/upload_release.ps1` or the repository’s configured upload helper.
- Use: `deploy/ubuntu/install_release.sh`
- Use: `deploy/ubuntu/copimine_verify.sh`

**Interfaces:**
- Consumes the locally validated archive and exact SHA-256.
- Produces remote staging hashes, installed release hashes, active services, public health responses, and a retained rollback path.

- [ ] **Step 1: Upload to `/home/qwerty/copimine-upload` and verify size/SHA-256.**

  Do not use append mode or overwrite an existing partial archive. The remote hash must equal the local hash before installation.

- [ ] **Step 2: Install through the guarded release entrypoint.**

  Use the configured passwordless sudo rule for `/home/qwerty/copimine-upload/install_release.sh` and keep the previous release backup. Do not pass a database dump in the release archive unless the approved restore plan explicitly requires it.

- [ ] **Step 3: Verify services, endpoints, plugin startup, and hashes.**

  Check systemd state, `/api/health`, the public download/resource-pack endpoints, release manifest/signature, plugin JAR hashes, and recent Minecraft/admin logs. Record any failure instead of declaring release readiness.

---

### Task 6: Release handoff and retention decision

**Files:**
- Create/update: `docs/deploy/COPIMINE_GAME_WIPE_MANIFEST_RU.md`

- [ ] **Step 1: Record local commit, archive hash, remote archive hash, installed hashes, backup path, and post-wipe counts.**
- [ ] **Step 2: Run the final verification command set after deployment.**
- [ ] **Step 3: Keep the pre-wipe backup until the agreed retention date and only then remove it with a separate explicit confirmation.**
