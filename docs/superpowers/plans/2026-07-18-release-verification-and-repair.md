# CopiMine Release Verification And Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` task-by-task. Every production behavior change follows red-green-refactor.

**Goal:** Verify the release branch across the site, custom plugins, resource pack, and deployment contract; repair only reproducible defects and leave an evidence-based Ubuntu handoff.

**Architecture:** Preserve the existing FastAPI, static web frontend, Paper plugin, PostgreSQL, and resource-pack contracts. Treat the 507 existing validators as regression coverage, then add a focused regression only when a new defect is demonstrated. Browser checks cover the rendered public and representative cabinet surfaces; live money and deferred-delivery transactions remain gated on a real PostgreSQL instance.

**Tech Stack:** Python 3/FastAPI, vanilla HTML/CSS/ES modules, Java/Paper sources, PostgreSQL-compatible SQL, PowerShell validators, resource-pack builder, Browser/IAB.

---

### Task 1: Establish the release baseline

**Files:**
- Verify: `tests/RunCopiMineValidators.ps1`
- Verify: `admin-web/scripts/backend_smoketest.py`
- Verify: `admin-web/scripts/regression_contract_test.py`
- Verify: `admin-web/scripts/sql_selftest.py`
- Verify: `admin-web/scripts/security_selftest.py`
- Modify: none unless a check demonstrates a failure

- [ ] Run the complete validator suite and require `VALIDATOR_SUMMARY total=507 passed=507 failed=0`.
- [ ] Run backend, SQL, and security self-tests plus Python compilation and JavaScript syntax checks.
- [ ] Record skipped integration paths separately from passing paths; never report a skipped PostgreSQL purchase test as a paid-purchase success.
- [ ] Preserve all pre-existing untracked CSS and screenshot files.

### Task 2: Verify commerce and custom-item invariants

**Files:**
- Verify: `admin-web/backend/main.py`
- Verify: `copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java`
- Verify: `copimine-artifacts/items.yml`
- Verify: `resourcepacks/models_manifest.json`
- Verify: `resourcepacks/item_texture_sources.json`
- Verify: `tests/ValidateCopiMinePozdnyakovAce.ps1`
- Verify: `tests/ValidateCopiMineShopCart*.ps1`
- Verify: `tests/ValidateCopiMineWebsiteItemVisuals.ps1`
- Verify: `tests/ValidateCopiMineShieldPreview.ps1`

- [ ] Trace the AR single-purchase, AR cart, and in-game shop paths to the same per-item PostgreSQL advisory lock before each supply-limit check.
- [ ] Verify that prices are resolved on the server, checkout requires a player session, CSRF, PIN, and idempotency key, and paid rows create pending delivery rows in the same transaction.
- [ ] Verify that `pozdnyakov_ace` is `ADMIN_ONLY`, has the exact role text, has a resource-pack model, and is excluded from public catalogues.
- [ ] Verify that every sellable item has an in-game model and a website preview; the shield must use a rendered front preview rather than its UV layout.
- [ ] If any invariant fails, first add one failing focused validator in `tests/`, run it to prove the gap, apply the minimum owner-file repair, then rerun the focused validator and full suite.

### Task 3: Exercise the rendered web surface

**Files:**
- Verify: `admin-web/frontend/index.html`
- Verify: `admin-web/frontend/shops.html`
- Verify: `admin-web/frontend/cart.html`
- Verify: `admin-web/frontend/cabinet/*.html`
- Verify: `admin-web/frontend/assets/css/release-ui.css`
- Verify: `admin-web/frontend/assets/js/public/*.js`
- Modify only after a reproducible Browser defect

- [ ] Start the local FastAPI server without changing production configuration.
- [ ] Open public routes in Browser/IAB at desktop and mobile widths. Check readable foreground/background contrast, nav behavior, cart count, AR and donation separation, item textures, shield preview, no technical item fields, and the absence of horizontal overflow.
- [ ] Open representative player and admin preview routes. Check mobile drawer behavior, desktop navigation, theme switch, focusable controls, and non-overlapping text.
- [ ] Capture screenshots to `admin-web/screenshots/` only if those files are intended for this release report; do not add them to a code commit unless release policy explicitly requires it.
- [ ] Review browser console warnings/errors and failed resource requests. For each reproducible UI or route defect, add a focused check first, then repair the owning module and re-run the visual path.

### Task 4: Rebuild and validate deployable artifacts

**Files:**
- Verify: `resourcepacks/build-resourcepack.ps1`
- Verify: `scripts/package_full_release.ps1`
- Verify: `deploy/ubuntu/copimine_full_replace.sh`
- Verify: `tests/ValidateCopiMineUbuntuReleaseStructure.ps1`
- Verify: `tests/ValidateCopiMineUbuntuLiveSmoke.ps1`
- Modify only after a reproducible build or release-contract failure

- [ ] Build the resource pack and verify the server hash, model manifest, TAB compatibility inputs, and item textures.
- [ ] Run the release-package validation without exposing `.env` contents or deployment credentials.
- [ ] State the exact remaining runtime prerequisite: run `backend_smoketest.py` and the Ubuntu live smoke after the real PostgreSQL service and `/opt/copimine/admin-web/.env` are available.

### Task 5: Publish only scoped work

**Files:**
- Modify: only files produced by confirmed repairs or this plan

- [ ] Run `git diff --check` and inspect the final status.
- [ ] Stage only files owned by this task, commit with a concrete message, and push `codex/release-overhaul` to `IliaZav/copimine`.
- [ ] Report fixes, verification evidence, screenshots, and unresolved environment-only checks in Russian without claiming that unrun live services passed.
